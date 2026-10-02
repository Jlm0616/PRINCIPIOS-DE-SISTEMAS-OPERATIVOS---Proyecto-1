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
 *
 * FCFS puro no apropiativo estricto:
 *   - Si un proceso se bloquea por INT 09H (input), la CPU queda
 *     idle hasta que se desbloquee. NO se ejecuta otro proceso.
 *   - Si un proceso se bloquea por INT 21H (E/S a disco), se simula
 *     una tardanza de N segundos. El proceso sigue siendo el actual
 *     (la CPU espera). Cuando el disco "responde", vuelve a RUNNING.
 */
public class GestorProcesos {

    /** Máximo de BCPs permitidos en memoria principal (restricción del enunciado). */
    public static final int MAX_PROCESOS = 5;

    /** Tardanza simulada del disco según la operación AH de INT 21H. */
    private static final int TARDANZA_CREAR     = 1;   // AH=3Ch
    private static final int TARDANZA_ABRIR     = 1;   // AH=3Dh
    private static final int TARDANZA_LEER      = 2;   // AH=4Dh
    private static final int TARDANZA_ESCRIBIR  = 2;   // AH=40h
    private static final int TARDANZA_ELIMINAR  = 1;   // AH=41h
    private static final int TARDANZA_DEFAULT   = 1;

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

        Object[] instruccionesArr = instrucciones.toArray();
        memoriaVirtual.escribirBloque(dirSwap, instruccionesArr);

        ProcesoEnEspera pe = new ProcesoEnEspera(
                id, EstadoProceso.NEW, tamanoInstrucciones);
        pe.setDireccionBaseEnSwap(dirSwap);
        procesosSuspendidos.add(pe);

        System.out.println("[SWAP] Proceso " + id
                + " en NEW (swap, dir=" + dirSwap
                + ", instrucciones=" + tamanoInstrucciones + ")");

