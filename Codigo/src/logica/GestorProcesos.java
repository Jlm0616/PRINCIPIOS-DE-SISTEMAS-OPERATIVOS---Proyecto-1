package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Instruccion;
import modelo.Memoria;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Fachada de orquestación del ciclo de vida y ejecución de procesos.
 *
 * Su única responsabilidad es COORDINAR las clases especializadas:
 *   - ListaDeTrabajos     (estructura de la cola FCFS)
 *   - Planificador        (decide quién sigue)
 *   - Despachador         (cambio de contexto + ejecución)
 *   - ParticionadorFijo   (asignación de memoria)
 *   - Ensamblador         (validación y parseo de .asm)
 *
 * NO implementa la lógica de ninguna de esas clases; solo las usa.
 *
 * Modelo de ejecución:
 *   - 1 paso de simulación = 1 segundo de CPU.
 *   - Cada instrucción tiene un peso (segundos de CPU); una instrucción
 *     de peso N tarda N pasos en completarse.
 *   - Las instrucciones son atómicas: si el peso pendiente es > 0,
 *     el proceso NO se reencola (sigue ejecutando la misma instrucción).
 *   - Cuando el peso llega a 0, el proceso se reencola al final (round-robin).
 *   - Si el proceso termina (INT 20H o error), se libera su partición.
 */
public class GestorProcesos {

    private Memoria memoria;
    private CPU cpu;
    private ListaDeTrabajos listaDeTrabajos;
    private Planificador planificador;
    private Despachador despachador;
    private ParticionadorFijo particionador;

    private int siguienteId;
    private List<File> archivosEnEspera;
    private List<BCP> procesosTerminados;

    public GestorProcesos(Memoria memoria, CPU cpu, ListaDeTrabajos listaDeTrabajos,
                          ParticionadorFijo particionador) {
        this.memoria = memoria;
        this.cpu = cpu;
        this.listaDeTrabajos = listaDeTrabajos;
        this.particionador = particionador;
        this.planificador = new Planificador(listaDeTrabajos);
        this.despachador = new Despachador(cpu);
        this.siguienteId = 1;
        this.archivosEnEspera = new ArrayList<>();
        this.procesosTerminados = new ArrayList<>();
    }

    /* ==================== CREACIÓN DE PROCESOS ==================== */

    /**
     * Intenta cargar un archivo .asm como proceso nuevo.
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

        int indice = particionador.asignarParticion();
        if (indice == -1) {
            archivosEnEspera.add(archivo);
            return ResultadoCarga.enEspera();
        }

        BCP bcp = crearProcesoEnParticion(instrucciones, indice);
        return ResultadoCarga.exito(bcp);
    }

    private BCP crearProcesoEnParticion(List<Instruccion> instrucciones, int indiceParticion) {
        int base = particionador.getBaseParticion(indiceParticion);
        int alcance = instrucciones.size();

        BCP bcp = new BCP(siguienteId++, 1, base, alcance);
        bcp.setEstado(EstadoProceso.READY);   // admitido en la cola

        memoria.registrarBCP(bcp);

        int pos = base;
        for (Instruccion instr : instrucciones) {
            memoria.escribir(pos++, instr);
        }

        listaDeTrabajos.agregar(bcp);
        return bcp;
    }

    /* ==================== EJECUCIÓN PASO A PASO ==================== */

