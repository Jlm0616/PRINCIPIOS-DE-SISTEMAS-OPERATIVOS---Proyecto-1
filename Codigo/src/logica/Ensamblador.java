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
 *   MOV AH, 3Ch      ; 3C en hexadecimal = 60 en decimal
 *   MOV AL, 40h
 *   MOV DX, "datos.txt"   ; string literal para INT 21H
 *   SWAP AX, BX
 *   JMP +3
 *   INT 20H
 *   CMP AX, BX
 *   PARAM 5, 10, 3
 *   PUSH AX
 *
 * Los números pueden estar en decimal ("5", "10") o en hexadecimal
 * ("3Ch", "40H"). El sufijo 'h' o 'H' indica hexadecimal.
 *
 * Los strings van entre comillas dobles y solo son válidos como segundo
 * argumento de MOV (ej. MOV DX, "datos.txt").
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
            Arrays.asList("AC", "AX", "BX", "CX", "DX", "AH", "AL"));

    /** Patrón esperado para códigos de interrupción, ej. "20H", "09H". */
    private static final String PATRON_CODIGO_INTERRUPCION = "^[0-9A-Fa-f]{2}[Hh]$";

    private List<String> erroresEncontrados;

    /* ==================== VALIDACIÓN DE ARCHIVO ==================== */

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

    public String getErroresComoTexto() {
        if (erroresEncontrados == null || erroresEncontrados.isEmpty()) {
            return "";
        }
        return String.join("\n\n", erroresEncontrados);
    }

    public List<String> getErroresEncontrados() {
        return erroresEncontrados;
    }

    /* ==================== LECTURA DE ARCHIVO ==================== */

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

    /* ==================== PARSEO CENTRAL ==================== */

    /**
     * Analiza una línea de código y construye la instrucción correspondiente.
     *
     * El parseo de argumentos respeta strings entre comillas: las comas
     * dentro de un string NO se consideran separadores.
     */
    private Instruccion parsearLinea(String linea) {
        int primerEspacio = linea.indexOf(' ');
        String opcode = (primerEspacio == -1) ? linea : linea.substring(0, primerEspacio);
        opcode = opcode.toUpperCase();

        String resto = (primerEspacio == -1) ? "" : linea.substring(primerEspacio + 1).trim();

        List<String> argumentos = parsearArgumentos(resto);

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
     * Parsea la lista de argumentos separando por comas, pero respetando
     * strings entre comillas dobles.
     *
     * Ejemplos:
     *   "BX, AX"            → ["BX", "AX"]
     *   "DX, \"datos.txt\"" → ["DX", "\"datos.txt\""]
     *   "DX, \"a,b.txt\""   → ["DX", "\"a,b.txt\""]   (la coma NO separa)
     *
     * @param resto texto de argumentos sin el opcode
     * @return lista de argumentos
     */
    private List<String> parsearArgumentos(String resto) {
        List<String> argumentos = new ArrayList<>();
        if (resto == null || resto.isEmpty()) {
            return argumentos;
        }

        StringBuilder actual = new StringBuilder();
        boolean dentroComillas = false;

        for (int i = 0; i < resto.length(); i++) {
            char c = resto.charAt(i);

            if (c == '"') {
                dentroComillas = !dentroComillas;
                actual.append(c);
            } else if (c == ',' && !dentroComillas) {
                argumentos.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }

        if (actual.length() > 0) {
            argumentos.add(actual.toString().trim());
        }

        return argumentos;
    }

    /**
     * Valida el tipo de cada argumento según las reglas del opcode dado.
     */
    private void validarArgumentos(String opcode, List<String> argumentos) {

        // Saltos: un solo argumento, debe ser un entero con o sin signo
        if (OPCODES_SALTO.contains(opcode)) {
            validarEntero(argumentos.get(0), "desplazamiento");
            return;
        }

        // Interrupciones: un solo argumento, formato de codigo (ej. "20H")
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

        // MOV: primer argumento siempre registro; segundo puede ser:
        //   - registro
        //   - numero
        //   - string literal (entre comillas)
        if (opcode.equals("MOV")) {
            validarRegistro(argumentos.get(0));
            String segundo = argumentos.get(1);

            if (REGISTROS_VALIDOS.contains(segundo.toUpperCase())) {
                return;   // es un registro
            }

            if (esStringLiteral(segundo)) {
                return;   // es un string literal
            }

            validarEntero(segundo, "valor");
            return;
        }

        // Resto de opcodes con argumentos de registro
        if (PRIMER_ARG_REGISTRO.contains(opcode)) {
            for (String arg : argumentos) {
                validarRegistro(arg);
            }
        }
    }

    /**
     * Verifica si un argumento es un string literal (entre comillas dobles).
     */
    private boolean esStringLiteral(String arg) {
        if (arg == null) return false;
        String s = arg.trim();
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"");
    }

    /**
     * Valida que un texto sea un nombre de registro conocido.
     */
    private void validarRegistro(String arg) {
        if (!REGISTROS_VALIDOS.contains(arg.toUpperCase())) {
            throw new IllegalArgumentException(
                    "registro desconocido '" + arg + "'. Validos: " + REGISTROS_VALIDOS);
        }
    }

    /**
     * Valida que un texto sea un número entero válido.
     * Acepta decimal ("5") o hexadecimal ("3Ch").
     */
    private void validarEntero(String arg, String descripcion) {
        try {
            parsearNumero(arg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "el " + descripcion + " '" + arg + "' no es un numero entero valido");
        }
    }

    /* ==================== HELPERS DE NUMEROS ==================== */

    /**
     * Parsea un numero que puede estar en decimal o hexadecimal.
     *
     * Formato:
     *   - Hexadecimal: termina en 'h' o 'H' (ej. "3Ch", "40H", "0A0h").
     *   - Decimal: sin sufijo (ej. "5", "10", "-3").
     *
     * @param arg numero en texto
     * @return el valor entero
     * @throws NumberFormatException si no es un numero valido
     */
    public static int parsearNumero(String arg) {
        if (arg == null || arg.isEmpty()) {
            throw new NumberFormatException("numero vacio");
        }
        String s = arg.trim();
        if (s.toLowerCase().endsWith("h")) {
            // Hexadecimal
            String sinH = s.substring(0, s.length() - 1);
            return Integer.parseInt(sinH, 16);
        } else {
            // Decimal
            return Integer.parseInt(s);
        }
    }
}