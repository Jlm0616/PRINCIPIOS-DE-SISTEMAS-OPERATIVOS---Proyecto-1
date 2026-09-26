package logica;

import modelo.BCP;

/**
 * Resultado de intentar cargar un programa .asm como proceso nuevo.
 *
 * Tres desenlaces posibles:
 *   - EXITO:     el proceso se creó y quedó en ListaDeTrabajos (READY).
 *   - ERROR:     el archivo no es válido o no cabe en ninguna partición.
 *   - EN_ESPERA: el archivo es válido, pero no hay partición libre
 *                por ahora; se guardó para reintentar después.
 */
public class ResultadoCarga {

    public enum Estado { EXITO, ERROR, EN_ESPERA }

    private final Estado estado;
    private final BCP bcp;           // solo si EXITO
    private final String mensajeError; // solo si ERROR

    private ResultadoCarga(Estado estado, BCP bcp, String mensajeError) {
        this.estado = estado;
        this.bcp = bcp;
        this.mensajeError = mensajeError;
    }

    public static ResultadoCarga exito(BCP bcp) {
        return new ResultadoCarga(Estado.EXITO, bcp, null);
    }

    public static ResultadoCarga error(String mensaje) {
        return new ResultadoCarga(Estado.ERROR, null, mensaje);
    }

    public static ResultadoCarga enEspera() {
        return new ResultadoCarga(Estado.EN_ESPERA, null, null);
    }

    public Estado getEstado() { return estado; }
    public BCP getBcp() { return bcp; }
    public String getMensajeError() { return mensajeError; }
}