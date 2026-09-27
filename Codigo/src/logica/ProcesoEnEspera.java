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
 *   - estado:        READY_SUSPEND o BLOCKED_SUSPEND.
 *   - direccionBaseEnSwap: posición donde empieza el process image en swap.
 *   - tamanoEnSwap:  cantidad de posiciones ocupadas (22 + N instrucciones).
 */
public class ProcesoEnEspera {

    /** Identificador del proceso. */
    private final int id;

    /** Estado del proceso suspendido. */
    private final EstadoProceso estado;

    /** Dirección base del process image dentro del swap (-1 si no asignada). */
    private int direccionBaseEnSwap;

    /** Cantidad de posiciones que ocupa en el swap (22 + instrucciones). */
    private final int tamanoEnSwap;

    /**
     * Crea los metadatos de un proceso suspendido.
     *
     * @param id              identificador del proceso
     * @param estado          READY_SUSPEND o BLOCKED_SUSPEND
     * @param tamanoEnSwap    cantidad de posiciones que ocupa en swap
     *                        (debe ser >= BCP.POSICIONES_REQUERIDAS)
     * @throws IllegalArgumentException si algún argumento es inválido
     */
    public ProcesoEnEspera(int id, EstadoProceso estado, int tamanoEnSwap) {
        if (estado != EstadoProceso.READY_SUSPEND
                && estado != EstadoProceso.BLOCKED_SUSPEND) {
            throw new IllegalArgumentException(
                "El estado de un proceso suspendido debe ser READY_SUSPEND o BLOCKED_SUSPEND");
        }
        if (tamanoEnSwap < modelo.BCP.POSICIONES_REQUERIDAS) {
            throw new IllegalArgumentException(
                "El tamaño en swap debe ser al menos "
                + modelo.BCP.POSICIONES_REQUERIDAS + " posiciones");
        }
        this.id = id;
        this.estado = estado;
        this.tamanoEnSwap = tamanoEnSwap;
        this.direccionBaseEnSwap = -1;
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

    /* ==================== toString ==================== */

    @Override
    public String toString() {
        return "ProcesoEnEspera{id=" + id
                + ", estado=" + estado
                + ", dirSwap=" + direccionBaseEnSwap
                + ", tamano=" + tamanoEnSwap + "}";
    }
}