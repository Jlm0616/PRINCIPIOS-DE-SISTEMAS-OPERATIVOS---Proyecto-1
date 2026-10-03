package logica;

import java.util.LinkedList;
import java.util.List;

/**
 * Lista de trabajos: cola FCFS de procesos que estan en DISCO
 * esperando ser cargados en RAM.
 *
 * A diferencia de ListaProcesos (que maneja BCPs en RAM), esta
 * lista maneja ProcesoEnEspera, que solo contienen metadatos:
 * id, estado (NEW), direccion en swap, tamaño.
 *
 * El BCP NO existe hasta que el proceso entra a RAM.
 */
public class ListaDeTrabajos {

    private final LinkedList<ProcesoEnEspera> lista;

    public ListaDeTrabajos() {
        this.lista = new LinkedList<>();
    }

    public void agregar(ProcesoEnEspera pe) {
        if (pe == null) {
            throw new IllegalArgumentException("No se puede agregar un ProcesoEnEspera nulo");
        }
        lista.addLast(pe);
    }

    public ProcesoEnEspera sacarPrimero() {
        if (lista.isEmpty()) {
            return null;
        }
        return lista.removeFirst();
    }

    public ProcesoEnEspera verPrimero() {
        return lista.isEmpty() ? null : lista.getFirst();
    }

    public boolean estaVacia() {
        return lista.isEmpty();
    }

    public int getCantidad() {
        return lista.size();
    }

    public List<ProcesoEnEspera> toList() {
        return new java.util.ArrayList<>(lista);
    }

    @Override
    public String toString() {
        return "ListaDeTrabajos[" + lista.size() + " procesos en disco]";
    }
}