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
 *   DX = cadena de texto con el nombre del archivo (String).
 *        Ejemplo: MOV DX, "datos.txt"
 *   AL = resultado (0 exito, 1 error, o contenido leido).
 *
 * IMPORTANTE: todos los archivos creados por procesos (INT 21H) van al
 * INDICE PROCESO del disco, NO al indice ASM. Esto evita conflictos
 * entre los .asm cargados por el usuario y los archivos creados por
 * los procesos en ejecucion.
 *
 * Al abrir un archivo, su nombre se agrega a las 5 posiciones de
 * archivos abiertos del BCP (Stallings, Tabla 3.5: "Resource Ownership
 * and Utilization: opened files").
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

    /**
     * INT 10H: imprime en pantalla el valor de DX.
     * DX puede ser un numero o un string.
     */
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
     * DX contiene el nombre del archivo como String.
     * Todos los archivos creados por procesos van al INDICE PROCESO.
     */
    private ResultadoInterrupcion ejecutarManejoArchivos(BCP bcp) {
        if (solicitudArchivo != null) {
            solicitudArchivo.accept(bcp);
        }

        int ah = bcp.getAh();
        String nombre = bcp.getDx();

        // Validacion: nombre vacio
        if (nombre == null || nombre.isEmpty()) {
            bcp.setAl(1);
            System.out.println("[DISCO] ERROR: nombre de archivo vacio (DX=\"" + nombre + "\")");
            return ResultadoInterrupcion.BLOQUEADO;
        }

        switch (ah) {
            case 0x3C: {  // crear archivo
                boolean creado = crearArchivoEnDisco(nombre);
                bcp.setAl(creado ? 0 : 1);
                System.out.println("[DISCO] crear(\"" + nombre + "\") = " + creado);
                break;
            }

            case 0x3D: {  // abrir archivo
                if (!existeArchivoProceso(nombre)) {
                    bcp.setAl(1);   // archivo no existe
                    System.out.println("[DISCO] abrir(\"" + nombre + "\") = NO EXISTE");
                    break;
                }
                boolean abierto = bcp.abrirArchivo(nombre);
                if (abierto) {
                    bcp.setAl(0);
                    System.out.println("[DISCO] abrir(\"" + nombre + "\") = OK");
                } else {
                    bcp.setAl(1);
                    System.out.println("[DISCO] abrir(\"" + nombre + "\") = ERROR (max "
                            + BCP.TAMANO_MAXIMO_ARCHIVOS + " archivos abiertos)");
                }
                break;
            }

            case 0x4D: {  // leer archivo
                String contenido = leerArchivoDeDisco(nombre);
                if (contenido != null && !contenido.isEmpty()) {
                    bcp.setAl(contenido.charAt(0));
                } else {
                    bcp.setAl(0);
                }
                System.out.println("[DISCO] leer(\"" + nombre + "\") = \"" + contenido + "\"");
                break;
            }

            case 0x40: {  // escribir archivo
                int al = bcp.getAl();
                String aEscribir = String.valueOf((char) al);
                boolean escrito = escribirArchivoEnDisco(nombre, aEscribir);
                bcp.setAl(escrito ? 0 : 1);
                System.out.println("[DISCO] escribir(\"" + nombre + "\", \"" + aEscribir + "\")");
                break;
            }

            case 0x41: {  // eliminar archivo
                bcp.cerrarArchivo(nombre);

                boolean eliminado = eliminarArchivoDeDisco(nombre);
                bcp.setAl(eliminado ? 0 : 1);
                System.out.println("[DISCO] eliminar(\"" + nombre + "\") = " + eliminado);
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
     * Verifica si un archivo creado por un proceso existe en el INDICE PROCESO.
     */
    private boolean existeArchivoProceso(String nombre) {
        return disco.existe(nombre, Disco.TIPO_PROCESO);
    }

    /**
     * Crea un archivo en el INDICE PROCESO (no en el indice ASM).
     */
    private boolean crearArchivoEnDisco(String nombre) {
        if (existeArchivoProceso(nombre)) return false;

        int inicio = disco.reservarBloqueArchivo(1);
        if (inicio == -1) return false;

        disco.escribir(inicio, "");

        return disco.registrarArchivoProceso(nombre, inicio, inicio, Disco.ZONA_PRINCIPAL);
    }

    private String leerArchivoDeDisco(String nombre) {
        if (!existeArchivoProceso(nombre)) return null;

        int inicio = disco.getInicioArchivo(nombre, Disco.TIPO_PROCESO);
        int fin = disco.getFinArchivo(nombre, Disco.TIPO_PROCESO);
        if (inicio == -1 || fin == -1) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = inicio; i <= fin; i++) {
            Object v = disco.leer(i);
            if (v != null) sb.append(v.toString());
        }
        return sb.toString();
    }

    private boolean escribirArchivoEnDisco(String nombre, String contenido) {
        if (!existeArchivoProceso(nombre)) return false;
        if (contenido == null) return false;

        int inicioViejo = disco.getInicioArchivo(nombre, Disco.TIPO_PROCESO);
        int finViejo = disco.getFinArchivo(nombre, Disco.TIPO_PROCESO);
        int tamanoViejo = (finViejo - inicioViejo) + 1;

        if (contenido.length() <= tamanoViejo) {
            for (int i = 0; i < contenido.length(); i++) {
                disco.escribir(inicioViejo + i, String.valueOf(contenido.charAt(i)));
            }
            for (int i = contenido.length(); i < tamanoViejo; i++) {
                disco.escribir(inicioViejo + i, null);
            }
            return true;
        }

        int nuevoInicio = disco.reservarBloqueArchivo(contenido.length());
        if (nuevoInicio == -1) return false;

        for (int i = 0; i < contenido.length(); i++) {
            disco.escribir(nuevoInicio + i, String.valueOf(contenido.charAt(i)));
        }

        disco.liberarBloqueArchivo(inicioViejo, tamanoViejo);

        disco.eliminarDelIndice(nombre, Disco.TIPO_PROCESO);
        return disco.registrarArchivoProceso(
                nombre, nuevoInicio, nuevoInicio + contenido.length() - 1,
                Disco.ZONA_PRINCIPAL);
    }

    private boolean eliminarArchivoDeDisco(String nombre) {
        if (!existeArchivoProceso(nombre)) return false;

        int inicio = disco.getInicioArchivo(nombre, Disco.TIPO_PROCESO);
        int fin = disco.getFinArchivo(nombre, Disco.TIPO_PROCESO);
        if (inicio == -1 || fin == -1) return false;

        int tamano = (fin - inicio) + 1;

        disco.liberarBloqueArchivo(inicio, tamano);
        disco.eliminarDelIndice(nombre, Disco.TIPO_PROCESO);

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