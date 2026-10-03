package logica;

import logica.planificacion.EstrategiaPlanificacion;
import logica.planificacion.PlanificadorFCFS;
import modelo.BCP;

/**
 * Planificador de procesos.
 *
 * Delega la decision a una {@link EstrategiaPlanificacion} concreta.
 * Por defecto usa FCFS. Se puede cambiar en runtime con
 * {@link #setEstrategia(EstrategiaPlanificacion)}.
 *
 * Trabaja sobre la ListaProcesos (procesos en RAM). La ListaDeTrabajos
 * (procesos en disco) se gestiona desde GestorProcesos.
 */
public class Planificador {

    private EstrategiaPlanificacion estrategia;
    private ListaProcesos listaProcesos;

    public Planificador(ListaProcesos listaProcesos) {
        this.listaProcesos = listaProcesos;
        this.estrategia = new PlanificadorFCFS();   // default
    }

    /**
     * Selecciona el siguiente BCP a ejecutar segun la estrategia activa.
     */
    public BCP seleccionarSiguiente() {
        return estrategia.seleccionarSiguiente(listaProcesos);
    }

    /**
     * @return true si hay algun proceso listo para ejecutar.
     */
    public boolean hayProcesosPendientes() {
        return !listaProcesos.estaVacia();
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