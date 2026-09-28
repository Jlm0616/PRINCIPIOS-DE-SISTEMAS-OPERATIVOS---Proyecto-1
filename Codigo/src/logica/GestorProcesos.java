package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Instruccion;
import modelo.Memoria;
import modelo.MemoriaVirtual;
import modelo.Disco;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Fachada de orquestación del ciclo de vida y ejecución de procesos.
 *
 * Responsabilidades:
 *   - Cargar archivos .asm y convertirlos en procesos.
 *   - Gestionar el ciclo de vida (crear, ejecutar, bloquear, terminar).
 *   - Manejar swap in/out cuando no hay partición libre o kernel lleno:
 *       * READY_SUSPEND:    proceso listo que no cabe en memoria.
 *       * BLOCKED_SUSPEND:  proceso bloqueado que no cabe en memoria.
 *   - Exponer consultas para la GUI.
 *
 * Modelo de swap (Stallings, secciones 3.2 y 3.3):
 *   - El process image (BCP + instrucciones + pila) se mueve COMPLETO
 *     entre memoria principal y swap.
 *   - Cuando un proceso se suspende, su BCP se libera del kernel y
 *     el process image se guarda en posiciones contiguas del swap:
 *       [ BCP: 22 posiciones ][ Instrucciones: N posiciones ]
 *   - Al reactivarse, se reserva un bloque nuevo en kernel, se restauran
 *     los 22 valores del BCP y se recargan las instrucciones.
 *
 * La lista "procesosSuspendidos" es la "tabla del SO" que describe
 * qué procesos están suspendidos y dónde (Stallings, sección 3.3).
 */
public class GestorProcesos {

    /** Máximo de procesos permitidos en el sistema (restricción del enunciado). */
    public static final int MAX_PROCESOS = 5;

    private Memoria memoria;
    private MemoriaVirtual memoriaVirtual;
    private CPU cpu;
    private ListaDeTrabajos listaDeTrabajos;
    private Planificador planificador;
    private Despachador despachador;
    private ParticionadorFijo particionador;
    private Disco disco;

    private int siguienteId;
    private List<BCPTerminado> procesosTerminados;
    private List<BCP> procesosBloqueadosIO;
    private List<BCP> procesosBloqueadosInput;
    
    /** Callback de salida a pantalla (INT 10H y mensajes del gestor). */
    private java.util.function.Consumer<String> salidaPantalla;

    /** Tabla del SO: procesos suspendidos y dónde viven en swap. */
    private List<ProcesoEnEspera> procesosSuspendidos;

    public GestorProcesos(Memoria memoria,
                          MemoriaVirtual memoriaVirtual,
                          CPU cpu,
                          ListaDeTrabajos listaDeTrabajos,
                          ParticionadorFijo particionador,
                          Disco disco) {
        this.memoria = memoria;
        this.memoriaVirtual = memoriaVirtual;
        this.cpu = cpu;
        this.listaDeTrabajos = listaDeTrabajos;
        this.particionador = particionador;
        this.planificador = new Planificador(listaDeTrabajos);
        this.despachador = new Despachador(cpu);
        this.siguienteId = 1;
        this.procesosTerminados = new ArrayList<>();
        this.procesosBloqueadosIO = new ArrayList<>();
        this.procesosBloqueadosInput = new ArrayList<>();
        this.procesosSuspendidos = new ArrayList<>();
        this.disco = disco;
        this.despachador.setDisco(disco);
    }

    /* ==================== CREACIÓN DE PROCESOS ==================== */

