package modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Disco simulado (almacenamiento secundario).
 *
 * El disco se divide en CUATRO zonas contiguas:
 *
 *   ┌─────────────────────────────────────────────────────┐
 *   │  ZONA 1: Índice de ASM                              │
 *   │  ├── maxArchivos configurable (default 10)          │
 *   │  ├── 4 posiciones por archivo:                      │
 *   │  │     [nombre] [inicio] [fin] [zona]               │
 *   │  └── Total = maxArchivos * 4                        │
 *   ├─────────────────────────────────────────────────────┤
 *   │  ZONA 2: Índice de archivos de PROCESO              │
 *   │  ├── maxArchivos configurable (default 10)          │
 *   │  ├── 4 posiciones por archivo:                      │
 *   │  │     [nombre] [inicio] [fin] [zona]               │
 *   │  └── Total = maxArchivos * 4                        │
 *   ├─────────────────────────────────────────────────────┤
 *   │  ZONA 3: Memoria virtual (swap)                     │
 *   │  ├── tamaño configurable (default 64)               │
 *   │  └── guarda instrucciones de procesos NEW           │
 *   ├─────────────────────────────────────────────────────┤
 *   │  ZONA 4: Archivos                                   │
 *   │  └── contenido de los .asm y otros archivos         │
 *   └─────────────────────────────────────────────────────┘
 *
 * Analogía con el modelo de Stallings:
 *   - Memoria principal = procesos activos (READY, RUNNING, BLOCKED).
 *   - Disco             = índice + swap + archivos.
 *
 * La zona "zona" del índice indica dónde está el contenido del archivo:
 *   - "PRINCIPAL" → en la zona de archivos.
 *   - "VIRTUAL"   → en la zona de swap.
 *
 * IMPORTANTE: si un archivo ya existe en el índice, registrarArchivo()
 * actualiza su posición y zona en vez de duplicarlo.
 */
public class Disco {

    /** Tamaño mínimo del disco (en posiciones). */
    public static final int TAMANO_MINIMO = 512;

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

    /** Tipo de archivo: .asm cargado por el usuario. */
    public static final String TIPO_ASM = "ASM";

    /** Tipo de archivo: archivo creado por un proceso. */
    public static final String TIPO_PROCESO = "PROCESO";

    /** Tamaño mínimo de la memoria virtual (swap). */
    public static final int TAMANO_SWAP_MINIMO = 64;

    /** Tamaño por defecto de la memoria virtual (swap). */
    public static final int TAMANO_SWAP_DEFAULT = 64;

    /** Posiciones mínimas reservadas para archivos. */
    public static final int ESPACIO_ARCHIVOS_MINIMO = 64;

    /* ==================== CONFIGURACIÓN ==================== */

    private final int tamanoTotal;
    private final int maxArchivos;
    private final int tamanoSwap;

    /* ==================== ZONAS (calculadas) ==================== */

    private final int inicioIndiceAsm;        // 0
    private final int inicioIndiceProceso;    // maxArchivos * 4
    private final int inicioSwap;             // maxArchivos * 8
    private final int inicioArchivos;         // maxArchivos * 8 + tamanoSwap

    /* ==================== CONTENIDO ==================== */

    private final Object[] posiciones;

    /* ==================== CONSTRUCTOR ==================== */

    /**
     * Crea un disco con las tres zonas (índice ASM, índice PROCESO, swap)
     * y calcula el inicio de cada zona y el espacio restante para archivos.
     * Lanza excepción si los tamaños no permiten un disco válido.
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

        int espacioIndiceAsm = maxArchivos * POSICIONES_POR_ENTRADA_INDICE;
        int espacioIndiceProceso = maxArchivos * POSICIONES_POR_ENTRADA_INDICE;
        int espacioIndice = espacioIndiceAsm + espacioIndiceProceso;
        int espacioOcupado = espacioIndice + tamanoSwap;
        int espacioArchivos = tamanoTotal - espacioOcupado;

        if (espacioArchivos < ESPACIO_ARCHIVOS_MINIMO) {
            throw new IllegalArgumentException(
                "El disco es muy pequeño: índice ASM (" + espacioIndiceAsm
                + ") + índice PROCESO (" + espacioIndiceProceso
                + ") + swap (" + tamanoSwap + ") dejan solo "
                + espacioArchivos + " posiciones para archivos (mínimo "
                + ESPACIO_ARCHIVOS_MINIMO + ")");
        }

        this.tamanoTotal = tamanoTotal;
        this.maxArchivos = maxArchivos;
        this.tamanoSwap = tamanoSwap;

        this.inicioIndiceAsm = 0;
        this.inicioIndiceProceso = espacioIndiceAsm;
        this.inicioSwap = espacioIndiceAsm + espacioIndiceProceso;
        this.inicioArchivos = espacioIndiceAsm + espacioIndiceProceso + tamanoSwap;

        this.posiciones = new Object[tamanoTotal];
    }

    /* ==================== ACCESO PUNTUAL ==================== */

