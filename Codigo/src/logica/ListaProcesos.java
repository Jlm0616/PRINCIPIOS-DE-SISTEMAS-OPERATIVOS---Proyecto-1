package logica;

import modelo.BCP;
import modelo.EstadoProceso;
import modelo.Memoria;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista de procesos: cola FCFS de BCPs que estan en RAM.
 *
 * Esta lista NO es una LinkedList en Java: es una VISTA sobre una
 * zona del kernel en la Memoria (Stallings, seccion 3.3, "Memory Tables"
 * y "Process Tables").
 *
 * La zona ListaProcesos guarda PUNTEROS (direcciones base) a los BCPs
 * que estan en estado READY. El campo cantidad indica cuantos punteros
 * hay actualmente (0 a TAMANO_LISTA_PROCESOS).
 *
 * El primer BCP (indice 0) es el que se ejecutara a continuacion (FCFS).
 *
 * El campo siguienteBCP del BCP se actualiza como informativo, para que
 * la GUI pueda visualizar el enlace en memoria.
 */
public class ListaProcesos {

    private final Memoria memoria;
    private final int inicio;
    private final int tamano;

    /** Cantidad actual de punteros guardados (0 a tamano). */
    private int cantidad;

    /**
     * Crea la ListaProcesos como vista sobre la zona de memoria.
     */
    public ListaProcesos(Memoria memoria) {
        if (memoria == null) {
            throw new IllegalArgumentException("La memoria no puede ser nula");
        }
        this.memoria = memoria;
        this.inicio = memoria.getInicioListaProcesos();
        this.tamano = memoria.getTamanoListaProcesos();
        this.cantidad = 0;
    }

    /* ==================== OPERACIONES ==================== */

    /**
     * Agrega un BCP al final de la lista (FCFS).
     */
    public void agregar(BCP bcp) {
        if (bcp == null) {
            throw new IllegalArgumentException("No se puede agregar un BCP nulo");
        }
        if (cantidad >= tamano) {
            throw new IllegalStateException(
                "ListaProcesos llena (" + tamano + " posiciones)");
        }

        bcp.setEstado(EstadoProceso.READY);
        int dir = bcp.getDireccionBase();
        memoria.escribirListaProcesos(cantidad, dir);
        cantidad++;
        actualizarEnlaces();
    }

    /**
     * Agrega un BCP al PRINCIPIO de la lista.
     */
    public void agregarAlPrincipio(BCP bcp) {
        if (bcp == null) {
            throw new IllegalArgumentException("No se puede agregar un BCP nulo");
        }
        if (cantidad >= tamano) {
            throw new IllegalStateException(
                "ListaProcesos llena (" + tamano + " posiciones)");
        }

        // Desplazar todos hacia la derecha
        for (int i = cantidad - 1; i >= 0; i--) {
            Object v = memoria.leerListaProcesos(i);
            memoria.escribirListaProcesos(i + 1, v);
        }

        bcp.setEstado(EstadoProceso.READY);
        int dir = bcp.getDireccionBase();
        memoria.escribirListaProcesos(0, dir);
        cantidad++;
        actualizarEnlaces();
    }

    /**
     * Saca y devuelve el primer BCP de la lista (FCFS).
     * Construye un BCP VISTA sobre la direccion guardada.
     */
    public BCP sacarPrimero() {
        if (cantidad == 0) return null;

        Object dir = memoria.leerListaProcesos(0);
        if (dir == null) {
            cantidad = 0;
            return null;
        }
        BCP bcp = new BCP(memoria, (Integer) dir);

        // Desplazar todos hacia la izquierda
        for (int i = 0; i < cantidad - 1; i++) {
            Object v = memoria.leerListaProcesos(i + 1);
            memoria.escribirListaProcesos(i, v);
        }
        memoria.escribirListaProcesos(cantidad - 1, null);
        cantidad--;
        actualizarEnlaces();
        return bcp;
    }

    /**
     * Saca y devuelve la DIRECCION del primer BCP de la lista (FCFS).
     * NO construye el BCP: solo devuelve la direccion.
     *
     * @return la direccion base del primer BCP, o -1 si la lista esta vacia
     */
    public int sacarPrimeraDireccion() {
        if (cantidad == 0) return -1;

        Object dir = memoria.leerListaProcesos(0);
        if (dir == null) {
            cantidad = 0;
            return -1;
        }

        // Desplazar todos hacia la izquierda
        for (int i = 0; i < cantidad - 1; i++) {
            Object v = memoria.leerListaProcesos(i + 1);
            memoria.escribirListaProcesos(i, v);
        }
        memoria.escribirListaProcesos(cantidad - 1, null);
        cantidad--;
        actualizarEnlaces();
        return (Integer) dir;
    }

    /**
     * Saca un BCP especifico de la lista (por ejemplo, cuando termina).
     */
    public boolean sacar(BCP bcp) {
        if (bcp == null) return false;
        int dir = bcp.getDireccionBase();

        for (int i = 0; i < cantidad; i++) {
            Object v = memoria.leerListaProcesos(i);
            if (v != null && ((Integer) v) == dir) {
                for (int j = i; j < cantidad - 1; j++) {
                    Object sig = memoria.leerListaProcesos(j + 1);
                    memoria.escribirListaProcesos(j, sig);
                }
                memoria.escribirListaProcesos(cantidad - 1, null);
                cantidad--;
                actualizarEnlaces();
                return true;
            }
        }
        return false;
    }

    /**
     * Devuelve (sin sacar) el primer BCP de la lista.
     */
    public BCP verPrimero() {
        if (cantidad == 0) return null;
        Object dir = memoria.leerListaProcesos(0);
        if (dir == null) return null;
        return new BCP(memoria, (Integer) dir);
    }

    /* ==================== CONSULTAS ==================== */

    public boolean estaVacia() { return cantidad == 0; }
    public int getCantidad()   { return cantidad; }

    public boolean contiene(BCP bcp) {
        if (bcp == null) return false;
        int dir = bcp.getDireccionBase();
        for (int i = 0; i < cantidad; i++) {
            Object v = memoria.leerListaProcesos(i);
            if (v != null && ((Integer) v) == dir) return true;
        }
        return false;
    }

    public List<BCP> toList() {
        List<BCP> lista = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            Object dir = memoria.leerListaProcesos(i);
            if (dir != null) {
                lista.add(new BCP(memoria, (Integer) dir));
            }
        }
        return lista;
    }

    public List<Integer> getDirecciones() {
        List<Integer> dirs = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            Object dir = memoria.leerListaProcesos(i);
            if (dir != null) dirs.add((Integer) dir);
        }
        return dirs;
    }

    /* ==================== HELPERS ==================== */

    /**
     * Actualiza el campo siguienteBCP de cada BCP para reflejar el enlace.
     */
    private void actualizarEnlaces() {
        for (int i = 0; i < cantidad; i++) {
            Object dir = memoria.leerListaProcesos(i);
            if (dir == null) continue;
            BCP actual = new BCP(memoria, (Integer) dir);

            if (i + 1 < cantidad) {
                Object sig = memoria.leerListaProcesos(i + 1);
                actual.setSiguienteBCP((sig != null) ? (Integer) sig : -1);
            } else {
                actual.setSiguienteBCP(-1);
            }
        }
    }

    /* ==================== GETTERS ==================== */

    public int getInicioMemoria() { return inicio; }
    public int getTamanoMemoria() { return tamano; }
    public Memoria getMemoria()   { return memoria; }

    @Override
    public String toString() {
        return "ListaProcesos[" + cantidad + "/" + tamano + " en RAM]";
    }
}