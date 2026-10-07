package logica;

import modelo.Memoria;

import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia de particionamiento DINAMICO de la zona usuario.
 *
 * Usa FIRST-FIT (Stallings, seccion 7.2).
 *
 * Cada asignacion/liberacion se sincroniza con la TablaMemoria del kernel.
 * Cada bloque asignado ocupa 3 posiciones:
 *   [0] = idProceso (Integer)
 *   [1] = inicio    (Integer)
 *   [2] = tamano    (Integer)
 *
 * El tamano de la TablaMemoria es proporcional a bcpsQueCaben:
 *   tamanoTablaMemoria = bcpsQueCaben × 3
 * (un bloque por cada BCP que cabe en el kernel).
 */
public class ParticionadorDinamico {

    /** Cuantas posiciones ocupa cada bloque en la TablaMemoria. */
    public static final int POSICIONES_POR_BLOQUE = 3;

    private final int inicioZonaUsuario;
    private final int finZonaUsuario;
    private final List<BloqueLibre> bloquesLibres;
    private final Memoria memoria;

    public ParticionadorDinamico(Memoria memoria,
                                  int inicioZonaUsuario,
                                  int espacioUsuarioDisponible) {
        if (espacioUsuarioDisponible <= 0) {
            throw new IllegalArgumentException(
                "El espacio de usuario debe ser mayor a 0");
        }
        this.memoria = memoria;
        this.inicioZonaUsuario = inicioZonaUsuario;
        this.finZonaUsuario = inicioZonaUsuario + espacioUsuarioDisponible;
        this.bloquesLibres = new ArrayList<>();
        this.bloquesLibres.add(new BloqueLibre(inicioZonaUsuario, espacioUsuarioDisponible));
    }

    /* ==================== ASIGNACION ==================== */

    public int asignarParticion(int tamano, int idProceso) {
        if (tamano <= 0) return -1;

        for (int i = 0; i < bloquesLibres.size(); i++) {
            BloqueLibre bloque = bloquesLibres.get(i);
            if (bloque.tamano >= tamano) {
                int base = bloque.inicio;

                if (bloque.tamano == tamano) {
                    bloquesLibres.remove(i);
                } else {
                    bloque.inicio += tamano;
                    bloque.tamano -= tamano;
                }

                registrarEnTablaMemoria(idProceso, base, tamano);
                return base;
            }
        }
        return -1;
    }

    /* ==================== LIBERACION ==================== */

    public void liberarParticion(int base, int tamano, int idProceso) {
        if (tamano <= 0) return;

        bloquesLibres.add(new BloqueLibre(base, tamano));
        fusionarBloques();

        eliminarDeTablaMemoria(idProceso);
    }

    private void fusionarBloques() {
        bloquesLibres.sort((a, b) -> Integer.compare(a.inicio, b.inicio));

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

    /* ==================== TABLA DE MEMORIA ==================== */

    /**
     * Registra un bloque en la TablaMemoria.
     * Cada bloque ocupa 3 posiciones: idProceso, inicio, tamaño.
     *
     * El tamano de la TablaMemoria es proporcional a bcpsQueCaben
     * (memoria.getTamanoTablaMemoria() / POSICIONES_POR_BLOQUE bloques).
     */
    private void registrarEnTablaMemoria(int idProceso, int base, int tamano) {
        int tamanoTabla = memoria.getTamanoTablaMemoria();
        int cantidadBloques = tamanoTabla / POSICIONES_POR_BLOQUE;

        for (int i = 0; i < cantidadBloques; i++) {
            int posId     = i * POSICIONES_POR_BLOQUE;
            int posInicio = posId + 1;
            int posTamano = posId + 2;

            Object v = memoria.leerTablaMemoria(posId);
            if (v == null) {
                memoria.escribirTablaMemoria(posId, idProceso);
                memoria.escribirTablaMemoria(posInicio, base);
                memoria.escribirTablaMemoria(posTamano, tamano);
                return;
            }
        }
        System.err.println("[TABLA_MEMORIA] Llena: no se pudo registrar P" + idProceso);
    }

    /**
     * Elimina el bloque del proceso en la TablaMemoria.
     */
    private void eliminarDeTablaMemoria(int idProceso) {
        int tamanoTabla = memoria.getTamanoTablaMemoria();
        int cantidadBloques = tamanoTabla / POSICIONES_POR_BLOQUE;

        for (int i = 0; i < cantidadBloques; i++) {
            int posId = i * POSICIONES_POR_BLOQUE;
            Object v = memoria.leerTablaMemoria(posId);
            if (v != null && ((Integer) v) == idProceso) {
                memoria.escribirTablaMemoria(posId, null);
                memoria.escribirTablaMemoria(posId + 1, null);
                memoria.escribirTablaMemoria(posId + 2, null);
                return;
            }
        }
    }

    /* ==================== CONSULTAS ==================== */

    public int getEspacioLibre() {
        int total = 0;
        for (BloqueLibre b : bloquesLibres) total += b.tamano;
        return total;
    }

    public int getCantidadHuecos() { return bloquesLibres.size(); }

    public int getHuecoMasGrande() {
        int max = 0;
        for (BloqueLibre b : bloquesLibres) if (b.tamano > max) max = b.tamano;
        return max;
    }

    public boolean hayEspacioPara(int tamano) {
        for (BloqueLibre b : bloquesLibres) if (b.tamano >= tamano) return true;
        return false;
    }

    public int getInicioZonaUsuario() { return inicioZonaUsuario; }
    public int getFinZonaUsuario() { return finZonaUsuario; }
    public int getEspacioTotal() { return finZonaUsuario - inicioZonaUsuario; }

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