    // Lee el contenido de una posición del disco.
    public Object leer(int posicion) {
        return posiciones[posicion];
    }

    // Escribe un valor en una posición del disco.
    public void escribir(int posicion, Object valor) {
        posiciones[posicion] = valor;
    }

    /* ==================== ZONA 1: ÍNDICE ASM ==================== */

    /**
     * Registra un archivo .asm en el índice ASM (zona PRINCIPAL por defecto).
     */
    public boolean registrarArchivoAsm(String nombre, int inicio, int fin) {
        return registrarArchivoAsm(nombre, inicio, fin, ZONA_PRINCIPAL);
    }

    /**
     * Registra un archivo .asm en el índice ASM con la zona indicada.
     * Si ya existe, actualiza en vez de duplicar.
     */
    public boolean registrarArchivoAsm(String nombre, int inicio, int fin, String zona) {
        if (nombre == null || nombre.isEmpty()) return false;

        int entradaExistente = buscarEntradaPorNombre(nombre, inicioIndiceAsm);
        if (entradaExistente != -1) {
            posiciones[entradaExistente + 1] = inicio;
            posiciones[entradaExistente + 2] = fin;
            posiciones[entradaExistente + 3] = zona;
            return true;
        }

        if (contarArchivos(inicioIndiceAsm) >= maxArchivos) return false;

        int entrada = buscarEntradaLibre(inicioIndiceAsm);
        if (entrada == -1) return false;

        posiciones[entrada]     = nombre;
        posiciones[entrada + 1] = inicio;
        posiciones[entrada + 2] = fin;
        posiciones[entrada + 3] = zona;
        return true;
    }

    /* ==================== ZONA 2: ÍNDICE PROCESO ==================== */

    /**
     * Registra un archivo creado por un proceso en el índice PROCESO.
     */
    public boolean registrarArchivoProceso(String nombre, int inicio, int fin) {
        return registrarArchivoProceso(nombre, inicio, fin, ZONA_PRINCIPAL);
    }

    /**
     * Registra un archivo creado por un proceso en el índice PROCESO.
     * Si ya existe, actualiza en vez de duplicar.
     */
    public boolean registrarArchivoProceso(String nombre, int inicio, int fin, String zona) {
        if (nombre == null || nombre.isEmpty()) return false;

        int entradaExistente = buscarEntradaPorNombre(nombre, inicioIndiceProceso);
        if (entradaExistente != -1) {
            posiciones[entradaExistente + 1] = inicio;
            posiciones[entradaExistente + 2] = fin;
            posiciones[entradaExistente + 3] = zona;
            return true;
        }

        if (contarArchivos(inicioIndiceProceso) >= maxArchivos) return false;

        int entrada = buscarEntradaLibre(inicioIndiceProceso);
        if (entrada == -1) return false;

        posiciones[entrada]     = nombre;
        posiciones[entrada + 1] = inicio;
        posiciones[entrada + 2] = fin;
        posiciones[entrada + 3] = zona;
        return true;
    }

    /* ==================== MÉTODOS GENÉRICOS (por tipo) ==================== */

    /**
     * Registra un archivo en el índice indicado (tipo ASM o PROCESO).
     */
    public boolean registrarArchivo(String nombre, int inicio, int fin, String zona, String tipo) {
        if (TIPO_ASM.equals(tipo)) {
            return registrarArchivoAsm(nombre, inicio, fin, zona);
        } else {
            return registrarArchivoProceso(nombre, inicio, fin, zona);
        }
    }

    /**
     * @return inicio del índice del tipo indicado.
     */
    public int getInicioIndicePorTipo(String tipo) {
        return TIPO_ASM.equals(tipo) ? inicioIndiceAsm : inicioIndiceProceso;
    }

