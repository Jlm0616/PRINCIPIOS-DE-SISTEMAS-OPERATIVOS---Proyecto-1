package modelo;

/**
 * Representa una instrucción ensamblador de la máquina virtual.
 *
 * Cada instrucción se compone de tres partes:
 *   - opcode:   operación a realizar (ej. "MOV", "ADD", "LOAD")
 *   - registro: registro sobre el que actúa (ej. "AX", "BX", "AC")
 *   - valor:    operando numérico (constante o dirección)
 *
 * Ejemplo: new Instruccion("MOV", "AX", 5)  ->  AX = 5
 *
 */
public class Instruccion {

    private String opcode;    // operación a ejecutar
    private String registro;  // registro destino o fuente
    private int valor;        // operando numérico

    /**
     * Crea una instrucción con sus tres componentes.
     *
     * @param opcode
     * @param registro
     * @param valor
     */
    public Instruccion(String opcode, String registro, int valor) {
        this.opcode = opcode;
        this.registro = registro;
        this.valor = valor;
    }

    /* ==================== GETTERS ==================== */

    public String getOpcode() { return opcode; }
    public String getRegistro() { return registro; }
    public int getValor() { return valor; }
}