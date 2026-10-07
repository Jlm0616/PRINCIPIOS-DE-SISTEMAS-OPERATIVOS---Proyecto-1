package logica;

import modelo.BCP;
import modelo.EstadoProceso;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Lista de procesos: cola FCFS de BCPs que estan en RAM.
 *
 * Esta lista es una estructura JAVA (LinkedList), NO una vista sobre
 * la zona kernel de la Memoria. Esto es asi porque, segun el profesor,
 * la Lista de Procesos es "solo visual" (para que el planificador FCFS
 * sepa cual ejecutar), mientras que la Lista de Trabajos SI va en RAM.
 *
 * El primer BCP de la lista es el que se ejecutara a continuacion (FCFS).
 */
public class ListaProcesos {

    private final LinkedList<BCP> lista;

    /**
     * Crea una ListaProcesos vacia.
     */
    public ListaProcesos() {
        this.lista = new LinkedList<>();
    }

    /* ==================== OPERACIONES ==================== */

    /**
     * Agrega un BCP al final de la lista (FCFS).
     */
    public void agregar(BCP bcp) {
        if (bcp == null) {
            throw new IllegalArgumentException("No se puede agregar un BCP nulo");
        }
        bcp.setEstado(EstadoProceso.READY);
        lista.addLast(bcp);
    }

    /**
     * Agrega un BCP al PRINCIPIO de la lista.
     */
    public void agregarAlPrincipio(BCP bcp) {
        if (bcp == null) {
            throw new IllegalArgumentException("No se puede agregar un BCP nulo");
        }
        bcp.setEstado(EstadoProceso.READY);
        lista.addFirst(bcp);
    }

    /**
     * Saca y devuelve el primer BCP de la lista (FCFS).
     */
    public BCP sacarPrimero() {
        if (lista.isEmpty()) return null;
        return lista.removeFirst();
    }

    /**
     * Saca y devuelve la DIRECCION del primer BCP de la lista (FCFS).
     * NO construye el BCP: solo devuelve la direccion.
     *
     * @return la direccion base del primer BCP, o -1 si la lista esta vacia
     */
    public int sacarPrimeraDireccion() {
        if (lista.isEmpty()) return -1;
        BCP bcp = lista.removeFirst();
        return bcp.getDireccionBase();
    }

    /**
     * Saca un BCP especifico de la lista (por ejemplo, cuando termina).
     */
    public boolean sacar(BCP bcp) {
        if (bcp == null) return false;
        return lista.remove(bcp);
    }

    /**
     * Devuelve (sin sacar) el primer BCP de la lista.
     */
    public BCP verPrimero() {
        if (lista.isEmpty()) return null;
        return lista.getFirst();
    }

    /* ==================== CONSULTAS ==================== */

    public boolean estaVacia() { return lista.isEmpty(); }
    public int getCantidad()   { return lista.size(); }

    public boolean contiene(BCP bcp) {
        if (bcp == null) return false;
        return lista.contains(bcp);
    }

    public List<BCP> toList() {
        return new ArrayList<>(lista);
    }

    public List<Integer> getDirecciones() {
        List<Integer> dirs = new ArrayList<>();
        for (BCP bcp : lista) {
            dirs.add(bcp.getDireccionBase());
        }
        return dirs;
    }

    @Override
    public String toString() {
        return "ListaProcesos[" + lista.size() + " en RAM]";
    }
}