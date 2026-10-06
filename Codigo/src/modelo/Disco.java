package modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Disco simulado (almacenamiento secundario).
 *
 * El disco se divide en TRES zonas contiguas:
 *
 *   ┌─────────────────────────────────────────────────────┐
 *   │  ZONA 1: Índice de archivos                         │
 *   │  ├── maxArchivos configurable (default 10)          │
 *   │  ├── 4 posiciones por archivo:                      │
 *   │  │     [nombre] [inicio] [fin] [zona]               │
 *   │  │     zona = "PRINCIPAL" (archivos) o "VIRTUAL"    │
 *   │  │              (swap)                              │
 *   │  └── Total = maxArchivos * 4                        │
 *   ├─────────────────────────────────────────────────────┤
 *   │  ZONA 2: Memoria virtual (swap)                     │
 *   │  ├── tamaño configurable (default 64)               │
 *   │  └── guarda instrucciones de procesos NEW           │
 *   ├─────────────────────────────────────────────────────┤
 *   │  ZONA 3: Archivos                                   │
 *   │  └── contenido de los .asm y otros archivos         │
 *   └─────────────────────────────────────────────────────┘
 *
 * Analogía con el modelo de Stallings:
 *   - Memoria principal = procesos activos (READY, RUNNING, BLOCKED).
 *   - Disco             = índice + swap + archivos.
 *
 * La zona "zona" del índice indica dónde está el contenido del archivo:
 *   - "PRINCIPAL" → en la zona de archivos (94-511).
 *   - "VIRTUAL"   → en la zona de swap (30-93).
 */
public class Disco {

    /** Tamaño mínimo del disco (en posiciones). */
    public static final int TAMANO_MINIMO = 128;

    /** Cantidad máxima de archivos en el índice (configurable). */
    public static final int MAX_ARCHIVOS_DEFAULT = 10;

    /** Cantidad mínima de archivos en el índice. */
    public static final int MAX_ARCHIVOS_MINIMO = 1;

    /** Posiciones por entrada del índice (nombre, inicio, fin, zona). */
    public static final int POSICIONES_POR_ENTRADA_INDICE = 4;

    /** Zona del índice: archivo en zona de archivos. */
    public static final String ZONA_PRINCIPAL = "PRINCIPAL";

    /** Zona del índice: archivo en zona de swap. */
    public static final String ZONA_VIRTUAL = "VIRTUAL";

    /** Tamaño mínimo de la memoria virtual (swap). */
    public static final int TAMANO_SWAP_MINIMO = 16;

    /** Tamaño por defecto de la memoria virtual (swap). */
    public static final int TAMANO_SWAP_DEFAULT = 64;

    /** Posiciones mínimas reservadas para archivos. */
    public static final int ESPACIO_ARCHIVOS_MINIMO = 64;

    /* ==================== CONFIGURACIÓN ==================== */

    private final int tamanoTotal;
    private final int maxArchivos;
    private final int tamanoSwap;

    /* ==================== ZONAS (calculadas) ==================== */

    private final int inicioIndice;      // 0
    private final int inicioSwap;        // maxArchivos * 4
    private final int inicioArchivos;    // maxArchivos * 4 + tamanoSwap

    /* ==================== CONTENIDO ==================== */

    /** Contenido de cada posición del disco. */
    private final Object[] posiciones;

    /* ==================== CONSTRUCTOR ==================== */