    /**
    * Ejecuta un paso de simulación: 1 segundo de CPU del proceso actual.
    *
    * Flujo:
    *   1. Si no hay proceso despachado, seleccionar el primero de la cola
    *      y despacharlo.
    *   2. Ejecutar 1 segundo de CPU.
    *   3. Si el proceso terminó: liberar su partición y BCP.
    *   4. Si aún tiene peso pendiente (instrucción a medias): NO reencolar,
    *      sigue despachado para el próximo paso.
    *   5. Si completó la instrucción: reencolar al final (round-robin).
    *
    * @return true si se ejecutó algo, false si no hay procesos
     */
    public boolean ejecutarUnPaso() {
        // 1. Si no hay proceso despachado, elegir uno
        if (despachador.procesoActualTerminado()) {
            // Liberar el proceso anterior si terminó
            if (despachador.getEjecutorActual() != null) {
                BCP anterior = despachador.getEjecutorActual().getBcp();
                if (anterior.getEstado() == EstadoProceso.EXIT) {
                    procesoTerminado(anterior);
                }
                despachador.limpiarEjecutor();
            }

            // Elegir el siguiente
            BCP siguiente = planificador.seleccionarSiguiente();
            if (siguiente == null) {
                return false;   // no hay nada para ejecutar
            }

            listaDeTrabajos.sacarPrimero();
            despachador.despachar(siguiente, memoria);
        }

        // 2. Ejecutar 1 segundo de CPU
        boolean sigueVivo = despachador.ejecutarUnPaso();

        // 3. Evaluar resultado
        BCP actual = despachador.getEjecutorActual().getBcp();

        if (!sigueVivo) {
            // El proceso terminó el programa entero (EXIT ya seteado por EjecutorCPU)
            procesoTerminado(actual);
            despachador.limpiarEjecutor();
        } else if (actual.getPesoPendiente() > 0) {
            // Instrucción a medias: el proceso sigue RUNNING en el despachador.
            // NO se reencola para no fragmentar la instrucción (son atómicas).
            despachador.guardarContexto(actual);
            // (no se cambia el estado, sigue RUNNING)
        } else {
            // Instrucción completada: el proceso vuelve al final de la cola (round-robin).
            despachador.guardarContexto(actual);
            actual.setEstado(EstadoProceso.READY);
            actual.setCpuAsignado(-1);
            listaDeTrabajos.agregar(actual);
            despachador.limpiarEjecutor();
        }

        return true;
    }

    /**
     * Ejecuta el programa completo de forma automática.
     * @return cantidad de pasos ejecutados
     */
    public int ejecutarAutomatico() {
        int pasos = 0;
        while (hayProcesosActivos()) {
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

    /**
     * Se llama cuando un proceso termina: conserva su BCP para estadísticas,
     * y libera su partición y su entrada en la zona kernel.
     *
     * El BCP se conserva en {@link #procesosTerminados} siguiendo el modelo
     * descrito por Stallings (sección 3.2): la información del proceso se
     * preserva temporalmente para que programas auxiliares (estadísticas,
     * contabilidad) extraigan los datos que necesiten.
     */
    public void procesoTerminado(BCP bcp) {
        this.procesosTerminados.add(bcp);
        
        int indice = particionador.indiceDesdePosicion(bcp.getBase());
        particionador.liberarParticion(indice);
        memoria.liberarBCP(bcp.getDireccion());

        bcp.setEstado(EstadoProceso.EXIT);
        bcp.marcarFin();
        bcp.setCpuAsignado(-1);

        // Intentar cargar el siguiente archivo en espera
        if (!archivosEnEspera.isEmpty()) {
            File siguiente = archivosEnEspera.remove(0);
            cargarPrograma(siguiente);
        }
    }

    /* ==================== CONSULTAS PARA LA GUI ==================== */

    /** @return true si hay procesos en la cola o en ejecución. */
    public boolean hayProcesosActivos() {
        return !listaDeTrabajos.estaVacia() || !despachador.procesoActualTerminado();
    }

    /** @return el BCP del proceso en ejecución, o null si no hay ninguno. */
    public BCP getProcesoActual() {
        if (despachador.getEjecutorActual() == null) {
            return null;
        }
        return despachador.getEjecutorActual().getBcp();
    }

    /** @return la lista de trabajos (para mostrar en la GUI). */
    public ListaDeTrabajos getListaDeTrabajos() {
        return listaDeTrabajos;
    }

    /** @return cantidad de archivos esperando espacio en memoria. */
    public int getCantidadEnEspera() {
        return archivosEnEspera.size();
    }
    
    /**
    * @return lista de procesos que ya terminaron (EXIT o error fatal),
    *         conservados para estadísticas.
    */
   public List<BCP> getProcesosTerminados() {
       return procesosTerminados;
   }
}