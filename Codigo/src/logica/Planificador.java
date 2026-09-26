package logica;

import modelo.BCP;

/**
 * Planificador de procesos.
 *
 * Su única responsabilidad es DECIDIR cuál de los procesos en la lista
 * de trabajos debe ejecutarse a continuación.
 *
 * Implementa FCFS (First Come, First Served): simplemente el primero
 * de la lista de trabajos.
 *
 * No sabe cómo se guarda la lista (eso es de ListaDeTrabajos) ni cómo
 * se ejecuta el proceso (eso es del Despachador).
 */
public class Planificador {

    private ListaDeTrabajos listaDeTrabajos;

    public Planificador(ListaDeTrabajos listaDeTrabajos) {
        this.listaDeTrabajos = listaDeTrabajos;
    }

    /**
     * Selecciona el siguiente BCP a ejecutar según FCFS.
     *
     * @return el BCP elegido, o null si no hay procesos en la lista
     */
    public BCP seleccionarSiguiente() {
        return listaDeTrabajos.verPrimero();
    }

    /**
     * @return true si hay algún proceso listo para ejecutar.
     */
    public boolean hayProcesosPendientes() {
        return !listaDeTrabajos.estaVacia();
    }
}