    /**
     * Crea un disco con la configuración indicada.
     *
     * @param tamanoTotal  tamaño total del disco (>= TAMANO_MINIMO)
     * @param maxArchivos  cantidad máxima de archivos en el índice
     *                     (>= MAX_ARCHIVOS_MINIMO)
     * @param tamanoSwap   tamaño de la memoria virtual dentro del disco
     *                     (>= TAMANO_SWAP_MINIMO)
     * @throws IllegalArgumentException si la configuración es inválida
     */
    public Disco(int tamanoTotal, int maxArchivos, int tamanoSwap) {
        if (tamanoTotal < TAMANO_MINIMO) {
            throw new IllegalArgumentException(
                "El tamaño del disco debe ser al menos " + TAMANO_MINIMO);
        }
        if (maxArchivos < MAX_ARCHIVOS_MINIMO) {
            throw new IllegalArgumentException(
                "Debe haber al menos " + MAX_ARCHIVOS_MINIMO + " archivo en el índice");
        }
        if (tamanoSwap < TAMANO_SWAP_MINIMO) {
            throw new IllegalArgumentException(
                "La memoria virtual debe ser al menos " + TAMANO_SWAP_MINIMO);
        }

        int espacioIndice = maxArchivos * POSICIONES_POR_ENTRADA_INDICE;
        int espacioOcupado = espacioIndice + tamanoSwap;
        int espacioArchivos = tamanoTotal - espacioOcupado;

        if (espacioArchivos < ESPACIO_ARCHIVOS_MINIMO) {
            throw new IllegalArgumentException(
                "El disco es muy pequeño: índice (" + espacioIndice
                + ") + swap (" + tamanoSwap + ") dejan solo "
                + espacioArchivos + " posiciones para archivos (mínimo "
                + ESPACIO_ARCHIVOS_MINIMO + ")");
        }

        this.tamanoTotal = tamanoTotal;
        this.maxArchivos = maxArchivos;
        this.tamanoSwap = tamanoSwap;

        this.inicioIndice = 0;
        this.inicioSwap = espacioIndice;
        this.inicioArchivos = espacioIndice + tamanoSwap;

        this.posiciones = new Object[tamanoTotal];
    }

    /* ==================== ACCESO PUNTUAL ==================== */

    /**
     * Lee el contenido de una posición del disco.
     */
    public Object leer(int posicion) {
        return posiciones[posicion];
    }

    /**
     * Escribe un valor en una posición del disco.
     */
    public void escribir(int posicion, Object valor) {
        posiciones[posicion] = valor;
    }

    /* ==================== ZONA 1: ÍNDICE ==================== */

    /**
     * Registra un archivo en el índice (zona PRINCIPAL por defecto).
     *
     * @param nombre nombre del archivo
     * @param inicio posición donde empieza el contenido
     * @param fin    posición donde termina (inclusive)
     * @return true si se registró, false si el índice está lleno
     */
    public boolean registrarArchivo(String nombre, int inicio, int fin) {
        return registrarArchivo(nombre, inicio, fin, ZONA_PRINCIPAL);
    }

    /**
     * Registra un archivo en el índice con la zona indicada.
     *
     * @param nombre nombre del archivo
     * @param inicio posición donde empieza el contenido
     * @param fin    posición donde termina (inclusive)
     * @param zona   ZONA_PRINCIPAL (archivos) o ZONA_VIRTUAL (swap)
     * @return true si se registró, false si el índice está lleno
     */
    public boolean registrarArchivo(String nombre, int inicio, int fin, String zona) {
        if (nombre == null || nombre.isEmpty()) return false;
        if (getCantidadArchivos() >= maxArchivos) return false;

        int entrada = buscarEntradaLibre();
        if (entrada == -1) return false;

        posiciones[entrada]     = nombre;
        posiciones[entrada + 1] = inicio;
        posiciones[entrada + 2] = fin;
        posiciones[entrada + 3] = zona;
        return true;
    }

    /**
     * Elimina un archivo del índice.
     *
     * @param nombre nombre del archivo
     * @return true si se eliminó
     */
    public boolean eliminarDelIndice(String nombre) {
        int entrada = buscarEntradaPorNombre(nombre);
        if (entrada == -1) return false;

        posiciones[entrada]     = null;
        posiciones[entrada + 1] = null;
        posiciones[entrada + 2] = null;
        posiciones[entrada + 3] = null;
        return true;
    }

    /**
     * Actualiza la zona de un archivo existente en el índice.
     *
     * @param nombre nombre del archivo
     * @param nuevaZona ZONA_PRINCIPAL o ZONA_VIRTUAL
     * @param nuevoInicio nueva posición de inicio
     * @param nuevoFin nueva posición de fin
     * @return true si se actualizó
     */
    public boolean actualizarZona(String nombre, String nuevaZona, int nuevoInicio, int nuevoFin) {
        int entrada = buscarEntradaPorNombre(nombre);
        if (entrada == -1) return false;

        posiciones[entrada + 1] = nuevoInicio;
        posiciones[entrada + 2] = nuevoFin;
        posiciones[entrada + 3] = nuevaZona;
        return true;
    }

