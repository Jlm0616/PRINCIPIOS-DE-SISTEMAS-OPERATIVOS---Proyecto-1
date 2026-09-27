package logica;

import modelo.BCP;
import modelo.Disco;

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

    /** Disco simulado donde se almacenan los archivos. */
    private final Disco disco;

    /* ==================== CALLBACKS ==================== */

    /** Se invoca cuando hay salida a pantalla (INT 10H). */
    private Consumer<String> salidaPantalla;

    /** Se invoca cuando se necesita input del teclado (INT 09H). */
    private Consumer<BCP> solicitudTeclado;

    /** Se invoca cuando se necesita una operacion de archivo (INT 21H). */
    private Consumer<BCP> solicitudArchivo;

    /* ==================== CONSTRUCTOR ==================== */

    /**
     * Crea el manejador de interrupciones asociado a un disco.
     *
     * @param disco disco simulado para las operaciones de archivo
     */
    public Interrupciones(Disco disco) {
        this.disco = disco;
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
     *
     * AH indica la operacion:
     *   3Ch -> crear archivo
     *   3Dh -> abrir archivo
     *   4Dh -> leer archivo
     *   40h -> escribir archivo
     *   41h -> eliminar archivo
     *
     * DX contiene un numero que se usa para construir el nombre del archivo
     * (formato: "archivo_<DX>").
     *
     * AL contiene el resultado:
     *   - 0 si la operacion fue exitosa
     *   - 1 si hubo error
     *   - El contenido leido (para AH=4Dh)
     *
     * El proceso continua en RUNNING porque la operacion se realiza
     * inmediatamente (no hay espera de E/S real).
     */
    private ResultadoInterrupcion ejecutarManejoArchivos(BCP bcp) {
        if (solicitudArchivo != null) {
            solicitudArchivo.accept(bcp);
        }

        int ah = bcp.getAh();
        int dx = bcp.getDx();
        String nombre = "archivo_" + dx;

        switch (ah) {
            case 0x3C: {  // crear archivo
                boolean creado = disco.crear(nombre);
                bcp.setAl(creado ? 0 : 1);
                System.out.println("[DISCO] crear(" + nombre + ") = " + creado);
                break;
            }

            case 0x3D: {  // abrir archivo
                boolean existe = disco.existe(nombre);
                bcp.setAl(existe ? 0 : 1);
                System.out.println("[DISCO] abrir(" + nombre + ") = " + existe);
                break;
            }

            case 0x4D: {  // leer archivo
                String contenido = disco.leer(nombre);
                if (contenido != null && !contenido.isEmpty()) {
                    bcp.setAl(contenido.charAt(0));
                } else {
                    bcp.setAl(0);
                }
                System.out.println("[DISCO] leer(" + nombre + ") = " + contenido);
                break;
            }

            case 0x40: {  // escribir archivo
                int al = bcp.getAl();
                String aEscribir = String.valueOf((char) al);
                boolean escrito = disco.escribir(nombre, aEscribir);
                bcp.setAl(escrito ? 0 : 1);
                System.out.println("[DISCO] escribir(" + nombre + ", " + aEscribir + ")");
                break;
            }

            case 0x41: {  // eliminar archivo
                boolean eliminado = disco.eliminar(nombre);
                bcp.setAl(eliminado ? 0 : 1);
                System.out.println("[DISCO] eliminar(" + nombre + ") = " + eliminado);
                break;
            }

            default: {
                bcp.setAl(0xFF);   // operacion desconocida
                System.out.println("[DISCO] operacion desconocida: AH=" + Integer.toHexString(ah));
                break;
            }
        }

        return ResultadoInterrupcion.BLOQUEADO;
    }

    /* ==================== CONFIGURACION DE CALLBACKS ==================== */

    public void setSalidaPantalla(Consumer<String> callback) {
        this.salidaPantalla = callback;
    }

    public void setSolicitudTeclado(Consumer<BCP> callback) {
        this.solicitudTeclado = callback;
    }

    public void setSolicitudArchivo(Consumer<BCP> callback) {
        this.solicitudArchivo = callback;
    }

    /* ==================== RESULTADO ==================== */

    public enum ResultadoInterrupcion {
        RUNNING,
        BLOQUEADO,
        TERMINADO
    }
}