    /**
     * Carga un archivo .asm como proceso nuevo.
     *
     * Orden de intentos:
     *   1. Si hay partición libre Y espacio en kernel → cargar en READY.
     *   2. Si no → intentar suspender en swap (READY_SUSPEND).
     *   3. Si tampoco → error con mensaje claro.
     */
    public ResultadoCarga cargarPrograma(File archivo) {
        if (getTotalProcesosActivos() >= MAX_PROCESOS) {
            return ResultadoCarga.error(
                "Ya hay " + MAX_PROCESOS + " procesos activos en memoria.\n"
                + "Espera a que termine alguno o presiona 'Limpiar'.");
        }

        Ensamblador ensamblador = new Ensamblador();

        if (!ensamblador.esArchivoValido(archivo)) {
            return ResultadoCarga.error(ensamblador.getErroresComoTexto());
        }

        List<Instruccion> instrucciones = ensamblador.leerArchivo(archivo);

        if (instrucciones.size() > particionador.getTamanoParticion()) {
            return ResultadoCarga.error(
                "El programa tiene " + instrucciones.size() + " instrucciones, "
                + "pero cada partición solo admite " + particionador.getTamanoParticion());
        }

        // 1. ¿Hay partición libre Y espacio en kernel?
        int indice = particionador.asignarParticion();
        if (indice != -1) {
            BCP bcp = crearProcesoEnParticion(instrucciones, indice);
            if (bcp != null) {
                return ResultadoCarga.exito(bcp);
            }
            // No hay espacio en kernel: liberar la partición que acabamos de asignar
            particionador.liberarParticion(indice);
        }

        // 2. Intentar suspender en swap
        ResultadoCarga resultadoSwap = suspenderEnSwap(instrucciones);
        if (resultadoSwap.getEstado() == ResultadoCarga.Estado.EN_ESPERA) {
            return resultadoSwap;
        }

        // 3. No hay ni partición, ni kernel, ni swap
        return ResultadoCarga.error(
            "No hay espacio en memoria principal (ni partición ni kernel), "
            + "y la memoria virtual está llena.\n"
            + "Libera espacio o aumenta la memoria en 'Configurar'.");
    }

    /**
     * Suspende un proceso moviéndolo al swap.
     *
     * Guarda el process image COMPLETO en posiciones contiguas del swap:
     *   [ BCP (22 posiciones) ][ Instrucciones (N posiciones) ]
     *
     * El BCP no ocupa kernel: sus 22 valores viven en el swap.
     * La lista procesosSuspendidos registra dónde está cada uno.
     *
     * @return EN_ESPERA si se suspendió, ERROR si el swap está lleno
     */
    private ResultadoCarga suspenderEnSwap(List<Instruccion> instrucciones) {
        int tamanoNecesario = BCP.POSICIONES_REQUERIDAS + instrucciones.size();

        // Verificar que haya espacio libre suficiente
        if (memoriaVirtual.getEspacioLibre() < tamanoNecesario) {
            return ResultadoCarga.error(
                "La memoria virtual no tiene espacio suficiente.\n"
                + "Necesario: " + tamanoNecesario + " posiciones, "
                + "libre: " + memoriaVirtual.getEspacioLibre() + ".");
        }

        // Reservar bloque contiguo
        int dirSwap = memoriaVirtual.reservarBloque(tamanoNecesario);
        if (dirSwap == -1) {
            return ResultadoCarga.error("No hay bloque contiguo libre en la memoria virtual.");
        }

        int id = siguienteId++;

        // Escribir el BCP en las primeras 22 posiciones
        Object[] valoresBCP = BCP.valoresIniciales(id, 1, instrucciones.size());
        memoriaVirtual.escribirBloque(dirSwap, valoresBCP);

        // Escribir las instrucciones en las N posiciones siguientes
        Object[] instruccionesArr = instrucciones.toArray();
        memoriaVirtual.escribirBloque(dirSwap + BCP.POSICIONES_REQUERIDAS, instruccionesArr);

        // Registrar los metadatos en la tabla del SO
        ProcesoEnEspera pe = new ProcesoEnEspera(
                id, EstadoProceso.READY_SUSPEND, tamanoNecesario);
        pe.setDireccionBaseEnSwap(dirSwap);
        procesosSuspendidos.add(pe);

        System.out.println("[SWAP] Proceso " + id
                + " suspendido en swap (dir=" + dirSwap
                + ", tamano=" + tamanoNecesario + ")");

        return ResultadoCarga.enEspera();
    }