    /**
     * Verifica si un archivo existe en el índice.
     */
    public boolean existe(String nombre) {
        return buscarEntradaPorNombre(nombre) != -1;
    }

    /**
     * @return la posición de inicio del contenido del archivo, o -1 si no existe.
     */
    public int getInicioArchivo(String nombre) {
        int entrada = buscarEntradaPorNombre(nombre);
        if (entrada == -1) return -1;
        Integer inicio = (Integer) posiciones[entrada + 1];
        return (inicio != null) ? inicio : -1;
    }

    /**
     * @return la posición de fin del contenido del archivo, o -1 si no existe.
     */
    public int getFinArchivo(String nombre) {
        int entrada = buscarEntradaPorNombre(nombre);
        if (entrada == -1) return -1;
        Integer fin = (Integer) posiciones[entrada + 2];
        return (fin != null) ? fin : -1;
    }

    /**
     * @return la zona del archivo (ZONA_PRINCIPAL o ZONA_VIRTUAL), o null si no existe.
     */
    public String getZonaArchivo(String nombre) {
        int entrada = buscarEntradaPorNombre(nombre);
        if (entrada == -1) return null;
        return (String) posiciones[entrada + 3];
    }

    /**
     * @return la cantidad de archivos actualmente registrados.
     */
    public int getCantidadArchivos() {
        int contador = 0;
        for (int i = 0; i < maxArchivos; i++) {
            int entrada = inicioIndice + i * POSICIONES_POR_ENTRADA_INDICE;
            if (posiciones[entrada] != null) {
                contador++;
            }
        }
        return contador;
    }

    /**
     * @return lista de todas las entradas del índice (nombre, inicio, fin, zona).
     */
    public List<String[]> getIndice() {
        List<String[]> lista = new ArrayList<>();
        for (int i = 0; i < maxArchivos; i++) {
            int entrada = inicioIndice + i * POSICIONES_POR_ENTRADA_INDICE;
            Object nombre = posiciones[entrada];
            if (nombre != null) {
                Object inicio = posiciones[entrada + 1];
                Object fin = posiciones[entrada + 2];
                Object zona = posiciones[entrada + 3];
                lista.add(new String[]{
                    nombre.toString(),
                    inicio != null ? inicio.toString() : "-",
                    fin != null ? fin.toString() : "-",
                    zona != null ? zona.toString() : "-"
                });
            }
        }
        return lista;
    }

    private int buscarEntradaLibre() {
        for (int i = 0; i < maxArchivos; i++) {
            int entrada = inicioIndice + i * POSICIONES_POR_ENTRADA_INDICE;
            if (posiciones[entrada] == null) {
                return entrada;
            }
        }
        return -1;
    }

    private int buscarEntradaPorNombre(String nombre) {
        if (nombre == null) return -1;
        for (int i = 0; i < maxArchivos; i++) {
            int entrada = inicioIndice + i * POSICIONES_POR_ENTRADA_INDICE;
            Object n = posiciones[entrada];
            if (n != null && n.equals(nombre)) {
                return entrada;
            }
        }
        return -1;
    }

    /* ==================== ZONA 2: MEMORIA VIRTUAL (SWAP) ==================== */

    /**
     * Reserva un bloque de N posiciones consecutivas libres en la zona de swap.
     *
     * @param tamano cantidad de posiciones necesarias
     * @return la dirección base del bloque, o -1 si no hay espacio
     */
    public int reservarBloqueSwap(int tamano) {
        if (tamano <= 0 || tamano > tamanoSwap) return -1;
        for (int inicio = inicioSwap; inicio <= inicioSwap + tamanoSwap - tamano; inicio++) {
            boolean libre = true;
            for (int i = 0; i < tamano; i++) {
                if (posiciones[inicio + i] != null) {
                    libre = false;
                    break;
                }
            }
            if (libre) return inicio;
        }
        return -1;
    }

    /**
     * Escribe un bloque en la zona de swap.
     */
    public void escribirBloqueSwap(int direccionBase, Object[] valores) {
        for (int i = 0; i < valores.length; i++) {
            posiciones[direccionBase + i] = valores[i];
        }
    }

    /**
     * Lee un bloque de la zona de swap.
     */
    public Object[] leerBloqueSwap(int direccionBase, int tamano) {
        Object[] valores = new Object[tamano];
        for (int i = 0; i < tamano; i++) {
            valores[i] = posiciones[direccionBase + i];
        }
        return valores;
    }

