package logica;

import modelo.Instruccion;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.util.Scanner;

/**
 * Ensamblador de la máquina virtual (Proyecto 1).
 *
 * Se encarga de dos tareas:
 *   1. Validar un archivo .asm completo (sintaxis, cantidad de argumentos,
 *      registros conocidos, valores numéricos válidos).
 *   2. Leerlo y convertirlo en una lista de objetos {@link Instruccion}.
 *
 * Formato de línea general:
 *   OPCODE [ARG1[, ARG2[, ARG3]]]
 *
 * Ejemplos válidos:
 *   INC
 *   LOAD AX
 *   MOV BX, AX
 *   MOV BX, 5
 *   SWAP AX, BX
 *   JMP +3
 *   JMP -2
 *   INT 20H
 *   CMP AX, BX
 *   PARAM 5, 10, 3
 *   PUSH AX
 *
 * Si la validación falla, el mensaje del primer error queda disponible
 * en {@link #getPrimerErrorEncontrado()}.
 */
public class Ensamblador {

    /** Cantidad mínima y máxima de argumentos permitida por opcode. */
    private static final Map<String, int[]> RANGO_ARGUMENTOS = new HashMap<>();
    static {
        RANGO_ARGUMENTOS.put("LOAD",  new int[]{1, 1});
        RANGO_ARGUMENTOS.put("STORE", new int[]{1, 1});
        RANGO_ARGUMENTOS.put("MOV",   new int[]{2, 2});
        RANGO_ARGUMENTOS.put("ADD",   new int[]{1, 1});
        RANGO_ARGUMENTOS.put("SUB",   new int[]{1, 1});
        RANGO_ARGUMENTOS.put("INC",   new int[]{0, 1});
        RANGO_ARGUMENTOS.put("DEC",   new int[]{0, 1});
        RANGO_ARGUMENTOS.put("SWAP",  new int[]{2, 2});
        RANGO_ARGUMENTOS.put("INT",   new int[]{1, 1});
        RANGO_ARGUMENTOS.put("JMP",   new int[]{1, 1});
        RANGO_ARGUMENTOS.put("CMP",   new int[]{2, 2});
        RANGO_ARGUMENTOS.put("JE",    new int[]{1, 1});
        RANGO_ARGUMENTOS.put("JNE",   new int[]{1, 1});
        RANGO_ARGUMENTOS.put("PARAM", new int[]{1, 3});
        RANGO_ARGUMENTOS.put("PUSH",  new int[]{1, 1});
        RANGO_ARGUMENTOS.put("POP",   new int[]{1, 1});
    }

    /** Opcodes cuyo primer argumento (si existe) debe ser un registro. */
    private static final Set<String> PRIMER_ARG_REGISTRO = new HashSet<>(Arrays.asList(
            "LOAD", "STORE", "ADD", "SUB", "INC", "DEC", "SWAP", "CMP", "PUSH", "POP", "MOV"));

    /** Opcodes de salto, cuyo único argumento es un desplazamiento con signo. */
    private static final Set<String> OPCODES_SALTO = new HashSet<>(Arrays.asList("JMP", "JE", "JNE"));

    /** Registros válidos que pueden usarse como argumento en las instrucciones. */
    private static final Set<String> REGISTROS_VALIDOS = new HashSet<>(
            Arrays.asList("AC", "AX", "BX", "CX", "DX"));

    /** Patrón esperado para códigos de interrupción, ej. "20H", "09H". */
    private static final String PATRON_CODIGO_INTERRUPCION = "^[0-9A-Fa-f]{2}[Hh]$";

    private List<String> erroresEncontrados;

    /* ==================== VALIDACIÓN DE ARCHIVO ==================== */

    /**
     * Verifica que el archivo exista, tenga extensión .asm, y que cada
     * línea de contenido sea sintácticamente válida según las reglas
     * del conjunto de instrucciones soportado.
     *
     * Si una sola línea falla, el archivo completo se considera inválido.
     *
     * @param archivo archivo a validar
     * @return true si el archivo es válido; false en caso contrario
     */
    public boolean esArchivoValido(File archivo) {
        erroresEncontrados = new ArrayList<>();

        if (archivo == null || !archivo.exists() || !archivo.canRead()) {
            erroresEncontrados.add("El archivo no existe o no se puede leer.");
            return false;
        }

        if (!archivo.getName().toLowerCase().endsWith(".asm")) {
            erroresEncontrados.add("El archivo debe tener extension .asm");
            return false;
        }

        int numeroLinea = 0;
        boolean tieneAlMenosUnaInstruccion = false;

        try (Scanner lector = new Scanner(archivo)) {
            while (lector.hasNextLine()) {
                numeroLinea++;
                String linea = lector.nextLine().trim();

                if (linea.isEmpty()) {
                    continue;
                }

                try {
                    parsearLinea(linea);
                    tieneAlMenosUnaInstruccion = true;
                } catch (IllegalArgumentException e) {
                    erroresEncontrados.add("Linea " + numeroLinea + ": " + e.getMessage()
                            + "\n-> \"" + linea + "\"");
                }
            }
        } catch (Exception e) {
            erroresEncontrados.add("No se pudo leer el archivo: " + e.getMessage());
            return false;
        }

        if (!tieneAlMenosUnaInstruccion) {
            erroresEncontrados.add("El archivo no contiene ninguna instruccion.");
        }

        return erroresEncontrados.isEmpty();
    }

    /**
     * Devuelve todos los errores encontrados en un solo texto,
     * uno por línea, listos para mostrar en un diálogo.
     *
     * @return texto con todos los errores, o cadena vacía si no hubo
     */
    public String getErroresComoTexto() {
        if (erroresEncontrados == null || erroresEncontrados.isEmpty()) {
            return "";
        }
        return String.join("\n\n", erroresEncontrados);
    }

