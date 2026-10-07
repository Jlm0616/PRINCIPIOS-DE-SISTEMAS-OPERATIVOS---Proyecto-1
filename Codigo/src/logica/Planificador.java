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

    // Crea un planificador con FCFS como estrategia por defecto.
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

    // Devuelve la estrategia activa.
    public EstrategiaPlanificacion getEstrategia() {
        return estrategia;
    }

    // Cambia la estrategia activa (no puede ser null).
    public void setEstrategia(EstrategiaPlanificacion estrategia) {
        if (estrategia == null) {
            throw new IllegalArgumentException("La estrategia no puede ser nula");
        }
        this.estrategia = estrategia;
    }

    // Devuelve el nombre legible de la estrategia activa.
    public String getNombreEstrategia() {
        return estrategia.getNombre();
    }
}