package logica;

import modelo.BCP;
import modelo.EstadoProceso;

import java.util.LinkedList;
import java.util.List;

/**
 * Lista de procesos: cola FCFS de BCPs que estan en RAM.
 *
 * Incluye procesos en estado READY, RUNNING y BLOCKED.
 * El planificador elige el primero READY.
 *
 * El campo siguienteBCP del BCP se mantiene como informativo:
 * guarda la direccion del siguiente BCP en el kernel, para que la
 * GUI pueda visualizar el enlace.
 *
 * Esta clase SOLO gestiona la estructura. NO decide que proceso
 * ejecutar (eso es del Planificador) ni como ejecutarlo (Despachador).
 */
public class ListaProcesos {

    private final LinkedList<BCP> lista;

    public ListaProcesos() {
        this.lista = new LinkedList<>();
    }

    /**
     * Agrega un BCP al final de la lista (FCFS).
     */
    public void agregar(BCP bcp) {
        if (bcp == null) {
            throw new IllegalArgumentException("No se puede agregar un BCP nulo");
        }
        bcp.setEstado(EstadoProceso.READY);
        lista.addLast(bcp);
        actualizarEnlaces();
    }

    /**
     * Agrega un BCP al PRINCIPIO de la lista.
     * Se usa cuando un proceso bloqueado se desbloquea y debe
     * retomar su lugar original en FCFS puro.
     */
    public void agregarAlPrincipio(BCP bcp) {
        if (bcp == null) {
            throw new IllegalArgumentException("No se puede agregar un BCP nulo");
        }
        bcp.setEstado(EstadoProceso.READY);
        lista.addFirst(bcp);
        actualizarEnlaces();
    }

    /**
     * Saca y devuelve el primer BCP de la lista (FCFS).
     */
    public BCP sacarPrimero() {
        if (lista.isEmpty()) {
            return null;
        }
        BCP sacado = lista.removeFirst();
        actualizarEnlaces();
        return sacado;
    }

    /**
     * Saca un BCP especifico de la lista (por ejemplo, cuando termina).
     *
     * @return true si se saco
     */
    public boolean sacar(BCP bcp) {
        if (bcp == null) return false;
        boolean removido = lista.remove(bcp);
        if (removido) actualizarEnlaces();
        return removido;
    }

    public BCP verPrimero() {
        return lista.isEmpty() ? null : lista.getFirst();
    }

    public boolean estaVacia() {
        return lista.isEmpty();
    }

    public int getCantidad() {
        return lista.size();
    }

    public boolean contiene(BCP bcp) {
        return lista.contains(bcp);
    }

    public List<BCP> toList() {
        return new java.util.ArrayList<>(lista);
    }

    private void actualizarEnlaces() {
        for (int i = 0; i < lista.size(); i++) {
            BCP actual = lista.get(i);
            if (i + 1 < lista.size()) {
                BCP siguiente = lista.get(i + 1);
                actual.setSiguienteBCP(siguiente.getDireccionBase());
            } else {
                actual.setSiguienteBCP(-1);
            }
        }
    }

    @Override
    public String toString() {
        return "ListaProcesos[" + lista.size() + " procesos en RAM]";
    }
}