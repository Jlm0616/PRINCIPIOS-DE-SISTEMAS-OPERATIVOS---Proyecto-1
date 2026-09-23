package logica;

import modelo.Instruccion;
import java.util.Map;
import java.util.HashMap;

/**
 * Traductor entre las instrucciones ensamblador y su representación binaria.
 *
 * Se encarga de codificar y decodificar los tres componentes de una instrucción:
 *   - opcode:   nombre de la operación   4 bits
 *   - registro: nombre del registro      4 bits
 *   - valor:    número entero            8 bits en signo-magnitud
 *
 * Los mapeos opcode/registro son fijos y se cargan en el constructor.
 *
 * Formato del PRIMER byte (8 bits):  [ opcode (4) | registro (4) ]
 * Formato del SEGUNDO byte (8 bits): [ signo (1) | magnitud (7) ]
 *   - signo: 0 = positivo, 1 = negativo
 *   - magnitud: valor absoluto en binario
 *
 * Formato de la INSTRUCCIÓN COMPLETA (16 bits):
 *   [ opcode (4) | registro (4) | signo (1) | magnitud (7) ]
 */
public class Traductor {

    private Map<String, String> mapOpcode;    // opcode  -> código binario (4 bits)
    private Map<String, String> mapRegistro;  // registro -> código binario (4 bits)

    /**
     * Crea el traductor con los mapeos fijos de opcodes y registros.
     *
     * Opcodes:   LOAD=0001, STORE=0010, MOV=0011, SUB=0100, ADD=0101
     * Registros: AX=0001, BX=0010, CX=0011, DX=0100
     */
    public Traductor() {
        mapOpcode = new HashMap<>();
        mapOpcode.put("LOAD",  "0001");
        mapOpcode.put("STORE", "0010");
        mapOpcode.put("MOV",   "0011");
        mapOpcode.put("SUB",   "0100");
        mapOpcode.put("ADD",   "0101");

        mapRegistro = new HashMap<>();
        mapRegistro.put("AX", "0001");
        mapRegistro.put("BX", "0010");
        mapRegistro.put("CX", "0011");
        mapRegistro.put("DX", "0100");
    }

    /**
     * Codifica un valor entero como byte en formato signo-magnitud (8 bits).
     *
     * Estructura: [signo (1 bit)] + [magnitud (7 bits)]
     *   - Si el valor es negativo, el signo es '1'.
     *   - La magnitud se rellena con ceros a la izquierda hasta 7 bits.
     *
     * @param valor número a codificar (positivo o negativo)
     * @return string binario de 8 bits
     */
    public String valorEnsamblador(int valor) {
        if (valor < -127 || valor > 127) {
            throw new IllegalArgumentException("Valor fuera de rango [-127, 127]: " + valor);
        }
        
        String binario;
        String signo;
        if (valor < 0) {
            int valorAbsoluto = Math.abs(valor);
            signo = "1";
            binario = Integer.toBinaryString(valorAbsoluto);
        }
        else {
            signo = "0";
            binario = Integer.toBinaryString(valor);
        }

        // Relleno a la izquierda hasta 7 bits (la magnitud ocupa 7 bits)
        String relleno = String.format("%7s", binario).replace(' ', '0');
        relleno = signo + relleno;
        return relleno;
    }

    /**
     * Construye el primer byte de una instrucción.
     *
     * Concatena el código de opcode (4 bits) con el código de registro (4 bits).
     *
     * @param instruccion instrucción a codificar
     * @return string binario de 8 bits (opcode + registro)
     */
    public String primerByte(Instruccion instruccion) {
        String codigoOpcode   = mapOpcode.get(instruccion.getOpcode());
        String codigoRegistro = mapRegistro.get(instruccion.getRegistro());
        String codigo = codigoOpcode + codigoRegistro;

        return codigo;
    }

    /**
     * Codifica una instrucción completa en 16 bits.
     *
     * Formato: [ opcode (4) | registro (4) | signo (1) | magnitud (7) ]
     *
     * Es la concatenación del primer byte (opcode + registro) con el
     * segundo byte (valor en signo-magnitud).
     *
     * Esta representación es la que se guarda en UNA posición de memoria
     * (cada línea del archivo .asm ocupa una sola posición).
     *
     * @param instruccion instrucción a codificar
     * @return string binario de 16 bits
     */
    public String instruccionCompleta(Instruccion instruccion) {
        String primerByte  = primerByte(instruccion);                    // 8 bits
        String segundoByte = valorEnsamblador(instruccion.getValor());   // 8 bits
        return primerByte + segundoByte;                                  // 16 bits
    }

    /**
     * Decodifica un código binario de opcode a su nombre.
     *
     * @param codigo binario de 4 bits
     * @return nombre del opcode ("LOAD", "MOV", ...) o null si no coincide
     */
    public String decodificarOpcode(String codigo) {
        for (Map.Entry<String, String> entrada : mapOpcode.entrySet()) {
            if (entrada.getValue().equals(codigo)) {
                return entrada.getKey();
            }
        }
        return null;
    }

    /**
     * Decodifica un código binario de registro a su nombre.
     *
     * @param codigo binario de 4 bits
     * @return nombre del registro ("AX", "BX", ...) o null si no coincide
     */
    public String decodificarRegistro(String codigo) {
        for (Map.Entry<String, String> entrada : mapRegistro.entrySet()) {
            if (entrada.getValue().equals(codigo)) {
                return entrada.getKey();
            }
        }
        return null;
    }

    /**
     * Decodifica un byte en formato signo-magnitud a su valor entero.
     *
     * @param byteValor string binario de 8 bits (signo + magnitud de 7 bits)
     * @return valor entero (positivo o negativo)
     */
    public int decodificarValor(String byteValor) {
        char signo = byteValor.charAt(0);
        String magnitudBinaria = byteValor.substring(1, 8);
        int magnitud = Integer.parseInt(magnitudBinaria, 2);
        return signo == '1' ? -magnitud : magnitud;
    }
}