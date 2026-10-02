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
 *   - Manejar swap in/out cuando no hay partición libre o kernel lleno.
 *   - Exponer consultas para la GUI.
 *
 * Modelo de swap (según el profe):
 *   - El BCP NUNCA se mueve de memoria principal.
 *   - Solo las INSTRUCCIONES van a swap cuando no hay partición libre.
 *   - Un proceso en swap está en estado NEW.
 *   - Cuando entra a memoria, se le crea el BCP y pasa a READY.
 *
 * Máximo: 5 BCPs en kernel. Los demás esperan en swap (NEW).
 */
public class GestorProcesos {

    /** Máximo de BCPs permitidos en memoria principal (restricción del enunciado). */
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
     *   1. Si hay espacio en kernel (para el BCP) Y partición de usuario
     *      → cargar en READY (activo).
     *   2. Si no → suspender en swap (NEW, solo instrucciones).
     *   3. Si tampoco → error con mensaje claro.
     *
     * El BCP SIEMPRE vive en kernel. Solo las instrucciones se mueven
     * a swap cuando no hay espacio en memoria principal.
     */
    public ResultadoCarga cargarPrograma(File archivo) {
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

        // 2. No cabe en memoria: intentar swap (NEW)
        ResultadoCarga resultadoSwap = suspenderEnSwap(instrucciones);
        if (resultadoSwap.getEstado() == ResultadoCarga.Estado.EN_ESPERA) {
            return resultadoSwap;
        }

        // 3. No hay ni kernel, ni partición, ni swap
        return ResultadoCarga.error(
            "No hay espacio en memoria principal (ni kernel ni partición), "
            + "y la memoria virtual está llena.\n"
            + "Libera espacio o aumenta la memoria en 'Configurar'.");
    }

    /**
     * Suspende un proceso moviéndolo al swap.
     *
     * Según el profe, el BCP NUNCA se mueve de memoria principal.
     * Por eso, acá solo se guardan las INSTRUCCIONES en el swap.
     * El BCP no existe todavía: se creará cuando el proceso entre
     * a memoria principal.
     *
     * El proceso queda en estado NEW.
     *
     * @return EN_ESPERA si se suspendió, ERROR si el swap está lleno
     */
    private ResultadoCarga suspenderEnSwap(List<Instruccion> instrucciones) {
        int tamanoInstrucciones = instrucciones.size();

        if (memoriaVirtual.getEspacioLibre() < tamanoInstrucciones) {
            return ResultadoCarga.error(
                "La memoria virtual no tiene espacio para las instrucciones.\n"
                + "Necesario: " + tamanoInstrucciones + " posiciones, "
                + "libre: " + memoriaVirtual.getEspacioLibre() + ".");
        }

        int dirSwap = memoriaVirtual.reservarBloque(tamanoInstrucciones);
        if (dirSwap == -1) {
            return ResultadoCarga.error("No hay bloque contiguo libre en la memoria virtual.");
        }

        int id = siguienteId++;

        // Solo las instrucciones van a swap (el BCP no existe todavía)
        Object[] instruccionesArr = instrucciones.toArray();
        memoriaVirtual.escribirBloque(dirSwap, instruccionesArr);

        // Registrar metadatos en la tabla del SO
        ProcesoEnEspera pe = new ProcesoEnEspera(
                id, EstadoProceso.NEW, tamanoInstrucciones);
        pe.setDireccionBaseEnSwap(dirSwap);
        procesosSuspendidos.add(pe);

        System.out.println("[SWAP] Proceso " + id
                + " en NEW (swap, dir=" + dirSwap
                + ", instrucciones=" + tamanoInstrucciones + ")");

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
     * Ejecuta todos los procesos en modo automático.
     *
     * Cada paso dura 1 segundo real (Thread.sleep).
     * Si hay procesos esperando input, se pausa (el tiempo sigue contando).
     *
     * @param onPaso callback que se invoca después de cada paso
     *               (para que la GUI se actualice)
     * @return cantidad de pasos ejecutados
     */
    public int ejecutarAutomatico(Runnable onPaso) {
        int pasos = 0;
        while (hayProcesosActivos()) {

            // Si hay procesos esperando input, pausar
            if (!procesosBloqueadosInput.isEmpty()) {
                try {
                    Thread.sleep(1000);   // el tiempo sigue contando
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (onPaso != null) onPaso.run();
                continue;
            }

            // Ejecutar 1 paso
            ejecutarUnPaso();
            pasos++;

            // Notificar a la GUI
            if (onPaso != null) onPaso.run();

            // Respetar 1 segundo real
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

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
     *   3. Leer SOLO las instrucciones del swap.
     *   4. Liberar el bloque del swap.
     *   5. Crear el BCP en kernel (nuevo, nunca estuvo en memoria).
     *   6. Cargar las instrucciones en la partición de usuario.
     *   7. Marcar el estado READY y encolar.
     *
     * Si algo falla, el proceso queda intacto en el swap.
     */
    private void reactivarSiguienteProceso() {
        if (procesosSuspendidos.isEmpty()) {
            return;
        }

        int indiceNuevo = particionador.asignarParticion();
        if (indiceNuevo == -1) {
            return;
        }

        ProcesoEnEspera pe = procesosSuspendidos.get(0);

        // Reservar bloque en kernel para el BCP nuevo
        int direccionBase = memoria.reservarBloque(BCP.POSICIONES_REQUERIDAS);
        if (direccionBase == -1) {
            particionador.liberarParticion(indiceNuevo);
            return;
        }

        // Leer SOLO las instrucciones del swap
        int dirSwap = pe.getDireccionBaseEnSwap();
        int tamanoInstrucciones = pe.getTamanoEnSwap();

        Object[] instruccionesArr = memoriaVirtual.leerBloque(dirSwap, tamanoInstrucciones);

        // Liberar bloque del swap
        memoriaVirtual.liberarBloque(dirSwap, tamanoInstrucciones);
        procesosSuspendidos.remove(0);

        // Convertir Object[] a List<Instruccion>
        List<Instruccion> instrucciones = new ArrayList<>();
        for (Object obj : instruccionesArr) {
            if (obj instanceof Instruccion) {
                instrucciones.add((Instruccion) obj);
            }
        }

        // Crear BCP nuevo en kernel (nunca estuvo en memoria)
        BCP bcpRestaurado = new BCP(memoria, direccionBase, pe.getId(), 1);

        // Cargar instrucciones en usuario + encolar
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