    /** @return la lista completa de errores. */
    public List<String> getErroresEncontrados() {
        return erroresEncontrados;
    }
    
    /* ==================== LECTURA DE ARCHIVO ==================== */

    /**
     * Lee un archivo .asm y devuelve sus instrucciones como objetos.
     *
     * Se asume que el archivo ya fue validado con {@link #esArchivoValido(File)}.
     * Si una línea no es válida, se lanza {@link IllegalArgumentException}.
     *
     * @param archivoEnsamblador archivo .asm previamente validado
     * @return lista de instrucciones en orden de aparición
     */
    public List<Instruccion> leerArchivo(File archivoEnsamblador) {
        List<Instruccion> instrucciones = new ArrayList<>();

        try (Scanner lector = new Scanner(archivoEnsamblador)) {
            while (lector.hasNextLine()) {
                String linea = lector.nextLine().trim();

                if (linea.isEmpty()) {
                    continue;
                }

                instrucciones.add(parsearLinea(linea));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error leyendo el archivo: " + e.getMessage());
        }

        return instrucciones;
    }

    /* ==================== PARSEO CENTRAL (usado por ambos métodos) ==================== */

    /**
     * Analiza una línea de código y construye la instrucción correspondiente.
     *
     * Separa el opcode de los argumentos, valida la cantidad de argumentos
     * según el opcode, valida el tipo de cada argumento (registro, número,
     * desplazamiento con signo, o código de interrupción), y arma el objeto
     * {@link Instruccion} resultante.
     *
     * @param linea línea de código ya recortada (trim), no vacía
     * @return la instrucción construida
     * @throws IllegalArgumentException si la línea no es válida, con un
     *         mensaje describiendo el motivo exacto
     */
    private Instruccion parsearLinea(String linea) {
        int primerEspacio = linea.indexOf(' ');
        String opcode = (primerEspacio == -1) ? linea : linea.substring(0, primerEspacio);
        opcode = opcode.toUpperCase();

        String resto = (primerEspacio == -1) ? "" : linea.substring(primerEspacio + 1).trim();

        List<String> argumentos = new ArrayList<>();
        if (!resto.isEmpty()) {
            for (String parte : resto.split(",")) {
                argumentos.add(parte.trim());
            }
        }

        if (!RANGO_ARGUMENTOS.containsKey(opcode)) {
            throw new IllegalArgumentException("opcode desconocido '" + opcode + "'");
        }

        int[] rango = RANGO_ARGUMENTOS.get(opcode);
        int minimo = rango[0];
        int maximo = rango[1];
        if (argumentos.size() < minimo || argumentos.size() > maximo) {
            throw new IllegalArgumentException(
                    "el opcode '" + opcode + "' espera entre " + minimo + " y " + maximo
                    + " argumento(s), se encontraron " + argumentos.size());
        }

        validarArgumentos(opcode, argumentos);

        return new Instruccion(opcode, argumentos);
    }

    /**
     * Valida el tipo de cada argumento según las reglas del opcode dado.
     *
     * @param opcode     opcode ya validado en cantidad de argumentos
     * @param argumentos lista de argumentos en texto, ya separados y recortados
     * @throws IllegalArgumentException si algún argumento no cumple su tipo esperado
     */
    private void validarArgumentos(String opcode, List<String> argumentos) {

        // Saltos: un solo argumento, debe ser un entero con o sin signo
        if (OPCODES_SALTO.contains(opcode)) {
            validarEntero(argumentos.get(0), "desplazamiento");
            return;
        }

        // Interrupciones: un solo argumento, debe tener formato de codigo (ej. "20H")
        if (opcode.equals("INT")) {
            String codigo = argumentos.get(0);
            if (!codigo.matches(PATRON_CODIGO_INTERRUPCION)) {
                throw new IllegalArgumentException(
                        "codigo de interrupcion invalido '" + codigo + "', se esperaba formato como '20H'");
            }
            return;
        }

        // PARAM: todos los argumentos deben ser numericos
        if (opcode.equals("PARAM")) {
            for (String arg : argumentos) {
                validarEntero(arg, "parametro");
            }
            return;
        }

        // MOV: primer argumento siempre registro; segundo puede ser registro o valor
        if (opcode.equals("MOV")) {
            validarRegistro(argumentos.get(0));
            String segundo = argumentos.get(1);
            if (!REGISTROS_VALIDOS.contains(segundo.toUpperCase())) {
                validarEntero(segundo, "valor");
            }
            return;
        }

        // Resto de opcodes con argumentos de registro (LOAD, STORE, ADD, SUB,
        // INC, DEC, SWAP, CMP, PUSH, POP): cada argumento presente debe ser registro
        if (PRIMER_ARG_REGISTRO.contains(opcode)) {
            for (String arg : argumentos) {
                validarRegistro(arg);
            }
        }
    }

    /**
     * Valida que un texto sea un nombre de registro conocido.
     *
     * @param arg texto a validar
     * @throws IllegalArgumentException si no es un registro válido
     */
    private void validarRegistro(String arg) {
        if (!REGISTROS_VALIDOS.contains(arg.toUpperCase())) {
            throw new IllegalArgumentException(
                    "registro desconocido '" + arg + "'. Validos: " + REGISTROS_VALIDOS);
        }
    }

    /**
     * Valida que un texto sea un número entero válido (con o sin signo).
     *
     * @param arg       texto a validar
     * @param descripcion nombre descriptivo del argumento, para el mensaje de error
     * @throws IllegalArgumentException si no es un entero válido
     */
    private void validarEntero(String arg, String descripcion) {
        try {
            Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "el " + descripcion + " '" + arg + "' no es un numero entero valido");
        }
    }
}