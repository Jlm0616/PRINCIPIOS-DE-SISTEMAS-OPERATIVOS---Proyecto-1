package logica;

import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia de particionamiento DINAMICO de la zona usuario.
 *
 * A diferencia del particionamiento fijo, aca las particiones se
 * crean dinamicamente segun el tamaño del proceso. Se usa el
 * algoritmo FIRST-FIT (Stallings, seccion 7.2): se busca el primer
 * hueco libre donde quepa el proceso.
 *
 * Cuando un proceso termina, su espacio se libera y se fusiona con
 * los huecos adyacentes (coalescing) para evitar fragmentacion.
 *
 * Ventajas sobre el fijo:
 *   - No hay desperdicio interno (el proceso ocupa exactamente lo que necesita).
 *   - Se pueden cargar mas procesos si son pequeños.
 *
 * Desventajas:
 *   - Fragmentacion externa: pueden quedar huecos que no sirven
 *     para procesos grandes.
 *
 * Aislar esta logica permite reemplazarla por otra estrategia
 * (best-fit, worst-fit, paginacion) sin modificar GestorProcesos.
 */
public class ParticionadorDinamico {

    /** Inicio de la zona usuario (primera posicion). */
    private final int inicioZonaUsuario;

    /** Fin de la zona usuario (exclusivo). */
    private final int finZonaUsuario;

    /** Lista de huecos libres, ordenados por direccion. */
    private final List<BloqueLibre> bloquesLibres;

    /**
     * Crea un particionador dinamico con toda la zona usuario libre.
     *
     * @param inicioZonaUsuario   primera posicion de la zona usuario
     * @param espacioUsuarioDisponible cantidad de posiciones disponibles
     */
    public ParticionadorDinamico(int inicioZonaUsuario, int espacioUsuarioDisponible) {
        if (espacioUsuarioDisponible <= 0) {
            throw new IllegalArgumentException(
                "El espacio de usuario debe ser mayor a 0");
        }
        this.inicioZonaUsuario = inicioZonaUsuario;
        this.finZonaUsuario = inicioZonaUsuario + espacioUsuarioDisponible;
        this.bloquesLibres = new ArrayList<>();
        this.bloquesLibres.add(new BloqueLibre(inicioZonaUsuario, espacioUsuarioDisponible));
    }

    /* ==================== ASIGNACION ==================== */

    /**
     * Asigna un bloque de `tamano` posiciones usando FIRST-FIT.
     *
     * Busca el primer hueco libre donde quepa el proceso. Si lo
     * encuentra, lo reduce (o lo elimina si queda exacto).
     *
     * @param tamano cantidad de posiciones necesarias
     * @return la direccion base del bloque asignado, o -1 si no hay hueco
     */
    public int asignarParticion(int tamano) {
        if (tamano <= 0) return -1;

        for (int i = 0; i < bloquesLibres.size(); i++) {
            BloqueLibre bloque = bloquesLibres.get(i);
            if (bloque.tamano >= tamano) {
                int base = bloque.inicio;

                if (bloque.tamano == tamano) {
                    // El hueco queda exacto: eliminar
                    bloquesLibres.remove(i);
                } else {
                    // Reducir el hueco
                    bloque.inicio += tamano;
                    bloque.tamano -= tamano;
                }
                return base;
            }
        }
        return -1;   // no hay hueco
    }

    /* ==================== LIBERACION ==================== */

    /**
     * Libera el bloque del proceso y fusiona con huecos adyacentes.
     *
     * @param base   direccion base del bloque a liberar
     * @param tamano cantidad de posiciones a liberar
     */
    public void liberarParticion(int base, int tamano) {
        if (tamano <= 0) return;

        bloquesLibres.add(new BloqueLibre(base, tamano));
        fusionarBloques();
    }

    /**
     * Fusiona bloques libres adyacentes para evitar fragmentacion.
     */
    private void fusionarBloques() {
        // Ordenar por direccion
        bloquesLibres.sort((a, b) -> Integer.compare(a.inicio, b.inicio));

        // Fusionar adyacentes
        int i = 0;
        while (i < bloquesLibres.size() - 1) {
            BloqueLibre actual = bloquesLibres.get(i);
            BloqueLibre siguiente = bloquesLibres.get(i + 1);

            if (actual.inicio + actual.tamano == siguiente.inicio) {
                actual.tamano += siguiente.tamano;
                bloquesLibres.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    /* ==================== CONSULTAS ==================== */

    /**
     * @return total de posiciones libres en la zona usuario.
     */
    public int getEspacioLibre() {
        int total = 0;
        for (BloqueLibre b : bloquesLibres) {
            total += b.tamano;
        }
        return total;
    }

    /**
     * @return la cantidad de huecos libres.
     */
    public int getCantidadHuecos() {
        return bloquesLibres.size();
    }

    /**
     * @return el tamaño del hueco libre mas grande.
     */
    public int getHuecoMasGrande() {
        int max = 0;
        for (BloqueLibre b : bloquesLibres) {
            if (b.tamano > max) max = b.tamano;
        }
        return max;
    }

    /**
     * Indica si hay espacio para un proceso de `tamano` posiciones.
     */
    public boolean hayEspacioPara(int tamano) {
        for (BloqueLibre b : bloquesLibres) {
            if (b.tamano >= tamano) return true;
        }
        return false;
    }

    /* ==================== GETTERS ==================== */

    public int getInicioZonaUsuario() {
        return inicioZonaUsuario;
    }

    public int getFinZonaUsuario() {
        return finZonaUsuario;
    }

    public int getEspacioTotal() {
        return finZonaUsuario - inicioZonaUsuario;
    }

    /* ==================== CLASE INTERNA ==================== */

    /**
     * Representa un hueco libre en la zona usuario.
     */
    private static class BloqueLibre {
        int inicio;
        int tamano;

        BloqueLibre(int inicio, int tamano) {
            this.inicio = inicio;
            this.tamano = tamano;
        }
    }

    @Override
    public String toString() {
        return "ParticionadorDinamico[" + bloquesLibres.size() + " huecos, "
                + getEspacioLibre() + "/" + getEspacioTotal() + " libre]";
    }
}