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
 *   - ListaProcesos: cola FCFS en Java (solo para el planificador).
 *   - ListaDeTrabajos: procesos que esperan entrar a RAM (en RAM, apunta al disco).
 *
 * Zonas de la Memoria (Stallings, seccion 3.3):
 *   - ListaDeTrabajos: info de .asm que esperan entrar a RAM.
 *   - BCPs: bcpsQueCaben × 30 posiciones.
 *   - TablaMemoria: bcpsQueCaben × 3 posiciones.
 *
 * INDICE DEL DISCO: tiene 2 secciones:
 *   - Indice ASM: archivos .asm cargados.
 *   - Indice PROCESO: archivos creados por procesos (datos.txt).
 * Cada entrada ocupa 4 posiciones: [nombre, inicio, fin, zona].
 *
 * IMPORTANTE: cuando un .asm entra a RAM, sus instrucciones se copian
 * TAMBIEN a la zona de archivos del disco (para que el indice ASM
 * apunte a la zona de archivos, no a RAM).
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

        // 1. Intentar entrar a RAM
        int base = particionador.asignarParticion(tamanoNecesario, id);
        if (base != -1) {
            BCP bcp = crearProcesoEnParticion(instrucciones, base, id);
            if (bcp != null) {
                // Copiar el .asm a la zona de archivos del disco y registrar en indice ASM
                copiarAsmAArchivosYRegistrar(nombreArchivo, instrucciones);
                siguienteId++;
                return ResultadoCarga.exito(bcp);
            }
            particionador.liberarParticion(base, tamanoNecesario, id);
        }

        // 2. Si no cabe, va a swap
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
     * Copia las instrucciones de un .asm a la zona de archivos del disco
     * y lo registra en el indice ASM con zona PRINCIPAL.
     *
     * Si el .asm ya existe en el indice, solo actualiza su zona (no lo duplica).
     */
    private void copiarAsmAArchivosYRegistrar(String nombreArchivo,
                                              List<Instruccion> instrucciones) {
        int tamano = instrucciones.size();

        // Si ya existe en el indice ASM: solo actualizar zona a PRINCIPAL
        if (disco.existe(nombreArchivo, Disco.TIPO_ASM)) {
            int inicio = disco.getInicioArchivo(nombreArchivo, Disco.TIPO_ASM);
            int fin = disco.getFinArchivo(nombreArchivo, Disco.TIPO_ASM);
            disco.actualizarZona(nombreArchivo, Disco.TIPO_ASM,
                    Disco.ZONA_PRINCIPAL, inicio, fin);
            return;
        }

        // Reservar espacio en la zona de archivos
        int inicio = disco.reservarBloqueArchivo(tamano);
        if (inicio == -1) {
            System.out.println("[WARNING] Sin espacio en zona de archivos para "
                    + nombreArchivo + ". El .asm solo estara en RAM.");
            return;
        }

        // Copiar instrucciones a la zona de archivos
        Object[] instruccionesArr = instrucciones.toArray();
        disco.escribirBloqueArchivo(inicio, instruccionesArr);
        int fin = inicio + tamano - 1;

        // Registrar en el indice ASM con zona PRINCIPAL
        boolean ok = disco.registrarArchivoAsm(nombreArchivo, inicio, fin,
                Disco.ZONA_PRINCIPAL);
        if (!ok) {
            disco.liberarBloqueArchivo(inicio, tamano);
            System.out.println("[WARNING] No se pudo registrar " + nombreArchivo
                    + " en el indice ASM (lleno).");
            return;
        }

        System.out.println("[DISCO] .asm guardado en zona archivos: " + nombreArchivo
                + " (" + inicio + "-" + fin + ")");
    }

    /**
     * Suspende un proceso en swap y lo registra en el indice ASM del disco
     * con la zona VIRTUAL y en la ListaDeTrabajos (en RAM).
     */
    private ResultadoCarga suspenderEnSwap(List<Instruccion> instrucciones,
                                           int id,
                                           String nombreArchivo) {
        int tamanoInstrucciones = instrucciones.size();

        // 1. Verificar espacio en swap
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

        // 2. Registrar en el indice ASM del disco con zona VIRTUAL
        int finSwap = dirSwap + tamanoInstrucciones - 1;
        boolean registrado = disco.registrarArchivoAsm(
                nombreArchivo, dirSwap, finSwap, Disco.ZONA_VIRTUAL);
        if (!registrado) {
            disco.liberarBloqueSwap(dirSwap, tamanoInstrucciones);
            return ResultadoCarga.error(
                "No se pudo registrar el archivo en el indice ASM (lleno).");
        }

        // 3. Agregar a la ListaDeTrabajos (en RAM)
        boolean agregado = listaDeTrabajos.agregar(
                nombreArchivo, dirSwap, finSwap, Disco.ZONA_VIRTUAL);
        if (!agregado) {
            disco.liberarBloqueSwap(dirSwap, tamanoInstrucciones);
            disco.eliminarDelIndice(nombreArchivo, Disco.TIPO_ASM);
            return ResultadoCarga.error(
                "La ListaDeTrabajos esta llena. No se puede suspender el proceso.");
        }

        // 4. Guardar metadatos del proceso suspendido (por si se necesitan)
        ProcesoEnEspera pe = new ProcesoEnEspera(
                id, EstadoProceso.NEW, tamanoInstrucciones);
        pe.setDireccionBaseEnSwap(dirSwap);
        pe.setNombreArchivo(nombreArchivo);

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

        // 2. Si el proceso actual esta BLOCKED por INT 21H, decrementar tardanza.
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
        int maxPasos = 10000; // <- Evita ciclo infinito

        while (hayProcesosActivos() && pasos < maxPasos) {

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
        }

        // Si se alcanzo el maximo, terminar el proceso actual por seguridad
        if (pasos >= maxPasos && hayProcesosActivos()) {
            if (despachador.getEjecutorActual() != null) {
                BCP actual = despachador.getBcpActual();
                if (actual != null) {
                    actual.setEstado(EstadoProceso.EXIT);
                    actual.marcarFin();
                    procesoTerminado(actual);
                    despachador.limpiarEjecutor();
                }
            }

            String texto = "[WARNING] Ejecucion automatica detenida: "
                    + "maximo de pasos (" + maxPasos + ") alcanzado. "
                    + "Posible ciclo infinito.";

            System.out.println(texto);
            if (salidaPantalla != null) {
                salidaPantalla.accept(texto);
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
     */
    private void reactivarSiguienteProceso() {
        if (listaDeTrabajos.estaVacia()) return;

        Object[] trabajo = listaDeTrabajos.verPrimero();
        if (trabajo == null) return;

        String nombreArchivo = (String) trabajo[0];
        int inicioSwap = (Integer) trabajo[1];
        int finSwap = (Integer) trabajo[2];
        int tamanoInstrucciones = (finSwap - inicioSwap) + 1;
        int id = siguienteId;

        int base = particionador.asignarParticion(tamanoInstrucciones, id);
        if (base == -1) return;

        int direccionBase = memoria.reservarBloqueBCP();
        if (direccionBase == -1) {
            particionador.liberarParticion(base, tamanoInstrucciones, id);
            return;
        }

        // Copiar instrucciones de swap a RAM
        Object[] instruccionesArr = disco.leerBloqueSwap(inicioSwap, tamanoInstrucciones);

        List<Instruccion> instrucciones = new ArrayList<>();
        for (Object obj : instruccionesArr) {
            if (obj instanceof Instruccion) {
                instrucciones.add((Instruccion) obj);
            }
        }

        // Copiar a archivos (si hay espacio)
        int nuevoInicioArchivos = disco.reservarBloqueArchivo(tamanoInstrucciones);
        if (nuevoInicioArchivos != -1) {
            disco.escribirBloqueArchivo(nuevoInicioArchivos, instruccionesArr);
            int nuevoFinArchivos = nuevoInicioArchivos + tamanoInstrucciones - 1;

            // Actualizar el indice ASM
            if (nombreArchivo != null) {
                disco.actualizarZona(nombreArchivo, Disco.TIPO_ASM, Disco.ZONA_PRINCIPAL,
                        nuevoInicioArchivos, nuevoFinArchivos);
            }
        }

        // Liberar swap y sacar de la lista de trabajos
        disco.liberarBloqueSwap(inicioSwap, tamanoInstrucciones);
        listaDeTrabajos.sacarPrimero();

        // Crear BCP y admitir en RAM
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

    /* ================== CONSULTAS PARA LA GUI ================== */

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

    public List<Object[]> getProcesosEnEspera() {
        return listaDeTrabajos.toList();
    }

    public Disco getDisco() { return disco; }
    public ParticionadorDinamico getParticionador() { return particionador; }

    /* ================== CALLBACKS DE INTERRUPCIONES ================== */

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