package modelo;

/**
 * Representa la memoria principal de la máquina virtual.
 *
 * Se divide en dos zonas:
 *   - Zona kernel: posiciones [0, limiteKernelUsuario)
 *   - Zona usuario: posiciones [limiteKernelUsuario, tamanoMemoria)
 *
 * El límite kernel/usuario es fijo y se define al construir la memoria.
 * Cada posición almacena un valor de tipo String (instrucción o dato).
 */
public class Memoria {

    private int tamanoMemoria;         // cantidad total de posiciones
    private int limiteKernelUsuario;   // primera posición de la zona usuario
    private String[] arregloMemoria;   // contenido de cada posición

    /** Tamaño mínimo permitido para una memoria (en posiciones). */
    public static final int TAMANO_MINIMO = 128;

    /**
     * Crea una memoria con el tamaño y límite indicados.
     *
     * @param tamanoMemoria       cantidad total de posiciones (>= TAMANO_MINIMO)
     * @param limiteKernelUsuario primera posición de la zona usuario
     * @throws IllegalArgumentException si tamanoMemoria < TAMANO_MINIMO
     */
    public Memoria(int tamanoMemoria, int limiteKernelUsuario) {
        if (tamanoMemoria < TAMANO_MINIMO) {
            throw new IllegalArgumentException("Tamaño de memoria menor a " + TAMANO_MINIMO);
        }
        this.tamanoMemoria = tamanoMemoria;
        this.limiteKernelUsuario = limiteKernelUsuario;
        this.arregloMemoria = new String[tamanoMemoria];
    }

    /**
     * Escribe un valor en una posición de memoria.
     *
     * @param posicionMemoria índice donde escribir
     * @param valorMemoria    valor a almacenar (instrucción o dato)
     */
    public void escribir(int posicionMemoria, String valorMemoria) {
        arregloMemoria[posicionMemoria] = valorMemoria;
    }

    /**
     * Lee el valor almacenado en una posición de memoria.
     *
     * @param posicionMemoria índice a leer
     * @return el valor almacenado en esa posición
     */
    public String leer(int posicionMemoria) {
        return arregloMemoria[posicionMemoria];
    }

    /**
     * Indica si una posición pertenece a la zona kernel.
     *
     * @param posicion índice a evaluar
     * @return true si la posición está en la zona kernel
     */
    public boolean esZonaKernel(int posicion) {
        return posicion < limiteKernelUsuario;
    }

    /**
     * Verifica si un programa de usuario de cierto tamaño cabe
     * en el espacio disponible de la zona usuario.
     *
     * @param cantidadPosiciones posiciones que requiere el programa
     * @return true si hay espacio suficiente
     */
    public boolean cabeProgramaDeUsuario(int cantidadPosiciones) {
        return cantidadPosiciones <= getEspacioUsuarioDisponible();
    }

    /* ==================== GETTERS ==================== */

    public int getTamanoMemoria() {
        return tamanoMemoria;
    }

    public int getLimiteKernelUsuario() {
        return limiteKernelUsuario;
    }

    public String[] getArregloMemoria() {
        return arregloMemoria;
    }

    /**
     * @return cantidad de posiciones disponibles en la zona usuario
     *         (tamanoMemoria - limiteKernelUsuario)
     */
    public int getEspacioUsuarioDisponible() {
        return tamanoMemoria - limiteKernelUsuario;
    }
}