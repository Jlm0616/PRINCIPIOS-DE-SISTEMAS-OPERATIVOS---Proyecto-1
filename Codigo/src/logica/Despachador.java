package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;

/**
 * Despachador de procesos.
 *
 * Su única responsabilidad es EJECUTAR la decisión del planificador:
 * carga el BCP elegido en la CPU, ejecuta UN SEGUNDO de CPU, y
 * actualiza el estado del proceso.
 *
 * Modela el peso de las instrucciones: una instrucción de peso N
 * requiere N llamadas a ejecutarUnPaso() para completarse.
 *
 * Implementa los pasos del cambio de contexto descritos por el Stallings
 * (sección 3.4, "Change of Process State"):
 *   1. Save the context of the processor.
 *   2. Update the PCB of the process currently in the Running state.
 *   3. Move the PCB of this process to the appropriate queue.
 *   4. Select another process for execution.
 *   5. Update the PCB of the process selected.
 *   6. Update memory management data structures.
 *   7. Restore the context of the processor.
 *
 * No sabe cómo se guarda la lista (eso es de ListaDeTrabajos) ni cómo se
 * decide quién sigue (eso es del Planificador).
 */
public class Despachador {

    private CPU cpu;
    private EjecutorCPU ejecutorActual;

    public Despachador(CPU cpu) {
        this.cpu = cpu;
        this.ejecutorActual = null;
    }

    /**
     * Despacha un BCP: lo carga en la CPU y prepara el ejecutor.
     * Corresponde a los pasos 5 y 7 del cambio de contexto.
     *
     * @param bcp BCP a despachar
     * @param memoria memoria donde vive el programa del proceso
     */
    public void despachar(BCP bcp, modelo.Memoria memoria) {
        if (bcp == null) {
            throw new IllegalArgumentException("No se puede despachar un BCP nulo");
        }

        // Paso 5: actualizar el BCP del proceso seleccionado
        bcp.setEstado(EstadoProceso.RUNNING);
        bcp.setCpuAsignado(0);   // 1 CPU, id = 0
        bcp.marcarInicio();      // registra tiempo de inicio (idempotente)

        // Paso 7: restaurar el contexto del procesador
        bcp.actualizarHaciaCPU(cpu);

        // Crear un ejecutor para este proceso
        this.ejecutorActual = new EjecutorCPU(cpu, memoria, bcp);
    }

    /**
     * Ejecuta UN SEGUNDO de CPU del proceso despachado.
     *
     * Modela el peso de las instrucciones: si la instrucción actual
     * pesa N, se necesitan N llamadas a este método para completarla.
     *
     * @return true si el proceso sigue vivo, false si terminó
     */
    public boolean ejecutarUnPaso() {
        if (ejecutorActual == null) {
            throw new IllegalStateException("No hay proceso despachado");
        }
        if (ejecutorActual.isProgramaTerminado()) {
            return false;
        }
        ejecutorActual.ejecutarSegundoDeCPU();
        return !ejecutorActual.isProgramaTerminado();
    }
    
    /**
     * Guarda el contexto del proceso actual (pasos 1 y 2).
     * Se llama antes de cambiarlo por otro.
     */
    public void guardarContexto(BCP bcp) {
        bcp.actualizarDesdeCPU(cpu);
        // El estado lo decide quien llama (READY, BLOCKED, EXIT)
    }

    /** @return el ejecutor del proceso actual, o null si no hay ninguno. */
    public EjecutorCPU getEjecutorActual() {
        return ejecutorActual;
    }

    /** @return true si el proceso actual ya terminó. */
    public boolean procesoActualTerminado() {
        return ejecutorActual == null || ejecutorActual.isProgramaTerminado();
    }
    
    /**
     * Limpia el ejecutor actual. Se llama cuando el gestor terminó
     * de procesar el BCP (sea porque terminó o porque vuelve a la cola).
     */
    public void limpiarEjecutor() {
        this.ejecutorActual = null;
    }
}