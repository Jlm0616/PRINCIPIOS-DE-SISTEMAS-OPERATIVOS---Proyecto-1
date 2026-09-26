package modelo;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Representa una instrucción ensamblador de la máquina virtual.
 *
 * A diferencia del diseño de Tarea 1 (opcode + registro + valor fijos),
 * esta versión soporta el conjunto ampliado de instrucciones del
 * Proyecto 1, cuyo número y tipo de operandos varía según el opcode:
 *
 *   - Sin argumentos:        INC, DEC
 *   - Un argumento:          LOAD AX, STORE BX, INC AX, DEC AX, PUSH AX, POP AX
 *   - Dos argumentos:        MOV BX,AX / MOV BX,5 / SWAP AX,BX / CMP Reg1,Reg2
 *   - Un desplazamiento:     JMP +3 / JMP -2 / JE +5 / JNE -1
 *   - Codigo de interrupcion: INT 20H / INT 10H / INT 09H / INT 21H
 *   - Hasta 3 parametros:    PARAM v1, v2, v3
 *
 * En lugar de campos fijos, los operandos se guardan como una lista
 * de argumentos en texto; cada opcode sabe cuantos argumentos espera
 * y como interpretarlos (esa interpretacion ocurre en EjecutorCPU,
 * no aqui).
 *
 * Esta clase solo GUARDA la instruccion ya separada; no valida ni
 * ejecuta nada (esas responsabilidades son de Ensamblador y
 * EjecutorCPU, respectivamente).
 */
public class Instruccion {

    private String opcode;              // operacion a ejecutar (ej. "MOV", "SWAP", "JMP")
    private List<String> argumentos;    // operandos en texto, en el orden en que aparecen

    /**
     * Crea una instruccion con su opcode y una lista de argumentos.
     *
     * @param opcode     nombre de la operacion (ej. "MOV", "ADD", "SWAP")
     * @param argumentos operandos en el orden en que aparecen en el codigo
     *                   fuente; puede ser una lista vacia si el opcode no
     *                   necesita argumentos (ej. "INC" a secas)
     */
    public Instruccion(String opcode, List<String> argumentos) {
        this.opcode = opcode;
        this.argumentos = (argumentos != null) ? argumentos : new ArrayList<>();
    }

    /**
     * Constructor de conveniencia para instrucciones sin argumentos.
     *
     * @param opcode nombre de la operacion (ej. "INC", "DEC")
     */
    public Instruccion(String opcode) {
        this(opcode, new ArrayList<>());
    }

    /* ==================== GETTERS BASICOS ==================== */

    public String getOpcode() {
        return opcode;
    }

    /** @return la lista de argumentos, en orden, como vista de solo lectura. */
    public List<String> getArgumentos() {
        return Collections.unmodifiableList(argumentos);
    }

    /** @return la cantidad de argumentos que tiene esta instruccion. */
    public int cantidadArgumentos() {
        return argumentos.size();
    }

    /**
     * Obtiene un argumento por posicion, como texto.
     *
     * @param indice posicion del argumento (0 = primero)
     * @return el argumento en esa posicion
     * @throws IndexOutOfBoundsException si el indice no existe
     */
    public String getArgumento(int indice) {
        return argumentos.get(indice);
    }

    /**
     * Obtiene un argumento por posicion, convertido a entero.
     * Util para valores numericos (ej. MOV BX, 5) o desplazamientos
     * con signo (ej. JMP +3, JMP -2).
     *
     * @param indice posicion del argumento (0 = primero)
     * @return el argumento en esa posicion, convertido a int
     * @throws IndexOutOfBoundsException si el indice no existe
     * @throws NumberFormatException si el argumento no es un numero valido
     */
    public int getArgumentoComoEntero(int indice) {
        return Integer.parseInt(argumentos.get(indice));
    }

    /**
     * Indica si el argumento en la posicion dada es un nombre de registro
     * conocido (AC, AX, BX, CX, DX) en lugar de un valor numerico.
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
                || arg.equals("CX") || arg.equals("DX");
    }
    
    /**
     * Obtiene el codigo de interrupcion como entero decimal.
     * Convierte "20H" -> 32, "10H" -> 16, "09H" -> 9, "21H" -> 33.
     *
     * @param indice posicion del argumento
     * @return codigo de interrupcion en decimal
     * @throws NumberFormatException si el argumento no tiene formato valido
     */
    public int getCodigoInterrupcion(int indice) {
        String codigo = argumentos.get(indice).toUpperCase();
        // Quita la 'H' final y parsea en base 16
        return Integer.parseInt(codigo.substring(0, codigo.length() - 1), 16);
    }
    
    /**
     * @return el peso (tiempo de CPU en segundos) de esta instrucción,
     *         según la tabla del enunciado del Proyecto 1.
     * @throws UnsupportedOperationException si el opcode no tiene peso definido
     */
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

    /**
     * @return el peso específico de la interrupción según su código.
     */
    private int pesoDeINT() {
        int codigo = getCodigoInterrupcion(0);
        switch (codigo) {
            case 0x20: return 2;   // INT 20H -> fin del programa
            case 0x10: return 2;   // INT 10H -> imprimir DX
            case 0x09: return 3;   // INT 09H -> leer teclado
            case 0x21: return 5;   // INT 21H -> manejo de archivos
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