    /**
     * Elimina un archivo del índice indicado.
     */
    public boolean eliminarDelIndice(String nombre, String tipo) {
        int inicio = getInicioIndicePorTipo(tipo);
        int entrada = buscarEntradaPorNombre(nombre, inicio);
        if (entrada == -1) return false;

        posiciones[entrada]     = null;
        posiciones[entrada + 1] = null;
        posiciones[entrada + 2] = null;
        posiciones[entrada + 3] = null;
        return true;
    }

    /**
     * Verifica si un archivo existe en el índice indicado.
     */
    public boolean existe(String nombre, String tipo) {
        return buscarEntradaPorNombre(nombre, getInicioIndicePorTipo(tipo)) != -1;
    }

    /**
     * @return posición de inicio del archivo, o -1 si no existe.
     */
    public int getInicioArchivo(String nombre, String tipo) {
        int entrada = buscarEntradaPorNombre(nombre, getInicioIndicePorTipo(tipo));
        if (entrada == -1) return -1;
        Integer inicio = (Integer) posiciones[entrada + 1];
        return (inicio != null) ? inicio : -1;
    }

    // Devuelve la posición final del archivo, o -1 si no existe.
    public int getFinArchivo(String nombre, String tipo) {
        int entrada = buscarEntradaPorNombre(nombre, getInicioIndicePorTipo(tipo));
        if (entrada == -1) return -1;
        Integer fin = (Integer) posiciones[entrada + 2];
        return (fin != null) ? fin : -1;
    }

    // Devuelve la zona donde está el archivo (PRINCIPAL/VIRTUAL), o null.
    public String getZonaArchivo(String nombre, String tipo) {
        int entrada = buscarEntradaPorNombre(nombre, getInicioIndicePorTipo(tipo));
        if (entrada == -1) return null;
        return (String) posiciones[entrada + 3];
    }

    /**
     * Actualiza la zona de un archivo existente.
     */
    public boolean actualizarZona(String nombre, String tipo, String nuevaZona,
                                   int nuevoInicio, int nuevoFin) {
        int entrada = buscarEntradaPorNombre(nombre, getInicioIndicePorTipo(tipo));
        if (entrada == -1) return false;

        posiciones[entrada + 1] = nuevoInicio;
        posiciones[entrada + 2] = nuevoFin;
        posiciones[entrada + 3] = nuevaZona;
        return true;
    }

    /* ==================== CONSULTAS DEL ÍNDICE ==================== */

    // Devuelve cuántos archivos hay registrados en el índice del tipo indicado.
    public int getCantidadArchivos(String tipo) {
        return contarArchivos(getInicioIndicePorTipo(tipo));
    }

    /**
     * @return lista de todas las entradas del índice (nombre, inicio, fin, zona).
     */
    public List<String[]> getIndice(String tipo) {
        int inicio = getInicioIndicePorTipo(tipo);
        List<String[]> lista = new ArrayList<>();
        for (int i = 0; i < maxArchivos; i++) {
            int entrada = inicio + i * POSICIONES_POR_ENTRADA_INDICE;
            Object nombre = posiciones[entrada];
            if (nombre != null) {
                Object ini = posiciones[entrada + 1];
                Object fin = posiciones[entrada + 2];
                Object zona = posiciones[entrada + 3];
                lista.add(new String[]{
                    nombre.toString(),
                    ini != null ? ini.toString() : "-",
                    fin != null ? fin.toString() : "-",
                    zona != null ? zona.toString() : "-"
                });
            }
        }
        return lista;
    }

    // Cuenta las entradas ocupadas en un índice.
    private int contarArchivos(int inicio) {
        int contador = 0;
        for (int i = 0; i < maxArchivos; i++) {
            int entrada = inicio + i * POSICIONES_POR_ENTRADA_INDICE;
            if (posiciones[entrada] != null) contador++;
        }
        return contador;
    }

    // Devuelve la primera entrada libre del índice, o -1 si está lleno.
    private int buscarEntradaLibre(int inicio) {
        for (int i = 0; i < maxArchivos; i++) {
            int entrada = inicio + i * POSICIONES_POR_ENTRADA_INDICE;
            if (posiciones[entrada] == null) return entrada;
        }
        return -1;
    }

