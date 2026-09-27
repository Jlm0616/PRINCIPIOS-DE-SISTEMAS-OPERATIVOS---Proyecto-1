package logica;

import modelo.BCP;
import modelo.EstadoProceso;

import java.util.LinkedList;

/**
 * Lista de trabajos: cola FCFS de BCPs.
 *
 * Usa una lista doblemente enlazada interna (LinkedList) para la
 * gestion O(1) en ambos extremos.
 *
 * El campo siguienteBCP del BCP se mantiene como informativo: guarda
 * la direccion del siguiente BCP en el kernel (se actualiza al agregar),
 * para que la GUI pueda visualizar el enlace.
 *
 * Esta clase SOLO gestiona la estructura. NO decide qué proceso ejecutar
 * (eso es del Planificador) ni cómo ejecutarlo (eso es del Despachador).
 */
public class ListaDeTrabajos {

    private final LinkedList<BCP> lista;

    public ListaDeTrabajos() {
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
     * Saca y devuelve el primer BCP de la lista (FCFS).
     *
     * @return el primer BCP, o null si la lista está vacía
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
     * Devuelve (sin sacar) el primer BCP de la lista.
     */
    public BCP verPrimero() {
        return lista.isEmpty() ? null : lista.getFirst();
    }

    public boolean estaVacia() {
        return lista.isEmpty();
    }

    public int getCantidad() {
        return lista.size();
    }

    public BCP getUltimo() {
        return lista.isEmpty() ? null : lista.getLast();
    }

    /**
     * @return lista con todos los BCPs (para la GUI), sin modificar la enlazada.
     */
    public java.util.List<BCP> toList() {
        return new java.util.ArrayList<>(lista);
    }

    /**
     * Actualiza el campo siguienteBCP de cada BCP de la lista
     * para que la GUI pueda visualizar el enlace en memoria.
     */
    private void actualizarEnlaces() {
        for (int i = 0; i < lista.size(); i++) {
            BCP actual = lista.get(i);
            if (i + 1 < lista.size()) {
                BCP siguiente = lista.get(i + 1);
                actual.setSiguienteBCP(siguiente.getDireccionBase());
            } else {
                actual.setSiguienteBCP(-1);   // último: sin siguiente
            }
        }
    }
}