package logica;

import logica.planificacion.EstrategiaPlanificacion;
import logica.planificacion.PlanificadorFCFS;
import modelo.BCP;

/**
 * Planificador de procesos.
 *
 * Delega la decisión a una {@link EstrategiaPlanificacion} concreta.
 * Por defecto usa FCFS. Se puede cambiar en runtime con
 * {@link #setEstrategia(EstrategiaPlanificacion)}.
 */
public class Planificador {

    private EstrategiaPlanificacion estrategia;
    private ListaDeTrabajos listaDeTrabajos;

    public Planificador(ListaDeTrabajos listaDeTrabajos) {
        this.listaDeTrabajos = listaDeTrabajos;
        this.estrategia = new PlanificadorFCFS();   // default
    }

    /**
     * Selecciona el siguiente BCP a ejecutar según la estrategia activa.
     */
    public BCP seleccionarSiguiente() {
        return estrategia.seleccionarSiguiente(listaDeTrabajos);
    }

    /**
     * @return true si hay algún proceso listo para ejecutar.
     */
    public boolean hayProcesosPendientes() {
        return !listaDeTrabajos.estaVacia();
    }

    public EstrategiaPlanificacion getEstrategia() {
        return estrategia;
    }

    public void setEstrategia(EstrategiaPlanificacion estrategia) {
        if (estrategia == null) {
            throw new IllegalArgumentException("La estrategia no puede ser nula");
        }
        this.estrategia = estrategia;
    }

    public String getNombreEstrategia() {
        return estrategia.getNombre();
    }
}