    // Busca la entrada cuyo nombre coincida, o -1 si no existe.
    private int buscarEntradaPorNombre(String nombre, int inicio) {
        if (nombre == null) return -1;
        for (int i = 0; i < maxArchivos; i++) {
            int entrada = inicio + i * POSICIONES_POR_ENTRADA_INDICE;
            Object n = posiciones[entrada];
            if (n != null && n.equals(nombre)) return entrada;
        }
        return -1;
    }

    /* ==================== ZONA 3: MEMORIA VIRTUAL (SWAP) ==================== */

    // Busca un bloque contiguo libre en swap, o -1 si no hay espacio.
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

    // Escribe un arreglo de valores en un bloque de swap.
    public void escribirBloqueSwap(int direccionBase, Object[] valores) {
        for (int i = 0; i < valores.length; i++) {
            posiciones[direccionBase + i] = valores[i];
        }
    }

    // Lee un bloque de swap y lo devuelve como arreglo.
    public Object[] leerBloqueSwap(int direccionBase, int tamano) {
        Object[] valores = new Object[tamano];
        for (int i = 0; i < tamano; i++) {
            valores[i] = posiciones[direccionBase + i];
        }
        return valores;
    }

    // Libera un bloque de swap (marca sus posiciones como null).
    public void liberarBloqueSwap(int direccionBase, int tamano) {
        for (int i = 0; i < tamano; i++) {
            posiciones[direccionBase + i] = null;
        }
    }

    // Devuelve cuántas posiciones libres quedan en swap.
    public int getEspacioSwapLibre() {
        int contador = 0;
        for (int i = 0; i < tamanoSwap; i++) {
            if (posiciones[inicioSwap + i] == null) contador++;
        }
        return contador;
    }

    // Indica si el swap está completamente lleno.
    public boolean swapEstaLleno() {
        return getEspacioSwapLibre() == 0;
    }

    /* ==================== ZONA 4: ARCHIVOS ==================== */

    // Busca un bloque contiguo libre en la zona de archivos, o -1 si no hay.
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

    // Escribe un arreglo de valores en un bloque de la zona de archivos.
    public void escribirBloqueArchivo(int direccionBase, Object[] valores) {
        for (int i = 0; i < valores.length; i++) {
            posiciones[direccionBase + i] = valores[i];
        }
    }

    // Lee un bloque de la zona de archivos y lo devuelve como arreglo.
    public Object[] leerBloqueArchivo(int direccionBase, int tamano) {
        Object[] valores = new Object[tamano];
        for (int i = 0; i < tamano; i++) {
            valores[i] = posiciones[direccionBase + i];
        }
        return valores;
    }

    // Libera un bloque de la zona de archivos (marca sus posiciones como null).
    public void liberarBloqueArchivo(int direccionBase, int tamano) {
        for (int i = 0; i < tamano; i++) {
            posiciones[direccionBase + i] = null;
        }
    }

    // Devuelve cuántas posiciones libres quedan en la zona de archivos.
    public int getEspacioArchivosLibre() {
        int contador = 0;
        int tamanoZona = tamanoTotal - inicioArchivos;
        for (int i = 0; i < tamanoZona; i++) {
            if (posiciones[inicioArchivos + i] == null) contador++;
        }
        return contador;
    }

    /* ==================== CONSULTAS GENERALES ==================== */

    public int getTamanoTotal() { return tamanoTotal; }
    public int getMaxArchivos() { return maxArchivos; }
    public int getTamanoSwap() { return tamanoSwap; }

    public int getInicioIndiceAsm() { return inicioIndiceAsm; }
    public int getInicioIndiceProceso() { return inicioIndiceProceso; }
    public int getInicioIndice() { return inicioIndiceAsm; }
    public int getInicioSwap() { return inicioSwap; }
    public int getInicioArchivos() { return inicioArchivos; }

    // Devuelve cuántas posiciones del disco están ocupadas.
    public int getEspacioOcupado() {
        int contador = 0;
        for (int i = 0; i < tamanoTotal; i++) {
            if (posiciones[i] != null) contador++;
        }
        return contador;
    }

    @Override
    public String toString() {
        return "Disco[" + tamanoTotal + " pos, "
                + "ASM=" + getCantidadArchivos(TIPO_ASM) + "/" + maxArchivos
                + ", PROCESO=" + getCantidadArchivos(TIPO_PROCESO) + "/" + maxArchivos
                + ", swap " + getEspacioSwapLibre() + "/" + tamanoSwap + " libres]";
    }
}