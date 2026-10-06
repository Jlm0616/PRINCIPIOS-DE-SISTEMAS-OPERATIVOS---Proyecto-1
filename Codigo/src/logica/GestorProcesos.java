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
 * Modelo de listas (segun el profe):
 *   - ListaProcesos: procesos en RAM (READY, RUNNING, BLOCKED).
 *   - ListaDeTrabajos: procesos en disco (NEW).
 *
 * Zonas de la Memoria (Stallings, seccion 3.3):
 *   - ListaProcesos: direcciones de BCPs READY.
 *   - BCPs: 5 × 26 posiciones.
 *   - TablaMemoria: bloques asignados.
 *   - TablaArchivos: archivos abiertos.
 *
 * FCFS puro no apropiativo estricto:
 *   - Si un proceso se bloquea por INT 09H, la CPU queda idle.
 *   - Si un proceso se bloquea por INT 21H, se simula una tardanza.
 *
 * NOTA: DX es un String (nombre de archivo para INT 21H).
 * Para operaciones con valores numericos (como INT 09H), se usa setDxAsInt().
 *
 * INDICE DEL DISCO: tiene 4 posiciones por archivo [nombre, inicio, fin, zona].
 * La zona puede ser:
 *   - ZONA_PRINCIPAL → el .asm está en la zona de archivos (94-511).
 *   - ZONA_VIRTUAL   → el .asm está en la zona de swap (30-93).
 * Cuando un proceso pasa de swap a RAM, se copia a archivos y se actualiza el índice.
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
    private ListaProcesos listaProcesos;
    private ListaDeTrabajos listaDeTrabajos;
    private Planificador planificador;
    private Despachador despachador;
    private ParticionadorDinamico particionador;
    private Disco disco;

    private int siguienteId;
    private List<BCPTerminado> procesosTerminados;
    private List<BCP> procesosBloqueadosInput;

    private java.util.function.Consumer<String> salidaPantalla;

    public GestorProcesos(Memoria memoria,
                          CPU cpu,
                          ListaProcesos listaProcesos,
                          ListaDeTrabajos listaDeTrabajos,
                          ParticionadorDinamico particionador,
                          Disco disco) {
        this.memoria = memoria;
        this.cpu = cpu;
        this.listaProcesos = listaProcesos;
        this.listaDeTrabajos = listaDeTrabajos;
        this.particionador = particionador;
        this.planificador = new Planificador(listaProcesos);
        this.despachador = new Despachador(cpu, memoria);
        this.siguienteId = 1;
        this.procesosTerminados = new ArrayList<>();
        this.procesosBloqueadosInput = new ArrayList<>();
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
        int id = siguienteId;
        String nombreArchivo = archivo.getName();

        int base = particionador.asignarParticion(tamanoNecesario, id);
        if (base != -1) {
            BCP bcp = crearProcesoEnParticion(instrucciones, base, id);
            if (bcp != null) {
                siguienteId++;
                return ResultadoCarga.exito(bcp);
            }
            particionador.liberarParticion(base, tamanoNecesario, id);
        }

        ResultadoCarga resultadoSwap = suspenderEnSwap(instrucciones, id, nombreArchivo);
        if (resultadoSwap.getEstado() == ResultadoCarga.Estado.EN_ESPERA) {
            siguienteId++;
            return resultadoSwap;
        }

        return ResultadoCarga.error(
            "No hay espacio en memoria principal (ni kernel ni partición), "
            + "y la memoria virtual del disco está llena.\n"
            + "Libera espacio o aumenta el disco en 'Configurar'.");
    }

    /**
     * Suspende un proceso en swap y lo registra en el índice del disco
     * con la zona VIRTUAL.
     */
    private ResultadoCarga suspenderEnSwap(List<Instruccion> instrucciones,
                                           int id,
                                           String nombreArchivo) {
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

        Object[] instruccionesArr = instrucciones.toArray();
        disco.escribirBloqueSwap(dirSwap, instruccionesArr);

        // Registrar en el índice del disco con zona VIRTUAL
        int finSwap = dirSwap + tamanoInstrucciones - 1;
        boolean registrado = disco.registrarArchivo(
                nombreArchivo, dirSwap, finSwap, Disco.ZONA_VIRTUAL);
        if (!registrado) {
            disco.liberarBloqueSwap(dirSwap, tamanoInstrucciones);
            return ResultadoCarga.error(
                "No se pudo registrar el archivo en el índice del disco (lleno).");
        }

        ProcesoEnEspera pe = new ProcesoEnEspera(
                id, EstadoProceso.NEW, tamanoInstrucciones);
        pe.setDireccionBaseEnSwap(dirSwap);
        pe.setNombreArchivo(nombreArchivo);
        listaDeTrabajos.agregar(pe);

        System.out.println("[SWAP] Proceso " + id
                + " (" + nombreArchivo + ") en NEW (lista de trabajos, dir swap=" + dirSwap
                + ", instrucciones=" + tamanoInstrucciones + ")");

        return ResultadoCarga.enEspera();
    }

    private BCP crearProcesoEnParticion(List<Instruccion> instrucciones, int base, int id) {
        int direccionBase = memoria.reservarBloqueBCP();
        if (direccionBase == -1) {
            return null;
        }
        BCP bcp = new BCP(memoria, direccionBase, id, 1);
        admitirEnParticion(bcp, instrucciones, base);
        return bcp;
    }

    /* ==================== EJECUCIÓN PASO A PASO ==================== */

    public boolean ejecutarUnPaso() {
        // 1. Si hay procesos esperando input, CPU idle.
        if (!procesosBloqueadosInput.isEmpty()) {
            return true;
        }

        // 2. Si el proceso actual está BLOCKED por INT 21H, decrementar tardanza.
        if (despachador.getEjecutorActual() != null) {
            BCP actualBloqueado = despachador.getBcpActual();
            if (actualBloqueado != null
                    && actualBloqueado.getEstado() == EstadoProceso.BLOCKED
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
                BCP anterior = despachador.getBcpActual();
                if (anterior != null && anterior.getEstado() == EstadoProceso.EXIT) {
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

            int dir = listaProcesos.sacarPrimeraDireccion();
            despachador.despachar(dir);
        }

        boolean sigueVivo = despachador.ejecutarUnPaso();
        BCP actual = despachador.getBcpActual();
        if (actual == null) return false;
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

                despachador.guardarContexto();
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
        if (ir < 0) return "(sin IR)";
        Instruccion instr = memoria.leerInstruccion(ir);
        if (instr == null) return "(posicion " + ir + " vacia)";
        return "[" + ir + "] " + instr.toString();
    }

    private boolean fueBloqueoIO(BCP bcp) {
        int ir = bcp.getIr();
        if (ir < 0) return false;
        Instruccion instr = memoria.leerInstruccion(ir);
        if (instr == null) return false;
        if (!"INT".equals(instr.getOpcode())) return false;
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
        int idProceso = bcp.getId();

        particionador.liberarParticion(base, alcance, idProceso);
        memoria.liberarBloqueUsuario(base, alcance);

        memoria.liberarBloqueBCP(bcp.getDireccionBase());

        reactivarSiguienteProceso();
    }

    /**
     * Reactiva el siguiente proceso de la ListaDeTrabajos.
     *
     * Pasos:
     *   1. Buscar el proceso en swap.
     *   2. Asignar partición + BCP.
     *   3. Copiar las instrucciones de swap a RAM.
     *   4. Copiar las instrucciones a la zona de archivos del disco.
     *   5. Actualizar el índice: VIRTUAL → PRINCIPAL, con las nuevas posiciones.
     *   6. Liberar el bloque de swap.
     *   7. Agregar el proceso a ListaProcesos.
     */
    private void reactivarSiguienteProceso() {
        if (listaDeTrabajos.estaVacia()) return;

        ProcesoEnEspera pe = listaDeTrabajos.verPrimero();
        int tamanoInstrucciones = pe.getTamanoEnSwap();
        int id = pe.getId();
        String nombreArchivo = pe.getNombreArchivo();

        int base = particionador.asignarParticion(tamanoInstrucciones, id);
        if (base == -1) return;

        int direccionBase = memoria.reservarBloqueBCP();
        if (direccionBase == -1) {
            particionador.liberarParticion(base, tamanoInstrucciones, id);
            return;
        }

        int dirSwap = pe.getDireccionBaseEnSwap();
        Object[] instruccionesArr = disco.leerBloqueSwap(dirSwap, tamanoInstrucciones);

        List<Instruccion> instrucciones = new ArrayList<>();
        for (Object obj : instruccionesArr) {
            if (obj instanceof Instruccion) {
                instrucciones.add((Instruccion) obj);
            }
        }

        // === COPIAR A ARCHIVOS (si hay espacio) ===
        int nuevoInicioArchivos = disco.reservarBloqueArchivo(tamanoInstrucciones);
        if (nuevoInicioArchivos != -1) {
            disco.escribirBloqueArchivo(nuevoInicioArchivos, instruccionesArr);
            int nuevoFinArchivos = nuevoInicioArchivos + tamanoInstrucciones - 1;

            // Actualizar el índice del disco
            if (nombreArchivo != null) {
                disco.actualizarZona(nombreArchivo, Disco.ZONA_PRINCIPAL,
                        nuevoInicioArchivos, nuevoFinArchivos);
            }
        } else {
            System.out.println("[WARNING] No hay espacio en archivos para copiar "
                    + nombreArchivo + ". Se mantiene solo en swap.");
        }

        // === LIBERAR SWAP Y LISTA DE TRABAJOS ===
        disco.liberarBloqueSwap(dirSwap, tamanoInstrucciones);
        listaDeTrabajos.sacarPrimero();

        // === CREAR BCP Y ADMITIR ===
        BCP bcpRestaurado = new BCP(memoria, direccionBase, id, 1);
        admitirEnParticion(bcpRestaurado, instrucciones, base);

        System.out.println("[SWAP] Proceso " + id + " (" + nombreArchivo
                + ") reactivado desde ListaDeTrabajos (kernel=" + direccionBase
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

        listaProcesos.agregar(bcp);
    }

    /**
     * Desbloquea el primer proceso esperando input de teclado.
     *
     * El valor viene como int (0-255) desde la GUI, y se guarda en DX
     * como String (convertido con setDxAsInt).
     */
    public boolean desbloquearProceso(int valor) {
        if (procesosBloqueadosInput.isEmpty()) return false;

        BCP bcp = procesosBloqueadosInput.remove(0);
        bcp.setDxAsInt(valor);
        bcp.setEstado(EstadoProceso.READY);
        listaProcesos.agregarAlPrincipio(bcp);

        return true;
    }

    public boolean hayProcesosBloqueados() {
        return !procesosBloqueadosInput.isEmpty();
    }

    /* ==================== CONSULTAS PARA LA GUI ==================== */

    public int getTotalProcesosActivos() {
        return listaProcesos.getCantidad()
             + procesosBloqueadosInput.size()
             + (despachador.getEjecutorActual() != null ? 1 : 0);
    }

    public int getTotalProcesos() {
        return getTotalProcesosActivos() + listaDeTrabajos.getCantidad();
    }

    public boolean hayProcesosActivos() {
        if (despachador.getEjecutorActual() != null) {
            BCP b = despachador.getBcpActual();
            if (b != null
                    && b.getEstado() == EstadoProceso.BLOCKED
                    && b.getPesoPendiente() > 0) {
                return true;
            }
        }

        return !listaProcesos.estaVacia()
            || despachador.procesoActualOcupaCPU()
            || !procesosBloqueadosInput.isEmpty();
    }

    public BCP getProcesoActual() {
        BCP actual = despachador.getBcpActual();
        if (actual != null) return actual;

        if (!listaProcesos.estaVacia()) {
            return listaProcesos.verPrimero();
        }
        if (!procesosBloqueadosInput.isEmpty()) {
            return procesosBloqueadosInput.get(0);
        }
        return null;
    }

    public ListaProcesos getListaProcesos() { return listaProcesos; }
    public ListaDeTrabajos getListaDeTrabajos() { return listaDeTrabajos; }

    public int getCantidadEnEspera() {
        return listaDeTrabajos.getCantidad();
    }

    public List<BCPTerminado> getProcesosTerminados() { return procesosTerminados; }

    public List<BCP> getProcesosBloqueados() {
        return new ArrayList<>(procesosBloqueadosInput);
    }

    public List<BCP> getProcesosBloqueadosInput() {
        return new ArrayList<>(procesosBloqueadosInput);
    }

    public List<ProcesoEnEspera> getProcesosEnEspera() {
        return new ArrayList<>(listaDeTrabajos.toList());
    }

    public Disco getDisco() { return disco; }
    public ParticionadorDinamico getParticionador() { return particionador; }

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