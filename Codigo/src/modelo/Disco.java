package modelo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Disco simulado: almacena archivos como pares (nombre -> contenido)
 * y mantiene un indice (nombre -> direccion simulada).
 *
 * Segun el enunciado, el disco tiene un tamano configurable (default 512)
 * y un indice de archivos en los primeros registros. En esta simulacion:
 *   - El indice vive en un Map<String, Integer>.
 *   - Las direcciones se asignan secuencialmente a partir de
 *     PRIMERA_DIRECCION_CONTENIDO.
 *   - El contenido de los archivos vive en un Map<String, String>.
 */
public class Disco {

    /** Tamano total del disco (en "posiciones" simuladas). */
    private final int tamanoTotal;

    /** Cantidad maxima de archivos que caben en el indice. */
    public static final int MAX_ARCHIVOS = 20;

    /** Primera direccion disponible para el contenido de los archivos. */
    public static final int PRIMERA_DIRECCION_CONTENIDO = 100;

    /** Archivos: nombre -> contenido. */
    private final Map<String, String> archivos;

    /** Indice: nombre -> direccion simulada. */
    private final Map<String, Integer> indice;

    /** Proxima direccion libre para el contenido. */
    private int proximaDireccion;

    /**
     * Crea un disco vacio con el tamano indicado.
     *
     * @param tamanoTotal cantidad de posiciones del disco (>= 1)
     * @throws IllegalArgumentException si tamanoTotal < 1
     */
    public Disco(int tamanoTotal) {
        if (tamanoTotal < 1) {
            throw new IllegalArgumentException("El tamano del disco debe ser >= 1");
        }
        this.tamanoTotal = tamanoTotal;
        this.archivos = new HashMap<>();
        this.indice = new HashMap<>();
        this.proximaDireccion = PRIMERA_DIRECCION_CONTENIDO;
    }

    /* ==================== OPERACIONES ==================== */

    /**
     * Crea un archivo vacio en el disco y lo registra en el indice.
     * Falla si el archivo ya existe o si el indice esta lleno.
     *
     * @param nombre nombre del archivo
     * @return true si se creo correctamente
     */
    public boolean crear(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            return false;
        }
        if (archivos.containsKey(nombre)) {
            return false;   // ya existe
        }
        if (indice.size() >= MAX_ARCHIVOS) {
            return false;   // indice lleno
        }
        archivos.put(nombre, "");
        indice.put(nombre, proximaDireccion);
        proximaDireccion += 50;   // cada archivo ocupa 50 posiciones simuladas
        return true;
    }

    /**
     * Verifica si un archivo existe en el disco.
     */
    public boolean existe(String nombre) {
        return nombre != null && archivos.containsKey(nombre);
    }

    /**
     * Lee el contenido de un archivo.
     *
     * @param nombre nombre del archivo
     * @return contenido del archivo, o null si no existe
     */
    public String leer(String nombre) {
        if (nombre == null) return null;
        return archivos.get(nombre);
    }

    /**
     * Escribe contenido en un archivo existente.
     *
     * @param nombre    nombre del archivo
     * @param contenido contenido a escribir
     * @return true si se escribio correctamente
     */
    public boolean escribir(String nombre, String contenido) {
        if (nombre == null || contenido == null) {
            return false;
        }
        if (!archivos.containsKey(nombre)) {
            return false;   // no existe
        }
        archivos.put(nombre, contenido);
        return true;
    }

    /**
     * Guarda un archivo completo en el disco.
     * Si no existe, lo crea; si existe, sobreescribe el contenido.
     *
     * @param nombre    nombre del archivo
     * @param contenido contenido a guardar
     * @return true si se guardo correctamente
     */
    public boolean guardar(String nombre, String contenido) {
        if (nombre == null || contenido == null) {
            return false;
        }
        if (!archivos.containsKey(nombre)) {
            boolean creado = crear(nombre);
            if (!creado) return false;
        }
        return escribir(nombre, contenido);
    }

    /**
     * Elimina un archivo del disco y lo quita del indice.
     *
     * @param nombre nombre del archivo
     * @return true si se elimino correctamente
     */
    public boolean eliminar(String nombre) {
        if (nombre == null) return false;
        if (!archivos.containsKey(nombre)) {
            return false;   // no existe
        }
        archivos.remove(nombre);
        indice.remove(nombre);
        return true;
    }

    /* ==================== CONSULTAS ==================== */

    /**
     * @return la direccion simulada de un archivo, o -1 si no existe.
     */
    public int getDireccion(String nombre) {
        Integer dir = indice.get(nombre);
        return (dir != null) ? dir : -1;
    }

    /**
     * @return el indice completo (vista de solo lectura).
     */
    public Map<String, Integer> getIndice() {
        return Collections.unmodifiableMap(indice);
    }

    /**
     * @return el mapa de archivos (vista de solo lectura).
     */
    public Map<String, String> getArchivos() {
        return Collections.unmodifiableMap(archivos);
    }

    /** @return cantidad de archivos almacenados. */
    public int getCantidadArchivos() {
        return archivos.size();
    }

    /** @return cantidad de posiciones totales del disco. */
    public int getTamanoTotal() {
        return tamanoTotal;
    }

    /**
     * @return porcentaje de uso del indice del disco (0-100).
     */
    public int getPorcentajeUso() {
        if (MAX_ARCHIVOS == 0) return 0;
        return (indice.size() * 100) / MAX_ARCHIVOS;
    }

    @Override
    public String toString() {
        return "Disco[" + archivos.size() + " archivos, dir max="
                + proximaDireccion + "/" + tamanoTotal + "]";
    }
}