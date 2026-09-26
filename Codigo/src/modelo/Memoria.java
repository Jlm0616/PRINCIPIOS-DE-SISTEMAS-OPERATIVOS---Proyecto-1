package modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa la memoria principal de la máquina virtual (Proyecto 1).
 *
 * Se divide en dos zonas:
 *   - Zona kernel: posiciones [0, limiteKernelUsuario)
 *   - Zona usuario: posiciones [limiteKernelUsuario, tamanoMemoria)
 *
 * Cada posición almacena un Object, ya que la memoria no distingue tipos:
 * puede contener una Instruccion, un BCP, un dato numérico, o null.
 *
 * Las primeras MAX_BCPS posiciones del kernel están reservadas para
 * almacenar los BCPs de los procesos (hasta 5 según el enunciado).
 */
public class Memoria {

    /** Tamaño mínimo permitido para una memoria (en posiciones). */
    public static final int TAMANO_MINIMO = 128;

    /** Cantidad máxima de BCPs (5 procesos según el enunciado). */
    public static final int MAX_BCPS = 5;                              // ← NUEVO

    /** Primera posición del kernel reservada para BCPs. */
    private static final int INICIO_ZONA_BCPS = 0;                     // ← NUEVO

    private int tamanoMemoria;         // cantidad total de posiciones
    private int limiteKernelUsuario;   // primera posición de la zona usuario
    private Object[] arregloMemoria;   // contenido de cada posición

    /**
     * Crea una memoria con el tamaño y límite indicados.
     *
     * @param tamanoMemoria       cantidad total de posiciones (>= TAMANO_MINIMO)
     * @param limiteKernelUsuario primera posición de la zona usuario
     * @throws IllegalArgumentException si tamanoMemoria < TAMANO_MINIMO
     *                                  o si el límite es inválido
     */
    public Memoria(int tamanoMemoria, int limiteKernelUsuario) {
        if (tamanoMemoria < TAMANO_MINIMO) {
            throw new IllegalArgumentException("Tamaño de memoria menor a " + TAMANO_MINIMO);
        }
        if (limiteKernelUsuario > tamanoMemoria) {
            throw new IllegalArgumentException(
                "El límite kernel/usuario no puede superar el tamaño total");
        }
        if (limiteKernelUsuario < MAX_BCPS) {
            throw new IllegalArgumentException(
                "La zona kernel debe tener al menos " + MAX_BCPS
                + " posiciones para almacenar BCPs");
        }
        this.tamanoMemoria = tamanoMemoria;
        this.limiteKernelUsuario = limiteKernelUsuario;
        this.arregloMemoria = new Object[tamanoMemoria];
    }

    /* ==================== ESCRITURA / LECTURA GENÉRICA ==================== */

    /**
     * Escribe un valor en una posición de memoria.
     *
     * @param posicionMemoria índice donde escribir
     * @param valor           valor a almacenar (Instruccion, BCP, u otro Object)
     */
    public void escribir(int posicionMemoria, Object valor) {
        arregloMemoria[posicionMemoria] = valor;
    }

    /**
     * Lee el valor almacenado en una posición de memoria.
     *
     * @param posicionMemoria índice a leer
     * @return el valor almacenado en esa posición (requiere casting)
     */
    public Object leer(int posicionMemoria) {
        return arregloMemoria[posicionMemoria];
    }

    /* ==================== LECTURA TIPADA ==================== */   // ← NUEVO

    /**
     * Lee una posición como Instruccion (con casting seguro).
     *
     * @param posicion índice a leer
     * @return la Instruccion en esa posición, o null si está vacía
     * @throws ClassCastException si la posición no contiene una Instruccion
     */
    public Instruccion leerInstruccion(int posicion) {
        Object valor = arregloMemoria[posicion];
        if (valor == null) {
            return null;
        }
        if (!(valor instanceof Instruccion)) {
            throw new ClassCastException(
                "La posición " + posicion + " no contiene una Instruccion, sino "
                + valor.getClass().getSimpleName());
        }
        return (Instruccion) valor;
    }

    /**
     * Lee una posición como BCP (con casting seguro).
     *
     * @param posicion índice a leer
     * @return el BCP en esa posición, o null si está vacía
     * @throws ClassCastException si la posición no contiene un BCP
     */
    public BCP leerBCP(int posicion) {
        Object valor = arregloMemoria[posicion];
        if (valor == null) {
            return null;
        }
        if (!(valor instanceof BCP)) {
            throw new ClassCastException(
                "La posición " + posicion + " no contiene un BCP, sino "
                + valor.getClass().getSimpleName());
        }
        return (BCP) valor;
    }

    /* ==================== GESTIÓN DE BCPs ==================== */   // ← NUEVO

    /**
     * Registra un BCP en la primera posición libre de la zona de BCPs.
     * Le asigna su dirección al BCP y lo guarda en memoria.
     *
     * @param bcp BCP a registrar
     * @return la dirección asignada
     * @throws IllegalStateException si ya hay MAX_BCPS BCPs registrados
     */
    public int registrarBCP(BCP bcp) {
        for (int i = 0; i < MAX_BCPS; i++) {
            int pos = INICIO_ZONA_BCPS + i;
            if (arregloMemoria[pos] == null) {
                arregloMemoria[pos] = bcp;
                bcp.setDireccion(pos);
                return pos;
            }
        }
        throw new IllegalStateException(
            "No se pueden registrar más de " + MAX_BCPS + " procesos");
    }

    /**
     * @return cantidad actual de BCPs registrados.
     */
    public int getCantidadBCPs() {
        int contador = 0;
        for (int i = 0; i < MAX_BCPS; i++) {
            if (arregloMemoria[INICIO_ZONA_BCPS + i] instanceof BCP) {
                contador++;
            }
        }
        return contador;
    }

    /**
     * @return lista de BCPs registrados actualmente, en orden de dirección.
     */
    public List<BCP> getBCPsRegistrados() {
        List<BCP> lista = new ArrayList<>();
        for (int i = 0; i < MAX_BCPS; i++) {
            Object obj = arregloMemoria[INICIO_ZONA_BCPS + i];
            if (obj instanceof BCP) {
                lista.add((BCP) obj);
            }
        }
        return lista;
    }

    /**
     * Libera la posición de un BCP (por ejemplo, cuando un proceso termina).
     *
     * @param direccionBCP dirección del BCP a liberar
     */
    public void liberarBCP(int direccionBCP) {
        if (direccionBCP >= INICIO_ZONA_BCPS
                && direccionBCP < INICIO_ZONA_BCPS + MAX_BCPS) {
            arregloMemoria[direccionBCP] = null;
        }
    }

    /* ==================== ZONAS ==================== */

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

    public Object[] getArregloMemoria() {
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