        return ResultadoCarga.enEspera();
    }

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
        // 1. Si hay procesos esperando input del usuario, CPU idle.
        if (!procesosBloqueadosInput.isEmpty()) {
            return true;
        }

        // 2. Si el proceso actual está BLOCKED por E/S a disco (INT 21H),
        //    decrementar el contador de tardanza. NO cambiar de proceso.
        if (despachador.getEjecutorActual() != null) {
            BCP actualBloqueado = despachador.getEjecutorActual().getBcp();
            if (actualBloqueado.getEstado() == EstadoProceso.BLOCKED
                    && actualBloqueado.getPesoPendiente() > 0) {

                actualBloqueado.setPesoPendiente(actualBloqueado.getPesoPendiente() - 1);

                System.out.println("[FCFS] Proceso " + actualBloqueado.getId()
                        + " esperando disco... ("
                        + actualBloqueado.getPesoPendiente() + "s restantes)");

                if (actualBloqueado.getPesoPendiente() == 0) {
                    actualBloqueado.setEstado(EstadoProceso.RUNNING);
                    System.out.println("[FCFS] Proceso " + actualBloqueado.getId()
                            + " desbloqueado (disco respondio)");
                }
                return true;
            }
        }

        // 3. Si no hay proceso en CPU, despachar el siguiente.
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

            System.out.println("[FCFS] Cambio de proceso -> ahora ejecuta ID "
                    + siguiente.getId());

            listaDeTrabajos.sacarPrimero();
            despachador.despachar(siguiente, memoria);
        }

        boolean sigueVivo = despachador.ejecutarUnPaso();
        BCP actual = despachador.getEjecutorActual().getBcp();
        EstadoProceso estado = actual.getEstado();

        if (estado == EstadoProceso.EXIT) {
            System.out.println("[FCFS] Proceso " + actual.getId()
                    + " TERMINO en instruccion: " + leerInstruccionActual(actual));

            procesoTerminado(actual);
            despachador.limpiarEjecutor();
        } else if (estado == EstadoProceso.BLOCKED) {
            if (fueBloqueoIO(actual)) {
                // INT 21H: E/S a disco. El proceso sigue siendo el actual.
                // Se le asigna una tardanza simulada. El contador se decrementa
                // en los siguientes ejecutarUnPaso().
                int tardanza = calcularTardanzaDisco(actual);
                actual.setPesoPendiente(tardanza);

                System.out.println("[FCFS] Proceso " + actual.getId()
                        + " BLOCKED por IO en: " + leerInstruccionActual(actual)
                        + " (tardanza=" + tardanza + "s)");
            } else {
                // INT 09H: input del usuario.
                System.out.println("[FCFS] Proceso " + actual.getId()
                        + " BLOCKED en: " + leerInstruccionActual(actual)
                        + " esperando input");

                despachador.guardarContexto(actual);
                actual.setCpuAsignado(-1);
                despachador.limpiarEjecutor();
                procesosBloqueadosInput.add(actual);
            }
        }

        return true;
    }

    /**
     * Calcula la tardanza simulada del disco según la operación AH.
     *
     * @param bcp BCP del proceso (se lee el registro AH)
     * @return segundos de tardanza
     */
    private int calcularTardanzaDisco(BCP bcp) {
        int ah = bcp.getAh();
        switch (ah) {
            case 0x3C: return TARDANZA_CREAR;
            case 0x3D: return TARDANZA_ABRIR;
            case 0x4D: return TARDANZA_LEER;
            case 0x40: return TARDANZA_ESCRIBIR;
            case 0x41: return TARDANZA_ELIMINAR;
            default:   return TARDANZA_DEFAULT;
        }
    }

    /**
     * Lee la instrucción que está en la dirección actual del IR del BCP.
     */
    private String leerInstruccionActual(BCP bcp) {
        int ir = bcp.getIr();
        if (ir < 0) {
            return "(sin IR)";
        }
        Instruccion instr = memoria.leerInstruccion(ir);
        if (instr == null) {
            return "(posicion " + ir + " vacia)";
        }
        return "[" + ir + "] " + instr.toString();
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
     * Si hay un proceso esperando al disco (INT 21H), también se respeta
     * la tardanza (cada paso decrementa el contador).
     */
    public int ejecutarAutomatico(Runnable onPaso) {
        int pasos = 0;
        while (hayProcesosActivos()) {

            if (!procesosBloqueadosInput.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (onPaso != null) onPaso.run();
                continue;
            }

            ejecutarUnPaso();
            pasos++;

            if (onPaso != null) onPaso.run();

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
        bcp.setEstado(EstadoProceso.EXIT);
        bcp.marcarFin();
        bcp.setCpuAsignado(-1);

        BCPTerminado snapshot = new BCPTerminado(
            bcp.getId(),
            bcp.getEstado(),
            bcp.getTiempoInicio(),
            bcp.getTiempoFin(),
            bcp.getBase(),
            bcp.getAlcance(),
            bcp.getPrioridad()
        );

        this.procesosTerminados.add(snapshot);

        int base = bcp.getBase();
        int alcance = bcp.getAlcance();

        int indice = particionador.indiceDesdePosicion(base);
        particionador.liberarParticion(indice);

        for (int i = 0; i < alcance; i++) {
            memoria.escribir(base + i, null);
        }

        memoria.liberarBloque(bcp.getDireccionBase(), BCP.POSICIONES_REQUERIDAS);

        reactivarSiguienteProceso();
    }

    private void reactivarSiguienteProceso() {
        if (procesosSuspendidos.isEmpty()) {
            return;
        }

        int indiceNuevo = particionador.asignarParticion();
        if (indiceNuevo == -1) {
            return;
        }

        ProcesoEnEspera pe = procesosSuspendidos.get(0);

        int direccionBase = memoria.reservarBloque(BCP.POSICIONES_REQUERIDAS);
        if (direccionBase == -1) {
            particionador.liberarParticion(indiceNuevo);
            return;
        }

        int dirSwap = pe.getDireccionBaseEnSwap();
        int tamanoInstrucciones = pe.getTamanoEnSwap();

        Object[] instruccionesArr = memoriaVirtual.leerBloque(dirSwap, tamanoInstrucciones);

        memoriaVirtual.liberarBloque(dirSwap, tamanoInstrucciones);
        procesosSuspendidos.remove(0);

        List<Instruccion> instrucciones = new ArrayList<>();
        for (Object obj : instruccionesArr) {
            if (obj instanceof Instruccion) {
                instrucciones.add((Instruccion) obj);
            }
        }

        BCP bcpRestaurado = new BCP(memoria, direccionBase, pe.getId(), 1);

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
        listaDeTrabajos.agregarAlPrincipio(bcp);

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