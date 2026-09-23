package modelo;

/**
 * Representa la unidad central de procesamiento (CPU) de la máquina virtual.
 *
 * Contiene los registros principales usados durante la ejecución de instrucciones:
 * PC (contador de programa), IR (registro de instrucción) y los registros
 * de propósito general AC, AX, BX, CX y DX.
 *
 * Los registros AX, BX, CX y DX se usan como operandos en las instrucciones,
 * mientras que AC (acumulador) suele almacenar el resultado de operaciones.
 */
public class CPU {

    private int PC;   // contador de programa: dirección de la próxima instrucción
    private int IR;   // registro de instrucción: instrucción en ejecución
    private int AC;   // acumulador: resultados de operaciones aritméticas/lógicas
    private int AX;   // registro de propósito general
    private int BX;   // registro de propósito general
    private int CX;   // registro de propósito general
    private int DX;   // registro de propósito general
    private boolean overflow;   // bandera de overflow: true si la última operación desbordó

    /**
     * Crea una CPU con todos los registros inicializados en 0,
     * excepto el PC, que arranca en el límite entre kernel y usuario.
     *
     * @param limiteKernelUsuario dirección inicial del PC (fin de la zona kernel)
     */
    public CPU(int limiteKernelUsuario) {
        this.PC = limiteKernelUsuario;
        this.IR = 0;
        this.AC = 0;
        this.AX = 0;
        this.BX = 0;
        this.CX = 0;
        this.DX = 0;
        this.overflow = false;
    }

    /* ==================== GETTERS ==================== */

    public int getPC() { return PC; }
    public int getIR() { return IR; }
    public int getAC() { return AC; }
    public int getAX() { return AX; }
    public int getBX() { return BX; }
    public int getCX() { return CX; }
    public int getDX() { return DX; }
    public boolean getOverflow() { return overflow; }

    /* ==================== SETTERS ==================== */

    public void setPC(int PC) { this.PC = PC; }
    public void setIR(int IR) { this.IR = IR; }
    public void setAC(int AC) { this.AC = AC; }
    public void setAX(int AX) { this.AX = AX; }
    public void setBX(int BX) { this.BX = BX; }
    public void setCX(int CX) { this.CX = CX; }
    public void setDX(int DX) { this.DX = DX; }
    public void setOverflow(boolean overflow) { this.overflow = overflow; }
}