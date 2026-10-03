package logica;

import modelo.BCP;
import modelo.Disco;

import java.util.function.Consumer;

/**
 * Maneja las interrupciones del sistema (llamadas al sistema).
 *
 * Segun Stallings (seccion 3.4, "Process Control"), las interrupciones
 * son supervisor calls: el programa en ejecucion pide un servicio al SO.
 *
 * INT 21H: manejo de archivos.
 *   AH = operacion:
 *     3Ch -> crear
 *     3Dh -> abrir
 *     4Dh -> leer
 *     40h -> escribir
 *     41h -> eliminar
 *   DX = numero usado para construir el nombre "archivo_<DX>".
 *   AL = resultado (0 exito, 1 error, o contenido leido).
 *
 * El disco simulado tiene 3 zonas: indice, swap, archivos.
 * Los archivos se registran en el indice con su nombre y sus
 * posiciones de inicio/fin en la zona de archivos.
 */
public class Interrupciones {

    private final Disco disco;

    /* ==================== CALLBACKS ==================== */

    private Consumer<String> salidaPantalla;
    private Consumer<BCP> solicitudTeclado;
    private Consumer<BCP> solicitudArchivo;

    /* ==================== CONSTRUCTOR ==================== */

    public Interrupciones(Disco disco) {
        this.disco = disco;
    }

    /* ==================== EJECUCION ==================== */

    public ResultadoInterrupcion ejecutar(int codigo, BCP bcp) {
        switch (codigo) {
            case 0x20: return ejecutarFinPrograma(bcp);
            case 0x10: return ejecutarImprimirPantalla(bcp);
            case 0x09: return ejecutarLecturaTeclado(bcp);
            case 0x21: return ejecutarManejoArchivos(bcp);
            default:
                throw new UnsupportedOperationException(
                    "Interrupcion no soportada: " + Integer.toHexString(codigo) + "H");
        }
    }

    /* ==================== MANEJADORES ==================== */

    private ResultadoInterrupcion ejecutarFinPrograma(BCP bcp) {
        bcp.marcarFin();
        return ResultadoInterrupcion.TERMINADO;
    }

    private ResultadoInterrupcion ejecutarImprimirPantalla(BCP bcp) {
        String mensaje = "[PANTALLA] DX = " + bcp.getDx();
        if (salidaPantalla != null) {
            salidaPantalla.accept(mensaje);
        }
        return ResultadoInterrupcion.RUNNING;
    }

    private ResultadoInterrupcion ejecutarLecturaTeclado(BCP bcp) {
        if (solicitudTeclado != null) {
            solicitudTeclado.accept(bcp);
        }
        return ResultadoInterrupcion.BLOQUEADO;
    }

    /**
     * INT 21H: manejo de archivos.
     *
     * El archivo se identifica por "archivo_<DX>".
     * El disco registra los archivos en su indice (nombre, inicio, fin).
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
                boolean creado = crearArchivoEnDisco(nombre);
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
                String contenido = leerArchivoDeDisco(nombre);
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
                boolean escrito = escribirArchivoEnDisco(nombre, aEscribir);
                bcp.setAl(escrito ? 0 : 1);
                System.out.println("[DISCO] escribir(" + nombre + ", " + aEscribir + ")");
                break;
            }

            case 0x41: {  // eliminar archivo
                boolean eliminado = eliminarArchivoDeDisco(nombre);
                bcp.setAl(eliminado ? 0 : 1);
                System.out.println("[DISCO] eliminar(" + nombre + ") = " + eliminado);
                break;
            }

            default: {
                bcp.setAl(0xFF);
                System.out.println("[DISCO] operacion desconocida: AH=" + Integer.toHexString(ah));
                break;
            }
        }

        return ResultadoInterrupcion.BLOQUEADO;
    }

    /* ==================== HELPERS DE ARCHIVOS ==================== */

    /**
     * Crea un archivo vacio en el disco (1 posicion reservada).
     * Lo registra en el indice.
     */
    private boolean crearArchivoEnDisco(String nombre) {
        if (disco.existe(nombre)) return false;

        int inicio = disco.reservarBloqueArchivo(1);
        if (inicio == -1) return false;

        // Escribir un caracter vacio
        disco.escribir(inicio, "");

        return disco.registrarArchivo(nombre, inicio, inicio);
    }

    /**
     * Lee el contenido completo de un archivo desde el disco.
     */
    private String leerArchivoDeDisco(String nombre) {
        if (!disco.existe(nombre)) return null;

        int inicio = disco.getInicioArchivo(nombre);
        int fin = disco.getFinArchivo(nombre);
        if (inicio == -1 || fin == -1) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = inicio; i <= fin; i++) {
            Object v = disco.leer(i);
            if (v != null) sb.append(v.toString());
        }
        return sb.toString();
    }

    /**
     * Escribe contenido en un archivo existente.
     * Si el contenido es mas grande que el bloque actual, intenta
     * reservar un bloque mas grande.
     */
    private boolean escribirArchivoEnDisco(String nombre, String contenido) {
        if (!disco.existe(nombre)) return false;
        if (contenido == null) return false;

        int inicioViejo = disco.getInicioArchivo(nombre);
        int finViejo = disco.getFinArchivo(nombre);
        int tamanoViejo = (finViejo - inicioViejo) + 1;

        // Si el contenido cabe en el bloque actual, sobreescribir
        if (contenido.length() <= tamanoViejo) {
            for (int i = 0; i < contenido.length(); i++) {
                disco.escribir(inicioViejo + i, String.valueOf(contenido.charAt(i)));
            }
            // Limpiar el resto del bloque
            for (int i = contenido.length(); i < tamanoViejo; i++) {
                disco.escribir(inicioViejo + i, null);
            }
            return true;
        }

        // Contenido mas grande: reservar bloque nuevo y liberar el viejo
        int nuevoInicio = disco.reservarBloqueArchivo(contenido.length());
        if (nuevoInicio == -1) return false;

        // Escribir el contenido
        for (int i = 0; i < contenido.length(); i++) {
            disco.escribir(nuevoInicio + i, String.valueOf(contenido.charAt(i)));
        }

        // Liberar bloque viejo
        disco.liberarBloqueArchivo(inicioViejo, tamanoViejo);

        // Actualizar indice
        disco.eliminarDelIndice(nombre);
        return disco.registrarArchivo(nombre, nuevoInicio, nuevoInicio + contenido.length() - 1);
    }

    /**
     * Elimina un archivo: libera su bloque y lo quita del indice.
     */
    private boolean eliminarArchivoDeDisco(String nombre) {
        if (!disco.existe(nombre)) return false;

        int inicio = disco.getInicioArchivo(nombre);
        int fin = disco.getFinArchivo(nombre);
        if (inicio == -1 || fin == -1) return false;

        int tamano = (fin - inicio) + 1;

        disco.liberarBloqueArchivo(inicio, tamano);
        disco.eliminarDelIndice(nombre);

        return true;
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