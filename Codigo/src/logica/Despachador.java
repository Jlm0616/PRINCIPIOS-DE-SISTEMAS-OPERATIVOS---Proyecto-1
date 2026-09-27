package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;

import java.util.function.Consumer;

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
 * (sección 3.4, "Change of Process State").
 *
 * Los callbacks de interrupciones (pantalla, teclado, archivos) se
 * propagan al EjecutorCPU para que las interrupciones lleguen a la GUI.
 */
public class Despachador {

    private CPU cpu;
    private EjecutorCPU ejecutorActual;

    // Callbacks de interrupciones (configurados por la GUI)
    private Consumer<String> salidaPantalla;
    private Consumer<BCP> solicitudTeclado;
    private Consumer<BCP> solicitudArchivo;

    public Despachador(CPU cpu) {
        this.cpu = cpu;
        this.ejecutorActual = null;
    }

    /**
     * Despacha un BCP: lo carga en la CPU y prepara el ejecutor.
     * Corresponde a los pasos 5 y 7 del cambio de contexto.
     */
    public void despachar(BCP bcp, modelo.Memoria memoria) {
        if (bcp == null) {
            throw new IllegalArgumentException("No se puede despachar un BCP nulo");
        }

        // Paso 5: actualizar el BCP del proceso seleccionado
        bcp.setEstado(EstadoProceso.RUNNING);
        bcp.setCpuAsignado(0);
        bcp.marcarInicio();

        // Paso 7: restaurar el contexto del procesador
        bcp.actualizarHaciaCPU(cpu);

        // Crear un ejecutor para este proceso
        this.ejecutorActual = new EjecutorCPU(cpu, memoria, bcp);

        // Propagar callbacks de interrupciones
        ejecutorActual.setSalidaPantalla(salidaPantalla);
        ejecutorActual.setSolicitudTeclado(solicitudTeclado);
        ejecutorActual.setSolicitudArchivo(solicitudArchivo);
    }

    /**
     * Ejecuta UN SEGUNDO de CPU del proceso despachado.
     *
     * @return true si el proceso sigue vivo y no bloqueado, false si terminó o se bloqueó
     */
    public boolean ejecutarUnPaso() {
        if (ejecutorActual == null) {
            throw new IllegalStateException("No hay proceso despachado");
        }
        if (ejecutorActual.isProgramaTerminado()) {
            return false;
        }
        ejecutorActual.ejecutarSegundoDeCPU();
        return !ejecutorActual.isProgramaTerminado()
            && ejecutorActual.getBcp().getEstado() != EstadoProceso.BLOCKED;
    }

    /**
     * Guarda el contexto del proceso actual (pasos 1 y 2).
     */
    public void guardarContexto(BCP bcp) {
        bcp.actualizarDesdeCPU(cpu);
    }

    public EjecutorCPU getEjecutorActual() {
        return ejecutorActual;
    }

    public boolean procesoActualOcupaCPU() {
        if (ejecutorActual == null) {
            return false;
        }
        if (ejecutorActual.isProgramaTerminado()) {
            return false;
        }
        if (ejecutorActual.getBcp().getEstado() == EstadoProceso.BLOCKED) {
            return false;
        }
        return true;
    }

    public void limpiarEjecutor() {
        this.ejecutorActual = null;
    }

    /* ==================== CALLBACKS ==================== */

    public void setSalidaPantalla(Consumer<String> callback) {
        this.salidaPantalla = callback;
    }

    public void setSolicitudTeclado(Consumer<BCP> callback) {
        this.solicitudTeclado = callback;
    }

    public void setSolicitudArchivo(Consumer<BCP> callback) {
        this.solicitudArchivo = callback;
    }
}