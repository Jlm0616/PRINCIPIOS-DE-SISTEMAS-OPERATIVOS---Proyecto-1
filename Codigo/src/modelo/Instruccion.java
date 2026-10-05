package modelo;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Representa una instrucción ensamblador de la máquina virtual.
 *
 * ... (javadoc igual)
 *
 * Los argumentos numéricos pueden estar en decimal ("5", "-3") o en
 * hexadecimal ("3Ch", "40H"). El sufijo 'h' o 'H' indica hexadecimal.
 */
public class Instruccion {

    private String opcode;
    private List<String> argumentos;

    public Instruccion(String opcode, List<String> argumentos) {
        this.opcode = opcode;
        this.argumentos = (argumentos != null) ? argumentos : new ArrayList<>();
    }

    public Instruccion(String opcode) {
        this(opcode, new ArrayList<>());
    }

    /* ==================== GETTERS BASICOS ==================== */

    public String getOpcode() {
        return opcode;
    }

    public List<String> getArgumentos() {
        return Collections.unmodifiableList(argumentos);
    }

    public int cantidadArgumentos() {
        return argumentos.size();
    }

    public String getArgumento(int indice) {
        return argumentos.get(indice);
    }

    /**
     * Obtiene un argumento por posicion, convertido a entero.
     *
     * Acepta:
     *   - Decimal: "5", "10", "-3".
     *   - Hexadecimal: "3Ch", "40H", "0A0h".
     *
     * @param indice posicion del argumento (0 = primero)
     * @return el argumento en esa posicion, convertido a int
     * @throws IndexOutOfBoundsException si el indice no existe
     * @throws NumberFormatException si el argumento no es un numero valido
     */
    public int getArgumentoComoEntero(int indice) {
        return parsearNumero(argumentos.get(indice));
    }

    /**
     * Parsea un numero que puede estar en decimal o hexadecimal.
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
            String sinH = s.substring(0, s.length() - 1);
            return Integer.parseInt(sinH, 16);
        } else {
            return Integer.parseInt(s);
        }
    }

    /**
     * Indica si el argumento en la posicion dada es un nombre de registro
     * conocido (AC, AX, BX, CX, DX, AH, AL) en lugar de un valor numerico.
     *
     * @param indice posicion del argumento a revisar
     * @return true si el argumento es un nombre de registro
     */
    public boolean esRegistro(int indice) {
        if (indice < 0 || indice >= argumentos.size()) {
            return false;
        }
        String arg = argumentos.get(indice).toUpperCase();
        return arg.equals("AC") || arg.equals("AX") || arg.equals("BX")
                || arg.equals("CX") || arg.equals("DX")
                || arg.equals("AH") || arg.equals("AL");
    }

    public int getCodigoInterrupcion(int indice) {
        String codigo = argumentos.get(indice).toUpperCase();
        return Integer.parseInt(codigo.substring(0, codigo.length() - 1), 16);
    }

    /* ==================== PESO ==================== */

    public int getPeso() {
        switch (opcode) {
            case "LOAD":  return 2;
            case "STORE": return 2;
            case "MOV":   return 1;
            case "ADD":   return 3;
            case "SUB":   return 3;
            case "INC":   return 1;
            case "DEC":   return 1;
            case "SWAP":  return 1;
            case "JMP":   return 2;
            case "CMP":   return 2;
            case "JE":    return 2;
            case "JNE":   return 2;
            case "PARAM": return 3;
            case "PUSH":  return 1;
            case "POP":   return 1;
            case "INT":   return pesoDeINT();
            default:
                throw new UnsupportedOperationException(
                    "Opcode sin peso definido: " + opcode);
        }
    }

    private int pesoDeINT() {
        int codigo = getCodigoInterrupcion(0);
        switch (codigo) {
            case 0x20: return 2;
            case 0x10: return 2;
            case 0x09: return 3;
            case 0x21: return 5;
            default:
                throw new UnsupportedOperationException(
                    "INT sin peso definido: " + Integer.toHexString(codigo) + "H");
        }
    }

    @Override
    public String toString() {
        if (argumentos.isEmpty()) {
            return opcode;
        }
        return opcode + " " + String.join(", ", argumentos);
    }
}