package logica;

import modelo.BCP;

import java.util.function.Consumer;

/**
 * Maneja las interrupciones del sistema (llamadas al sistema).
 *
 * Segun Stallings (seccion 3.4, "Process Control"), las interrupciones
 * son supervisor calls: el programa en ejecucion pide un servicio al SO.
 * El SO puede:
 *
 *   - Imprimir un valor en pantalla (INT 10H).
 *   - Leer un valor del teclado (INT 09H).
 *   - Realizar una operacion de archivo (INT 21H).
 *   - Terminar el proceso (INT 20H).
 *
 * Las interrupciones NO modifican el estado del proceso directamente;
 * solo devuelven un ResultadoInterrupcion que indica a quien las invoca
 * como debe continuar la ejecucion.
 *
 * Los callbacks permiten desacoplar la logica de las interrupciones de
 * la interfaz grafica: la GUI configura que hacer cuando hay salida a
 * pantalla o cuando se necesita input del usuario.
 */
public class Interrupciones {

    /* ==================== CALLBACKS ==================== */

    /** Se invoca cuando hay salida a pantalla (INT 10H). */
    private Consumer<String> salidaPantalla;

    /** Se invoca cuando se necesita input del teclado (INT 09H). */
    private Consumer<BCP> solicitudTeclado;

    /** Se invoca cuando se necesita una operacion de archivo (INT 21H). */
    private Consumer<BCP> solicitudArchivo;

    /* ==================== CONSTRUCTOR ==================== */

    public Interrupciones() {
        // sin estado propio: los callbacks se configuran despues
    }

    /* ==================== EJECUCION ==================== */

    /**
     * Ejecuta la interrupcion indicada.
     *
     * @param codigo codigo decimal de la interrupcion (ej. 0x20, 0x10, 0x09, 0x21)
     * @param bcp    BCP del proceso que ejecuta la interrupcion
     * @return el resultado de la interrupcion (sigue vivo, bloqueado o terminado)
     * @throws UnsupportedOperationException si el codigo no esta soportado
     */
    public ResultadoInterrupcion ejecutar(int codigo, BCP bcp) {
        switch (codigo) {
            case 0x20:
                return ejecutarFinPrograma(bcp);
            case 0x10:
                return ejecutarImprimirPantalla(bcp);
            case 0x09:
                return ejecutarLecturaTeclado(bcp);
            case 0x21:
                return ejecutarManejoArchivos(bcp);
            default:
                throw new UnsupportedOperationException(
                    "Interrupcion no soportada: " + Integer.toHexString(codigo) + "H");
        }
    }

    /* ==================== MANEJADORES ==================== */

    /**
     * INT 20H: termina el programa.
     * El proceso pasa a EXIT y se registra el fin.
     */
    private ResultadoInterrupcion ejecutarFinPrograma(BCP bcp) {
        bcp.marcarFin();
        return ResultadoInterrupcion.TERMINADO;
    }

    /**
     * INT 10H: imprime el valor de DX en la pantalla simulada.
     * El proceso continua en RUNNING.
     */
    private ResultadoInterrupcion ejecutarImprimirPantalla(BCP bcp) {
        String mensaje = "[PANTALLA] DX = " + bcp.getDx();
        if (salidaPantalla != null) {
            salidaPantalla.accept(mensaje);
        }
        return ResultadoInterrupcion.RUNNING;
    }

    /**
     * INT 09H: lee un valor numerico (0-255) del teclado.
     * El proceso se bloquea hasta que el usuario ingrese un valor.
     */
    private ResultadoInterrupcion ejecutarLecturaTeclado(BCP bcp) {
        if (solicitudTeclado != null) {
            solicitudTeclado.accept(bcp);
        }
        return ResultadoInterrupcion.BLOQUEADO;
    }

    /**
     * INT 21H: manejo de archivos.
     * AH indica la operacion (crear, abrir, leer, escribir, eliminar).
     * DX contiene el nombre del archivo.
     * AL contiene el resultado.
     * El proceso se bloquea mientras se realiza la operacion.
     */
    private ResultadoInterrupcion ejecutarManejoArchivos(BCP bcp) {
        if (solicitudArchivo != null) {
            solicitudArchivo.accept(bcp);
        }
        return ResultadoInterrupcion.BLOQUEADO;
    }

    /* ==================== CONFIGURACION DE CALLBACKS ==================== */

    /**
     * Configura el callback que se invoca en INT 10H.
     *
     * @param callback recibe el mensaje a mostrar en pantalla
     */
    public void setSalidaPantalla(Consumer<String> callback) {
        this.salidaPantalla = callback;
    }

    /**
     * Configura el callback que se invoca en INT 09H.
     *
     * @param callback recibe el BCP del proceso que pide el valor
     */
    public void setSolicitudTeclado(Consumer<BCP> callback) {
        this.solicitudTeclado = callback;
    }

    /**
     * Configura el callback que se invoca en INT 21H.
     *
     * @param callback recibe el BCP del proceso que pide la operacion
     */
    public void setSolicitudArchivo(Consumer<BCP> callback) {
        this.solicitudArchivo = callback;
    }

    /* ==================== RESULTADO ==================== */

    /**
     * Resultado de ejecutar una interrupcion.
     *
     * Indica al EjecutorCPU como debe continuar la ejecucion del proceso.
     */
    public enum ResultadoInterrupcion {

        /** El proceso sigue vivo y puede continuar ejecutando (INT 10H). */
        RUNNING,

        /** El proceso se bloqueo esperando E/S (INT 09H, INT 21H). */
        BLOQUEADO,

        /** El proceso termino su ejecucion (INT 20H). */
        TERMINADO
    }
} 