    /**
     * Intenta crear un proceso en una partición.
     *
     * @return el BCP creado, o null si no hay espacio en kernel
     */
    private BCP crearProcesoEnParticion(List<Instruccion> instrucciones, int indiceParticion) {
        int direccionBase = memoria.reservarBloque(BCP.POSICIONES_REQUERIDAS);
        if (direccionBase == -1) {
            return null;
        }
        BCP bcp = new BCP(memoria, direccionBase, siguienteId++, 1);
        admitirEnParticion(bcp, instrucciones, indiceParticion);
        return bcp;
    }

    /* ==================== EJECUCIÓN PASO A PASO ==================== */

    public boolean ejecutarUnPaso() {
        if (!despachador.procesoActualOcupaCPU()) {
            if (despachador.getEjecutorActual() != null) {
                BCP anterior = despachador.getEjecutorActual().getBcp();
                if (anterior.getEstado() == EstadoProceso.EXIT) {
                    procesoTerminado(anterior);
                }
                despachador.limpiarEjecutor();
            }

            BCP siguiente = planificador.seleccionarSiguiente();
            if (siguiente == null) {
                return false;
            }

            listaDeTrabajos.sacarPrimero();
            despachador.despachar(siguiente, memoria);
        }

        boolean sigueVivo = despachador.ejecutarUnPaso();
        BCP actual = despachador.getEjecutorActual().getBcp();
        EstadoProceso estado = actual.getEstado();

        if (estado == EstadoProceso.EXIT) {
            procesoTerminado(actual);
            despachador.limpiarEjecutor();
        } else if (estado == EstadoProceso.BLOCKED) {
            despachador.guardarContexto(actual);
            actual.setCpuAsignado(-1);
            despachador.limpiarEjecutor();

            if (fueBloqueoIO(actual)) {
                System.out.println("[GESTOR] Proceso " + actual.getId()
                    + " BLOCKED por IO -> desbloqueado inmediatamente");
                actual.setEstado(EstadoProceso.READY);
                listaDeTrabajos.agregar(actual);
            } else {
                procesosBloqueadosInput.add(actual);
                System.out.println("[GESTOR] Proceso " + actual.getId()
                    + " BLOCKED esperando input");
            }
        }

        return true;
    }

    private boolean fueBloqueoIO(BCP bcp) {
        int ir = bcp.getIr();
        if (ir < 0) {
            return false;
        }
        Instruccion instr = memoria.leerInstruccion(ir);
        if (instr == null) {
            return false;
        }
        if (!"INT".equals(instr.getOpcode())) {
            return false;
        }
        return instr.getCodigoInterrupcion(0) == 0x21;
    }

    /**
     * Ejecuta todos los procesos hasta que terminen.
     *
     * Si algún proceso se bloquea esperando input del teclado (INT 09H),
     * se simula automáticamente un valor aleatorio entre 0 y 255, y se
     * desbloquea. Esto permite que el modo automático no se quede colgado
     * esperando intervención del usuario.
     *
     * El valor simulado se notifica a la GUI a través del callback de
     * salida a pantalla, para que quede visible en el PanelPantalla.
     *
     * @return cantidad de pasos de CPU ejecutados
     */
    public int ejecutarAutomatico() {
        int pasos = 0;
        while (hayProcesosActivos()) {

            // Si hay procesos esperando input, simular uno automáticamente.
            if (!procesosBloqueadosInput.isEmpty()) {
                int valorSimulado = (int) (Math.random() * 256);   // 0-255
                String mensaje = ">> [AUTO] Simulando input de teclado: "
                        + valorSimulado + " (para proceso en espera)";
                if (salidaPantalla != null) {
                    salidaPantalla.accept(mensaje);
                }
                System.out.println("[GESTOR-AUTO] " + mensaje);
                desbloquearProceso(valorSimulado);
            }

            ejecutarUnPaso();
            pasos++;

            if (pasos > 100000) {
                throw new IllegalStateException(
                    "Demasiados pasos: posible ciclo infinito entre procesos.");
            }
        }
        return pasos;
    }

