package logica;

import modelo.BCP;
import modelo.EstadoProceso;

/**
 * Lista de trabajos: cola enlazada de BCPs (FCFS).
 *
 * Es una lista enlazada real (no una List<BCP> de Java) porque así lo
 * describe el Stallings en la sección 3.3 (Figura 3.14, "Process List
 * Structures"): cada BCP apunta al siguiente usando su campo siguienteBCP.
 *
 * Esta clase SOLO gestiona la estructura. NO decide qué proceso ejecutar
 * (eso es del Planificador) ni cómo ejecutarlo (eso es del Despachador).
 */
public class ListaDeTrabajos {

    private BCP primero;   // cabeza de la lista
    private BCP ultimo;    // cola de la lista (para insertar en O(1))
    private int cantidad;

    public ListaDeTrabajos() {
        this.primero = null;
        this.ultimo = null;
        this.cantidad = 0;
    }

    /**
     * Agrega un BCP al final de la lista (FCFS).
     */
    public void agregar(BCP bcp) {
        if (bcp == null) {
            throw new IllegalArgumentException("No se puede agregar un BCP nulo");
        }
        bcp.setSiguienteBCP(null);   // por si venía de otra lista
        bcp.setEstado(EstadoProceso.READY);

        if (primero == null) {
            primero = bcp;
            ultimo = bcp;
        } else {
            ultimo.setSiguienteBCP(bcp);
            ultimo = bcp;
        }
        cantidad++;
    }

    /**
     * Saca y devuelve el primer BCP de la lista (FCFS).
     *
     * @return el primer BCP, o null si la lista está vacía
     */
    public BCP sacarPrimero() {
        if (primero == null) {
            return null;
        }
        BCP sacado = primero;
        primero = primero.getSiguienteBCP();
        sacado.setSiguienteBCP(null);
        if (primero == null) {
            ultimo = null;
        }
        cantidad--;
        return sacado;
    }

    /**
     * Devuelve (sin sacar) el primer BCP de la lista.
     */
    public BCP verPrimero() {
        return primero;
    }

    public boolean estaVacia() {
        return primero == null;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BCP getUltimo() {
        return ultimo;
    }

    /**
     * @return lista con todos los BCPs (para la GUI), sin modificar la enlazada.
     */
    public java.util.List<BCP> toList() {
        java.util.List<BCP> lista = new java.util.ArrayList<>();
        BCP actual = primero;
        while (actual != null) {
            lista.add(actual);
            actual = actual.getSiguienteBCP();
        }
        return lista;
    }
}