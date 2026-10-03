package logica.planificacion;

import logica.ListaProcesos;
import modelo.BCP;

/**
 * Contrato para las estrategias de planificacion de CPU.
 *
 * Cada algoritmo (FCFS, SPN, SRT, RR, HRRN) implementa esta
 * interfaz para decidir cual proceso ejecutar a continuacion.
 *
 * El Planificador delega la decision a la estrategia activa.
 *
 * Trabaja sobre la ListaProcesos (procesos en RAM). La
 * ListaDeTrabajos (procesos en disco) se gestiona aparte.
 */
public interface EstrategiaPlanificacion {

    /**
     * Selecciona el siguiente proceso a ejecutar segun la politica.
     *
     * @param listaProcesos cola de procesos en RAM
     * @return el BCP elegido, o null si no hay procesos
     */
    BCP seleccionarSiguiente(ListaProcesos listaProcesos);

    /**
     * @return nombre legible de la estrategia (para la UI).
     */
    String getNombre();
}