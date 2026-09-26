package logica;

/**
 * Estrategia de particionamiento fijo de la zona usuario (Proyecto 1).
 *
 * Divide la zona usuario en MAX_BCPS particiones de tamaño igual,
 * calculado dinámicamente según el tamaño de memoria configurado
 *
 * Aislar esta lógica en su propia clase permite reemplazarla en el
 * futuro (ej. particionamiento dinámico o paginación) sin modificar
 * Memoria ni GestorProcesos.
 */
public class ParticionadorFijo {

    private int inicioZonaUsuario;
    private int tamanoParticion;
    private int cantidadParticiones;
    private boolean[] particionOcupada;

    public ParticionadorFijo(int inicioZonaUsuario, int espacioUsuarioDisponible, int cantidadParticiones) {
        this.inicioZonaUsuario = inicioZonaUsuario;
        this.cantidadParticiones = cantidadParticiones;
        this.tamanoParticion = espacioUsuarioDisponible / cantidadParticiones;
        this.particionOcupada = new boolean[cantidadParticiones];
    }

    /**
     * Busca una partición libre.
     *
     * @return índice de la partición asignada, o -1 si no hay ninguna libre
     */
    public int asignarParticion() {
        for (int i = 0; i < cantidadParticiones; i++) {
            if (!particionOcupada[i]) {
                particionOcupada[i] = true;
                return i;
            }
        }
        return -1;
    }

    /** Libera una partición (cuando el proceso termina). */
    public void liberarParticion(int indice) {
        particionOcupada[indice] = false;
    }

    /** @return la posición base (dirección de inicio) de una partición. */
    public int getBaseParticion(int indice) {
        return inicioZonaUsuario + (indice * tamanoParticion);
    }

    /** @return el tamaño de cada partición (alcance máximo por proceso). */
    public int getTamanoParticion() {
        return tamanoParticion;
    }

    public boolean hayParticionLibre() {
        for (boolean ocupada : particionOcupada) {
            if (!ocupada) return true;
        }
        return false;
    }
    
    /**
     * Calcula el índice de partición a partir de una dirección base.
     * Se usa para liberar la partición de un proceso que ya terminó,
     * sin tener que guardar el índice por separado en el BCP.
     *
     * @param base dirección base (la que devolvió getBaseParticion)
     * @return índice de la partición correspondiente
     */
    public int indiceDesdePosicion(int base) {
        return (base - inicioZonaUsuario) / tamanoParticion;
    }
}