package logica;

import modelo.EstadoProceso;

/**
 * Representa un proceso que ha sido suspendido (swap out) y reside
 * temporalmente en la memoria virtual (swap).
 *
 * Es un objeto de METADATOS: no guarda los valores del BCP ni las
 * instrucciones. Esos viven en las posiciones de {@link modelo.MemoriaVirtual}.
 *
 * Sirve como entrada de la "tabla del SO" que describe qué procesos
 * están suspendidos y dónde (Stallings, sección 3.3, "Operating System
 * Control Structures" — Memory Tables).
 *
 * Atributos:
 *   - id:            identificador del proceso.
 *   - estado:        NEW, READY_SUSPEND o BLOCKED_SUSPEND.
 *   - direccionBaseEnSwap: posición donde empiezan las instrucciones en swap.
 *   - tamanoEnSwap:  cantidad de posiciones ocupadas (N instrucciones).
 *   - nombreArchivo: nombre del archivo .asm original (para el índice del disco).
 *
 * Nota: según el profe, el BCP NUNCA se mueve de memoria principal.
 * Por eso el swap solo guarda las INSTRUCCIONES del proceso.
 */
public class ProcesoEnEspera {

    /** Identificador del proceso. */
    private final int id;

    /** Estado del proceso suspendido. */
    private final EstadoProceso estado;

    /** Dirección base de las instrucciones dentro del swap (-1 si no asignada). */
    private int direccionBaseEnSwap;

    /** Cantidad de posiciones que ocupa en el swap (N instrucciones). */
    private final int tamanoEnSwap;

    /** Nombre del archivo .asm original (para el índice del disco). */
    private String nombreArchivo;

    /**
     * Crea los metadatos de un proceso suspendido.
     *
     * @param id              identificador del proceso
     * @param estado          NEW, READY_SUSPEND o BLOCKED_SUSPEND
     * @param tamanoEnSwap    cantidad de posiciones que ocupa en swap
     *                        (debe ser > 0)
     * @throws IllegalArgumentException si algún argumento es inválido
     */
    public ProcesoEnEspera(int id, EstadoProceso estado, int tamanoEnSwap) {
        if (estado != EstadoProceso.NEW
                && estado != EstadoProceso.READY_SUSPEND
                && estado != EstadoProceso.BLOCKED_SUSPEND) {
            throw new IllegalArgumentException(
                "El estado de un proceso suspendido debe ser NEW, READY_SUSPEND o BLOCKED_SUSPEND");
        }
        if (tamanoEnSwap <= 0) {
            throw new IllegalArgumentException(
                "El tamaño en swap debe ser mayor a 0");
        }
        this.id = id;
        this.estado = estado;
        this.tamanoEnSwap = tamanoEnSwap;
        this.direccionBaseEnSwap = -1;
        this.nombreArchivo = null;
    }

    /* ==================== GETTERS ==================== */

    public int getId() {
        return id;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public int getDireccionBaseEnSwap() {
        return direccionBaseEnSwap;
    }

    public int getTamanoEnSwap() {
        return tamanoEnSwap;
    }

    /**
     * @return el nombre del archivo .asm original, o null si no se asignó.
     */
    public String getNombreArchivo() {
        return nombreArchivo;
    }

    /* ==================== SETTERS ==================== */

    /**
     * Establece la dirección base del proceso dentro del swap.
     * Invocado por GestorProcesos al reservar el bloque.
     *
     * @param direccionBase posición inicial en swap
     */
    public void setDireccionBaseEnSwap(int direccionBase) {
        this.direccionBaseEnSwap = direccionBase;
    }

    /**
     * Establece el nombre del archivo .asm original.
     * Invocado por GestorProcesos al suspender el proceso.
     *
     * @param nombreArchivo nombre del archivo .asm
     */
    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    /* ==================== toString ==================== */

    @Override
    public String toString() {
        return "ProcesoEnEspera{id=" + id
                + ", estado=" + estado
                + ", dirSwap=" + direccionBaseEnSwap
                + ", tamano=" + tamanoEnSwap
                + ", archivo=" + nombreArchivo + "}";
    }
}