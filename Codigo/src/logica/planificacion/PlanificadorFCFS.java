package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;

/**
 * Planificador FCFS (First Come, First Served).
 *
 * Política: el primer proceso en llegar es el primero en ejecutarse.
 * No apropiativo: un proceso se ejecuta hasta terminar o bloquearse.
 *
 * Trabaja sobre la ListaProcesos (procesos en RAM).
 *
 * Para FCFS puro:
 *   - Si un proceso se bloquea por INT 09H, la CPU queda idle
 *     hasta que se desbloquee (ver GestorProcesos.ejecutarUnPaso).
 *   - El siguiente proceso solo se ejecuta cuando el actual termina.
 */
public class PlanificadorFCFS implements EstrategiaPlanificacion {

    @Override
    public BCP seleccionarSiguiente(ListaProcesos listaProcesos) {
        return listaProcesos.verPrimero();
    }

    @Override
    public String getNombre() {
        return "FCFS";
    }
}