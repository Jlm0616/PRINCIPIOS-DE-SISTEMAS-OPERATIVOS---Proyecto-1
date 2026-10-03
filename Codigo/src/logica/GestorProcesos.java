package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Instruccion;
import modelo.Memoria;
import modelo.Disco;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Fachada de orquestación del ciclo de vida y ejecución de procesos.
 *
 * FCFS puro no apropiativo estricto:
 *   - Si un proceso se bloquea por INT 09H (input), la CPU queda
 *     idle hasta que se desbloquee. NO se ejecuta otro proceso.
 *   - Si un proceso se bloquea por INT 21H (E/S a disco), se simula
 *     una tardanza de N segundos. El proceso sigue siendo el actual.
 *
 * Particionamiento dinámico (first-fit):
 *   - Cada proceso ocupa exactamente las posiciones que necesita.
 *   - Al liberar, se fusionan huecos adyacentes (coalescing).
 */
public class GestorProcesos {

    public static final int MAX_PROCESOS = 5;

    private static final int TARDANZA_CREAR     = 1;
    private static final int TARDANZA_ABRIR     = 1;
    private static final int TARDANZA_LEER      = 2;
    private static final int TARDANZA_ESCRIBIR  = 2;
    private static final int TARDANZA_ELIMINAR  = 1;
    private static final int TARDANZA_DEFAULT   = 1;

    private Memoria memoria;
    private CPU cpu;
    private ListaDeTrabajos listaDeTrabajos;
    private Planificador planificador;
    private Despachador despachador;
    private ParticionadorDinamico particionador;
    private Disco disco;

    private int siguienteId;
    private List<BCPTerminado> procesosTerminados;
    private List<BCP> procesosBloqueadosIO;
    private List<BCP> procesosBloqueadosInput;
    private List<ProcesoEnEspera> procesosSuspendidos;

    private java.util.function.Consumer<String> salidaPantalla;

    public GestorProcesos(Memoria memoria,
                          CPU cpu,
                          ListaDeTrabajos listaDeTrabajos,
                          ParticionadorDinamico particionador,
                          Disco disco) {
        this.memoria = memoria;
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
        int tamanoNecesario = instrucciones.size();

        // ¿Hay un hueco suficientemente grande en la zona usuario?
        int base = particionador.asignarParticion(tamanoNecesario);
        if (base != -1) {
            BCP bcp = crearProcesoEnParticion(instrucciones, base);
            if (bcp != null) {
                return ResultadoCarga.exito(bcp);
            }
            // No hay espacio en kernel: liberar la partición recién asignada
            particionador.liberarParticion(base, tamanoNecesario);
        }

        // No cabe en memoria principal: intentar swap
        ResultadoCarga resultadoSwap = suspenderEnSwap(instrucciones);
        if (resultadoSwap.getEstado() == ResultadoCarga.Estado.EN_ESPERA) {
            return resultadoSwap;
        }

        return ResultadoCarga.error(
            "No hay espacio en memoria principal (ni kernel ni partición), "
            + "y la memoria virtual del disco está llena.\n"
            + "Libera espacio o aumenta el disco en 'Configurar'.");
    }

    private ResultadoCarga suspenderEnSwap(List<Instruccion> instrucciones) {
        int tamanoInstrucciones = instrucciones.size();

        if (disco.getEspacioSwapLibre() < tamanoInstrucciones) {
            return ResultadoCarga.error(
                "La memoria virtual del disco no tiene espacio.\n"
                + "Necesario: " + tamanoInstrucciones + " posiciones, "
                + "libre: " + disco.getEspacioSwapLibre() + ".");
        }

        int dirSwap = disco.reservarBloqueSwap(tamanoInstrucciones);
        if (dirSwap == -1) {
            return ResultadoCarga.error("No hay bloque contiguo libre en el swap del disco.");
        }

        int id = siguienteId++;

        Object[] instruccionesArr = instrucciones.toArray();
        disco.escribirBloqueSwap(dirSwap, instruccionesArr);

        ProcesoEnEspera pe = new ProcesoEnEspera(
                id, EstadoProceso.NEW, tamanoInstrucciones);
        pe.setDireccionBaseEnSwap(dirSwap);
        procesosSuspendidos.add(pe);

        System.out.println("[SWAP] Proceso " + id
                + " en NEW (swap disco, dir=" + dirSwap
                + ", instrucciones=" + tamanoInstrucciones + ")");

        return ResultadoCarga.enEspera();
    }

    private BCP crearProcesoEnParticion(List<Instruccion> instrucciones, int base) {
        int direccionBase = memoria.reservarBloque(BCP.POSICIONES_REQUERIDAS);
        if (direccionBase == -1) {
            return null;
        }
        BCP bcp = new BCP(memoria, direccionBase, siguienteId++, 1);
        admitirEnParticion(bcp, instrucciones, base);
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
                int tardanza = calcularTardanzaDisco(actual);
                actual.setPesoPendiente(tardanza);

                System.out.println("[FCFS] Proceso " + actual.getId()
                        + " BLOCKED por IO en: " + leerInstruccionActual(actual)
                        + " (tardanza=" + tardanza + "s)");
            } else {
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

        // Liberar la particion dinamica (con coalescing)
        particionador.liberarParticion(base, alcance);

        // Limpiar las posiciones de la zona usuario
        for (int i = 0; i < alcance; i++) {
            memoria.escribir(base + i, null);
        }

        // Liberar el bloque del BCP en el kernel
        memoria.liberarBloque(bcp.getDireccionBase(), BCP.POSICIONES_REQUERIDAS);

        reactivarSiguienteProceso();
    }

    private void reactivarSiguienteProceso() {
        if (procesosSuspendidos.isEmpty()) {
            return;
        }

        ProcesoEnEspera pe = procesosSuspendidos.get(0);
        int tamanoInstrucciones = pe.getTamanoEnSwap();

        // ¿Hay hueco en la zona usuario?
        int base = particionador.asignarParticion(tamanoInstrucciones);
        if (base == -1) {
            return;   // no hay hueco suficientemente grande
        }

        // ¿Hay espacio en kernel?
        int direccionBase = memoria.reservarBloque(BCP.POSICIONES_REQUERIDAS);
        if (direccionBase == -1) {
            particionador.liberarParticion(base, tamanoInstrucciones);
            return;
        }

        int dirSwap = pe.getDireccionBaseEnSwap();
        Object[] instruccionesArr = disco.leerBloqueSwap(dirSwap, tamanoInstrucciones);

        disco.liberarBloqueSwap(dirSwap, tamanoInstrucciones);
        procesosSuspendidos.remove(0);

        List<Instruccion> instrucciones = new ArrayList<>();
        for (Object obj : instruccionesArr) {
            if (obj instanceof Instruccion) {
                instrucciones.add((Instruccion) obj);
            }
        }

        BCP bcpRestaurado = new BCP(memoria, direccionBase, pe.getId(), 1);

        admitirEnParticion(bcpRestaurado, instrucciones, base);

        System.out.println("[SWAP] Proceso " + bcpRestaurado.getId()
                + " reactivado desde swap (kernel=" + direccionBase
                + ", usuario=" + base + ")");
    }

    private void admitirEnParticion(BCP bcp, List<Instruccion> instrucciones, int base) {
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
        if (despachador.getEjecutorActual() != null) {
            BCP b = despachador.getEjecutorActual().getBcp();
            if (b.getEstado() == EstadoProceso.BLOCKED
                    && b.getPesoPendiente() > 0) {
                return true;
            }
        }

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

    public Disco getDisco() {
        return disco;
    }

    public ParticionadorDinamico getParticionador() {
        return particionador;
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