    /**
     * Libera un bloque de la zona de swap.
     */
    public void liberarBloqueSwap(int direccionBase, int tamano) {
        for (int i = 0; i < tamano; i++) {
            posiciones[direccionBase + i] = null;
        }
    }

    /**
     * @return cantidad de posiciones libres en la zona de swap.
     */
    public int getEspacioSwapLibre() {
        int contador = 0;
        for (int i = 0; i < tamanoSwap; i++) {
            if (posiciones[inicioSwap + i] == null) contador++;
        }
        return contador;
    }

    /**
     * @return true si el swap está lleno.
     */
    public boolean swapEstaLleno() {
        return getEspacioSwapLibre() == 0;
    }

    /* ==================== ZONA 3: ARCHIVOS ==================== */

    /**
     * Reserva un bloque de N posiciones consecutivas libres en la zona de archivos.
     *
     * @param tamano cantidad de posiciones necesarias
     * @return la dirección base del bloque, o -1 si no hay espacio
     */
    public int reservarBloqueArchivo(int tamano) {
        if (tamano <= 0) return -1;
        int finZona = tamanoTotal;
        for (int inicio = inicioArchivos; inicio <= finZona - tamano; inicio++) {
            boolean libre = true;
            for (int i = 0; i < tamano; i++) {
                if (posiciones[inicio + i] != null) {
                    libre = false;
                    break;
                }
            }
            if (libre) return inicio;
        }
        return -1;
    }

    /**
     * Escribe un bloque de contenido en la zona de archivos.
     */
    public void escribirBloqueArchivo(int direccionBase, Object[] valores) {
        for (int i = 0; i < valores.length; i++) {
            posiciones[direccionBase + i] = valores[i];
        }
    }

    /**
     * Lee un bloque de la zona de archivos.
     */
    public Object[] leerBloqueArchivo(int direccionBase, int tamano) {
        Object[] valores = new Object[tamano];
        for (int i = 0; i < tamano; i++) {
            valores[i] = posiciones[direccionBase + i];
        }
        return valores;
    }

    /**
     * Libera un bloque de la zona de archivos.
     */
    public void liberarBloqueArchivo(int direccionBase, int tamano) {
        for (int i = 0; i < tamano; i++) {
            posiciones[direccionBase + i] = null;
        }
    }

    /**
     * @return cantidad de posiciones libres en la zona de archivos.
     */
    public int getEspacioArchivosLibre() {
        int contador = 0;
        int tamanoZona = tamanoTotal - inicioArchivos;
        for (int i = 0; i < tamanoZona; i++) {
            if (posiciones[inicioArchivos + i] == null) contador++;
        }
        return contador;
    }

    /* ==================== CONSULTAS GENERALES ==================== */

    public int getTamanoTotal() {
        return tamanoTotal;
    }

    public int getMaxArchivos() {
        return maxArchivos;
    }

    public int getTamanoSwap() {
        return tamanoSwap;
    }

    public int getInicioIndice() {
        return inicioIndice;
    }

    public int getInicioSwap() {
        return inicioSwap;
    }

    public int getInicioArchivos() {
        return inicioArchivos;
    }

    /**
     * @return cantidad de posiciones ocupadas en el disco.
     */
    public int getEspacioOcupado() {
        int contador = 0;
        for (int i = 0; i < tamanoTotal; i++) {
            if (posiciones[i] != null) contador++;
        }
        return contador;
    }

    /**
     * @return porcentaje de uso del índice (0-100).
     */
    public int getPorcentajeUsoIndice() {
        if (maxArchivos == 0) return 0;
        return (getCantidadArchivos() * 100) / maxArchivos;
    }

    /**
     * @return porcentaje de uso del swap (0-100).
     */
    public int getPorcentajeUsoSwap() {
        if (tamanoSwap == 0) return 0;
        int usadas = tamanoSwap - getEspacioSwapLibre();
        return (usadas * 100) / tamanoSwap;
    }

    @Override
    public String toString() {
        return "Disco[" + tamanoTotal + " pos, "
                + getCantidadArchivos() + "/" + maxArchivos + " archivos, "
                + "swap " + getEspacioSwapLibre() + "/" + tamanoSwap + " libres]";
    }
}