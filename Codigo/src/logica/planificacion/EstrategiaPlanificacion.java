package logica.planificacion;

import logica.ListaDeTrabajos;
import modelo.BCP;

/**
 * Contrato para las estrategias de planificación de CPU.
 *
 * Cada algoritmo (FCFS, SPN, SRT, RR, HRRN) implementa esta
 * interfaz para decidir cuál proceso ejecutar a continuación.
 *
 * El Planificador delega la decisión a la estrategia activa.
 */
public interface EstrategiaPlanificacion {

    /**
     * Selecciona el siguiente proceso a ejecutar según la política.
     *
     * @param listaDeTrabajos cola de procesos listos
     * @return el BCP elegido, o null si no hay procesos
     */
    BCP seleccionarSiguiente(ListaDeTrabajos listaDeTrabajos);

    /**
     * @return nombre legible de la estrategia (para la UI).
     */
    String getNombre();
}