    /* ==================== TERMINACIÓN ==================== */

    public void procesoTerminado(BCP bcp) {
        // 1. Marcar estado final
        bcp.setEstado(EstadoProceso.EXIT);
        bcp.marcarFin();
        bcp.setCpuAsignado(-1);

        // 2. Crear snapshot ANTES de liberar el BCP
        BCPTerminado snapshot = new BCPTerminado(
            bcp.getId(),
            bcp.getEstado(),
            bcp.getTiempoInicio(),
            bcp.getTiempoFin(),
            bcp.getBase(),
            bcp.getAlcance(),
            bcp.getPrioridad()
        );

        // 3. Agregar snapshot a la lista de terminados
        this.procesosTerminados.add(snapshot);

        // 4. Copiar base y alcance a variables locales
        int base = bcp.getBase();
        int alcance = bcp.getAlcance();

        // 5. Liberar la particion de usuario
        int indice = particionador.indiceDesdePosicion(base);
        particionador.liberarParticion(indice);

        // 6. Limpiar las posiciones de la zona usuario
        for (int i = 0; i < alcance; i++) {
            memoria.escribir(base + i, null);
        }

        // 7. Liberar el bloque del BCP en el kernel
        memoria.liberarBloque(bcp.getDireccionBase(), BCP.POSICIONES_REQUERIDAS);

        // 8. Intentar admitir el siguiente proceso suspendido
        reactivarSiguienteProceso();
    }

    /**
     * Intenta reactivar un proceso suspendido (swap in).
     *
     * Pasos:
     *   1. Verificar que haya partición libre y espacio en kernel.
     *   2. Tomar el primer ProcesoEnEspera de la tabla del SO.
     *   3. Leer el process image del swap (BCP + instrucciones).
     *   4. Liberar el bloque del swap.
     *   5. Reservar bloque en kernel y restaurar los 22 valores del BCP.
     *   6. Cargar las instrucciones en la partición de usuario.
     *
     * Si algo falla, el proceso queda intacto en el swap.
     */
    private void reactivarSiguienteProceso() {
        if (procesosSuspendidos.isEmpty()) {
            return;
        }

        int indiceNuevo = particionador.asignarParticion();
        if (indiceNuevo == -1) {
            return;   // todavía no hay partición libre
        }

        ProcesoEnEspera pe = procesosSuspendidos.get(0);

        // Reservar bloque en kernel para el BCP restaurado
        int direccionBase = memoria.reservarBloque(BCP.POSICIONES_REQUERIDAS);
        if (direccionBase == -1) {
            particionador.liberarParticion(indiceNuevo);
            return;
        }

        // Leer el process image completo del swap
        int dirSwap = pe.getDireccionBaseEnSwap();
        int tamanoEnSwap = pe.getTamanoEnSwap();
        int tamanoInstrucciones = tamanoEnSwap - BCP.POSICIONES_REQUERIDAS;

        Object[] valoresBCP = memoriaVirtual.leerBloque(dirSwap, BCP.POSICIONES_REQUERIDAS);
        Object[] instruccionesArr = memoriaVirtual.leerBloque(
                dirSwap + BCP.POSICIONES_REQUERIDAS, tamanoInstrucciones);

        // Convertir Object[] a List<Instruccion>
        List<Instruccion> instrucciones = new ArrayList<>();
        for (Object obj : instruccionesArr) {
            if (obj instanceof Instruccion) {
                instrucciones.add((Instruccion) obj);
            }
        }

        // Liberar el bloque del swap
        memoriaVirtual.liberarBloque(dirSwap, tamanoEnSwap);
        procesosSuspendidos.remove(0);

        // Crear el BCP en la nueva dirección y restaurar sus valores
        BCP bcpRestaurado = new BCP(memoria, direccionBase, pe.getId(), 1);
        bcpRestaurado.restaurarValores(valoresBCP);

        // Restaurar el proceso en la partición (carga instrucciones + encola)
        admitirEnParticion(bcpRestaurado, instrucciones, indiceNuevo);

        System.out.println("[SWAP] Proceso " + bcpRestaurado.getId()
                + " reactivado desde swap (kernel=" + direccionBase
                + ", usuario=" + particionador.getBaseParticion(indiceNuevo) + ")");
    }

