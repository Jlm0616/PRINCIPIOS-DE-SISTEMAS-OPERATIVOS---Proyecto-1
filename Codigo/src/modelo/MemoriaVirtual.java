package modelo;

/**
 * Memoria virtual del sistema (área de swap).
 *
 * En este proyecto, la "memoria virtual" se implementa como un área de
 * swap que mueve procesos COMPLETOS entre la memoria principal y el
 * almacenamiento secundario (Stallings, sección 3.2, "Suspended Processes").
 *
 * NO se implementa paginación ni segmentación (Stallings, capítulo 8).
 * Un proceso suspendido ocupa BCP.POSICIONES_REQUERIDAS + N posiciones
 * contiguas, donde N es la cantidad de instrucciones del proceso.
 * Es decir, se guarda el process image completo:
 *   - BCP: 22 posiciones (ID, estado, registros, pila, etc.)
 *   - Instrucciones: N posiciones
 *
 * Tamaño por defecto: 64 posiciones (configurable).
 *
 * Estructuralmente análoga a {@link Memoria}, pero:
 *   - No tiene división kernel/usuario.
 *   - No gestiona BCPs ni instrucciones tipadas.
 *   - Solo es un arreglo de posiciones con gestión de bloques.
 *
 * El swap se reinicia cuando el usuario presiona "Limpiar", pero su
 * tamaño persiste como parte de la configuración del sistema.
 *
 * Analogía con el modelo de Stallings:
 *   - Memoria principal = procesos activos (READY, RUNNING, BLOCKED).
 *   - MemoriaVirtual    = procesos suspendidos (READY_SUSPEND, BLOCKED_SUSPEND).
 *   - Disco             = archivos .asm y datos.
 */
public class MemoriaVirtual {

    /** Tamaño mínimo permitido para el swap (en posiciones). */
    public static final int TAMANO_MINIMO = 64;

    /** Tamaño por defecto del swap (en posiciones). */
    public static final int TAMANO_DEFAULT = 64;

    /** Cantidad total de posiciones del swap. */
    private final int tamanoTotal;

    /** Contenido de cada posición. Puede ser null (libre). */
    private final Object[] posiciones;

    /**
     * Crea un área de swap con el tamaño indicado.
     *
     * @param tamanoTotal cantidad de posiciones (>= TAMANO_MINIMO)
     * @throws IllegalArgumentException si tamanoTotal < TAMANO_MINIMO
     */
    public MemoriaVirtual(int tamanoTotal) {
        if (tamanoTotal < TAMANO_MINIMO) {
            throw new IllegalArgumentException(
                "El tamaño de la memoria virtual debe ser al menos " + TAMANO_MINIMO);
        }
        this.tamanoTotal = tamanoTotal;
        this.posiciones = new Object[tamanoTotal];
    }

    /* ==================== ACCESO PUNTUAL ==================== */

    /**
     * Escribe un valor en una posición del swap.
     *
     * @param posicion índice donde escribir
     * @param valor    valor a almacenar (puede ser null para liberar)
     */
    public void escribir(int posicion, Object valor) {
        posiciones[posicion] = valor;
    }

    /**
     * Lee el valor almacenado en una posición del swap.
     *
     * @param posicion índice a leer
     * @return el valor almacenado, o null si está libre
     */
    public Object leer(int posicion) {
        return posiciones[posicion];
    }

    /* ==================== GESTIÓN DE BLOQUES ==================== */

    /**
     * Reserva un bloque de N posiciones consecutivas libres en el swap.
     *
     * @param tamano cantidad de posiciones consecutivas necesarias
     * @return la dirección base del bloque (primera posición), o -1 si no hay
     * @throws IllegalArgumentException si tamano <= 0
     */
    public int reservarBloque(int tamano) {
        if (tamano <= 0) {
            throw new IllegalArgumentException("El tamaño del bloque debe ser > 0");
        }
        for (int inicio = 0; inicio <= tamanoTotal - tamano; inicio++) {
            boolean libre = true;
            for (int i = 0; i < tamano; i++) {
                if (posiciones[inicio + i] != null) {
                    libre = false;
                    break;
                }
            }
            if (libre) {
                return inicio;
            }
        }
        return -1;
    }

    /**
     * Escribe un bloque completo en posiciones consecutivas.
     *
     * @param direccionBase posición inicial donde escribir
     * @param valores       valores a escribir (uno por posición)
     */
    public void escribirBloque(int direccionBase, Object[] valores) {
        for (int i = 0; i < valores.length; i++) {
            posiciones[direccionBase + i] = valores[i];
        }
    }

    /**
     * Lee un bloque completo de posiciones consecutivas.
     *
     * @param direccionBase posición inicial del bloque
     * @param tamano        cantidad de posiciones a leer
     * @return arreglo con los valores leídos
     */
    public Object[] leerBloque(int direccionBase, int tamano) {
        Object[] valores = new Object[tamano];
        for (int i = 0; i < tamano; i++) {
            valores[i] = posiciones[direccionBase + i];
        }
        return valores;
    }

    /**
     * Libera un bloque de N posiciones consecutivas.
     *
     * @param direccionBase posición inicial del bloque
     * @param tamano        cantidad de posiciones a liberar
     */
    public void liberarBloque(int direccionBase, int tamano) {
        for (int i = 0; i < tamano; i++) {
            posiciones[direccionBase + i] = null;
        }
    }

    /**
     * Vacía completamente el swap. Se usa al presionar "Limpiar".
     */
    public void limpiar() {
        for (int i = 0; i < tamanoTotal; i++) {
            posiciones[i] = null;
        }
    }

    /* ==================== CONSULTAS ==================== */

    /**
     * @return cantidad de posiciones ocupadas actualmente.
     */
    public int getEspacioOcupado() {
        int contador = 0;
        for (int i = 0; i < tamanoTotal; i++) {
            if (posiciones[i] != null) {
                contador++;
            }
        }
        return contador;
    }

    /**
     * @return cantidad de posiciones libres actualmente.
     */
    public int getEspacioLibre() {
        return tamanoTotal - getEspacioOcupado();
    }

    /**
     * @return porcentaje de uso del swap (0-100).
     */
    public int getPorcentajeUso() {
        if (tamanoTotal == 0) return 0;
        return (getEspacioOcupado() * 100) / tamanoTotal;
    }

    /**
     * @return true si no hay ninguna posición libre en el swap.
     */
    public boolean estaLlena() {
        return getEspacioLibre() == 0;
    }

    /* ==================== GETTERS ==================== */

    public int getTamanoTotal() {
        return tamanoTotal;
    }

    /**
     * @return arreglo crudo de posiciones (para la UI).
     *         No modificar directamente.
     */
    public Object[] getPosiciones() {
        return posiciones;
    }

    /**
     * Obtiene el contenido crudo de una posición del swap.
     * Valida los límites y devuelve null si la posición está fuera de rango.
     *
     * @param posicion índice dentro del swap
     * @return el contenido de esa posición, o null si está fuera de rango o vacía
     */
    public Object getContenidoEn(int posicion) {
        if (posicion < 0 || posicion >= tamanoTotal) {
            return null;
        }
        return posiciones[posicion];
    }

    @Override
    public String toString() {
        return "MemoriaVirtual[" + getEspacioOcupado() + "/" + tamanoTotal + " posiciones]";
    }
}