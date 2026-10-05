package modelo;

/**
 * Representa la unidad central de procesamiento (CPU) de la máquina virtual.
 *
 * Contiene los registros principales usados durante la ejecución de instrucciones:
 * PC (contador de programa), IR (registro de instrucción) y los registros
 * de propósito general AC, AX, BX, CX y DX.
 *
 * IMPORTANTE: DX es un String, no un int. Esto es porque el enunciado indica
 * que en INT 21H, DX guarda la cadena de texto del nombre del archivo.
 * Para compatibilidad con instrucciones aritmeticas (ADD, SUB, CMP, etc.),
 * se proveen metodos auxiliares getDXAsInt() y setDXAsInt().
 *
 * También incluye dos banderas internas de hardware:
 *   - overflow:      true si la última operación aritmética desbordó el rango.
 *   - banderaIgual:  resultado de la última instrucción CMP, usado por JE/JNE.
 *
 * Los registros AX, BX, CX se usan como operandos numericos.
 * DX se usa como string (nombre de archivo) o como numero (compatibilidad).
 * AC (acumulador) suele almacenar el resultado de operaciones.
 */
public class CPU {

    private int PC;      // contador de programa: dirección de la próxima instrucción
    private int IR;      // registro de instrucción: instrucción en ejecución
    private int AC;      // acumulador: resultados de operaciones aritméticas/lógicas
    private int AX;      // registro de propósito general
    private int BX;      // registro de propósito general
    private int CX;      // registro de propósito general
    private String DX;   // registro de propósito general (string para nombres de archivo)

    private boolean overflow;      // true si la última operación aritmética desbordó
    private boolean banderaIgual;  // resultado de la última comparación (CMP)

    /**
     * Crea una CPU con todos los registros inicializados en 0,
     * excepto el PC, que arranca en el límite entre kernel y usuario.
     * DX se inicializa como "0" (string).
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
        this.DX = "0";   // ← String, no int
        this.overflow = false;
        this.banderaIgual = false;
    }

    /* ==================== GETTERS ==================== */

    public int getPC() {
        return PC;
    }

    public int getIR() {
        return IR;
    }

    public int getAC() {
        return AC;
    }

    public int getAX() {
        return AX;
    }

    public int getBX() {
        return BX;
    }

    public int getCX() {
        return CX;
    }

    /**
     * Devuelve DX como String.
     * Puede contener un nombre de archivo (ej. "datos.txt") o un numero
     * en texto (ej. "5").
     */
    public String getDX() {
        return DX;
    }

    /**
     * Devuelve DX como entero, parseando el string.
     * Si DX no es numerico, devuelve 0.
     * Se usa para compatibilidad con instrucciones aritmeticas.
     */
    public int getDXAsInt() {
        try {
            return Integer.parseInt(DX);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean getOverflow() {
        return overflow;
    }

    public boolean getBanderaIgual() {
        return banderaIgual;
    }

    /* ==================== SETTERS ==================== */

    public void setPC(int PC) {
        this.PC = PC;
    }

    public void setIR(int IR) {
        this.IR = IR;
    }

    public void setAC(int AC) {
        this.AC = AC;
    }

    public void setAX(int AX) {
        this.AX = AX;
    }

    public void setBX(int BX) {
        this.BX = BX;
    }

    public void setCX(int CX) {
        this.CX = CX;
    }

    /**
     * Asigna DX como String.
     * @param dx valor a asignar (si es null, se asigna "0")
     */
    public void setDX(String dx) {
        this.DX = (dx != null) ? dx : "0";
    }

    /**
     * Asigna DX como entero, convirtiendolo a String.
     * Se usa para compatibilidad con instrucciones aritmeticas.
     */
    public void setDXAsInt(int dx) {
        this.DX = String.valueOf(dx);
    }

    public void setOverflow(boolean overflow) {
        this.overflow = overflow;
    }

    public void setBanderaIgual(boolean banderaIgual) {
        this.banderaIgual = banderaIgual;
    }
}