    private void admitirEnParticion(BCP bcp, List<Instruccion> instrucciones, int indiceParticion) {
        int base = particionador.getBaseParticion(indiceParticion);
        bcp.setBase(base);
        bcp.setAlcance(instrucciones.size());
        bcp.setPc(base);
        bcp.setEstado(EstadoProceso.READY);

        int pos = base;
        for (Instruccion instr : instrucciones) {
            memoria.escribir(pos++, instr);
        }

        listaDeTrabajos.agregar(bcp);
    }

    public boolean desbloquearProceso(int valor) {
        if (procesosBloqueadosInput.isEmpty()) {
            return false;
        }

        BCP bcp = procesosBloqueadosInput.remove(0);
        bcp.setDx(valor);
        bcp.setEstado(EstadoProceso.READY);
        listaDeTrabajos.agregar(bcp);

        return true;
    }

    public boolean hayProcesosBloqueados() {
        return !procesosBloqueadosInput.isEmpty();
    }

    /* ==================== CONSULTAS PARA LA GUI ==================== */

    public int getTotalProcesosActivos() {
        return listaDeTrabajos.getCantidad()
             + procesosBloqueadosIO.size()
             + procesosBloqueadosInput.size()
             + (despachador.getEjecutorActual() != null ? 1 : 0);
    }

    public int getTotalProcesos() {
        return getTotalProcesosActivos() + procesosSuspendidos.size();
    }

    public boolean hayProcesosActivos() {
        return !listaDeTrabajos.estaVacia()
            || despachador.procesoActualOcupaCPU()
            || !procesosBloqueadosIO.isEmpty()
            || !procesosBloqueadosInput.isEmpty();
    }

    public BCP getProcesoActual() {
        if (despachador.getEjecutorActual() != null) {
            return despachador.getEjecutorActual().getBcp();
        }
        if (!listaDeTrabajos.estaVacia()) {
            return listaDeTrabajos.verPrimero();
        }
        if (!procesosBloqueadosInput.isEmpty()) {
            return procesosBloqueadosInput.get(0);
        }
        if (!procesosBloqueadosIO.isEmpty()) {
            return procesosBloqueadosIO.get(0);
        }
        return null;
    }

    public ListaDeTrabajos getListaDeTrabajos() {
        return listaDeTrabajos;
    }

    public int getCantidadEnEspera() {
        return procesosSuspendidos.size();
    }

    public List<BCPTerminado> getProcesosTerminados() {
        return procesosTerminados;
    }

    public List<BCP> getProcesosBloqueados() {
        List<BCP> todos = new ArrayList<>();
        todos.addAll(procesosBloqueadosIO);
        todos.addAll(procesosBloqueadosInput);
        return todos;
    }

    public List<BCP> getProcesosBloqueadosIO() {
        return procesosBloqueadosIO;
    }

    public List<BCP> getProcesosBloqueadosInput() {
        return procesosBloqueadosInput;
    }

    /**
     * @return lista de procesos suspendidos (en swap), para la UI.
     */
    public List<ProcesoEnEspera> getProcesosEnEspera() {
        return new ArrayList<>(procesosSuspendidos);
    }

    public MemoriaVirtual getMemoriaVirtual() {
        return memoriaVirtual;
    }

    /* ==================== CALLBACKS DE INTERRUPCIONES ==================== */

    public void setSalidaPantalla(java.util.function.Consumer<String> callback) {
        this.salidaPantalla = callback;
        despachador.setSalidaPantalla(callback);
    }

    public void setSolicitudTeclado(java.util.function.Consumer<BCP> callback) {
        despachador.setSolicitudTeclado(callback);
    }

    public void setSolicitudArchivo(java.util.function.Consumer<BCP> callback) {
        despachador.setSolicitudArchivo(callback);
    }
}