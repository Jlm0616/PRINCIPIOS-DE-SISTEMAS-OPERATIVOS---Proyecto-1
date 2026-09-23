package modelo;

import soporte.CodificadorBinario;

/**
 * Bloque de Control de Proceso (BCP).
 *
 * A diferencia de un diseño con atributos privados, esta clase NO
 * guarda ningún dato dentro de sí misma. Todos los valores del BCP
 * (ID, Estado, PC, AC, AX, BX, CX, DX) viven físicamente en posiciones
 * fijas de la zona de Kernel del arreglo de {@link Memoria}.
 *
 * Esto refleja el comportamiento real de un sistema operativo: el BCP
 * de un proceso es información que vive en RAM, para poder consultarla
 * o restaurarla aunque el proceso no esté actualmente en la CPU.
 *
 * Posiciones fijas usadas dentro de la zona de Kernel:
 *   0 = ID
 *   1 = Estado
 *   2 = PC
 *   3 = AC
 *   4 = AX
 *   5 = BX
 *   6 = CX
 *   7 = DX
 *
 * Todos los campos se guardan en BINARIO:
 *   - ID:     8 bits sin signo  (0 a 255)
 *   - Estado: 2 bits (00=NUEVO, 01=EJECUTANDO, 10=TERMINADO, 11=reservado)
 *   - PC:     16 bits sin signo (0 a 65535)
 *   - AC/AX/BX/CX/DX: 16 bits con signo (complemento a 2)
 *
 * (El IR no se guarda en el BCP: representa la instrucción en curso
 * en este preciso ciclo, no un dato necesario para reanudar el proceso).
 */
public class BCP {

    /* ==================== POSICIONES FIJAS EN KERNEL ==================== */

    private static final int POS_ID     = 0;  // identificador del proceso
    private static final int POS_ESTADO = 1;  // estado actual
    private static final int POS_PC     = 2;  // contador de programa
    private static final int POS_AC     = 3;  // acumulador
    private static final int POS_AX     = 4;  // registro AX
    private static final int POS_BX     = 5;  // registro BX
    private static final int POS_CX     = 6;  // registro CX
    private static final int POS_DX     = 7;  // registro DX
    private static final int POS_FLAGS  = 8;   // ← NUEVO: bandera de overflow

    /** Cantidad de posiciones de Kernel que este BCP necesita. */
    public static final int POSICIONES_REQUERIDAS = 9;

    /* ==================== CÓDIGOS DE ESTADO (2 bits) ==================== */

    private static final String ESTADO_NUEVO      = "00";
    private static final String ESTADO_EJECUTANDO = "01";
    private static final String ESTADO_TERMINADO  = "10";

    /* ==================== REFERENCIA A MEMORIA ==================== */

    private final Memoria memoria;   // memoria sobre la que vive este BCP

    /**
     * Crea un BCP que opera sobre las primeras posiciones de la zona
     * de Kernel de la Memoria dada, inicializándolas con los valores
     * de arranque de un proceso nuevo.
     *
     * @param memoria memoria sobre la cual el BCP lee y escribe
     * @param id      identificador del proceso
     */
    public BCP(Memoria memoria, int id) {
        this.memoria = memoria;
        memoria.escribir(POS_ID,     CodificadorBinario.aBinario(id, 8));
        memoria.escribir(POS_ESTADO, ESTADO_NUEVO);
        memoria.escribir(POS_PC,     CodificadorBinario.aBinario(0, 16));
        memoria.escribir(POS_AC,     CodificadorBinario.aBinario(0, 16));
        memoria.escribir(POS_AX,     CodificadorBinario.aBinario(0, 16));
        memoria.escribir(POS_BX,     CodificadorBinario.aBinario(0, 16));
        memoria.escribir(POS_CX,     CodificadorBinario.aBinario(0, 16));
        memoria.escribir(POS_DX,     CodificadorBinario.aBinario(0, 16));
        memoria.escribir(POS_FLAGS,  "0");   // ← NUEVO: sin overflow al inicio
    }

    /* ==================== ESCRITURA ==================== */

    /**
     * Copia el estado actual de una CPU hacia las posiciones del BCP
     * en Memoria. Debe llamarse después de cada instrucción ejecutada.
     *
     * @param cpu    CPU cuyos registros se van a copiar
     * @param estado nuevo estado del proceso ("NUEVO", "EJECUTANDO", "TERMINADO")
     */
    public void actualizarDesdeCPU(CPU cpu, String estado) {
        memoria.escribir(POS_ESTADO, codificarEstado(estado));
        memoria.escribir(POS_PC,     CodificadorBinario.aBinario(cpu.getPC(), 16));
        memoria.escribir(POS_AC,     CodificadorBinario.aBinario(cpu.getAC(), 16));
        memoria.escribir(POS_AX,     CodificadorBinario.aBinario(cpu.getAX(), 16));
        memoria.escribir(POS_BX,     CodificadorBinario.aBinario(cpu.getBX(), 16));
        memoria.escribir(POS_CX,     CodificadorBinario.aBinario(cpu.getCX(), 16));
        memoria.escribir(POS_DX,     CodificadorBinario.aBinario(cpu.getDX(), 16));
        memoria.escribir(POS_FLAGS,  cpu.getOverflow() ? "1" : "0");   // ← NUEVO
    }

    /**
     * Cambia solo el estado del proceso, sin tocar los demás valores.
     *
     * @param estado nuevo estado ("NUEVO", "EJECUTANDO", "TERMINADO")
     */
    public void setEstado(String estado) {
        memoria.escribir(POS_ESTADO, codificarEstado(estado));
    }

    /* ==================== GETTERS ==================== */

    /** @return el identificador del proceso. */
    public int getId() {
        return CodificadorBinario.desdeBinario(memoria.leer(POS_ID));
    }

    /** @return el estado actual del proceso como texto ("NUEVO", ...). */
    public String getEstado() {
        return decodificarEstado(memoria.leer(POS_ESTADO));
    }

    /** @return el contador de programa guardado en el BCP. */
    public int getPc() {
        return CodificadorBinario.desdeBinario(memoria.leer(POS_PC));
    }

    /** @return el acumulador guardado en el BCP (con signo). */
    public int getAc() {
        return CodificadorBinario.desdeBinarioConSigno(memoria.leer(POS_AC));
    }

    /** @return el registro AX guardado en el BCP (con signo). */
    public int getAx() {
        return CodificadorBinario.desdeBinarioConSigno(memoria.leer(POS_AX));
    }

    /** @return el registro BX guardado en el BCP (con signo). */
    public int getBx() {
        return CodificadorBinario.desdeBinarioConSigno(memoria.leer(POS_BX));
    }

    /** @return el registro CX guardado en el BCP (con signo). */
    public int getCx() {
        return CodificadorBinario.desdeBinarioConSigno(memoria.leer(POS_CX));
    }

    /** @return el registro DX guardado en el BCP (con signo). */
    public int getDx() {
        return CodificadorBinario.desdeBinarioConSigno(memoria.leer(POS_DX));
    }

    /** @return true si la bandera de overflow está activa. */
    public boolean getOverflow() {
        return "1".equals(memoria.leer(POS_FLAGS));
    }

    /* ==================== MÉTODOS AUXILIARES DE ESTADO ==================== */

    /**
     * Mapea el texto de estado a su código binario de 2 bits.
     *
     * @param estado "NUEVO", "EJECUTANDO" o "TERMINADO"
     * @return código binario de 2 bits
     */
    private String codificarEstado(String estado) {
        switch (estado) {
            case "NUEVO":      return ESTADO_NUEVO;
            case "EJECUTANDO": return ESTADO_EJECUTANDO;
            case "TERMINADO":  return ESTADO_TERMINADO;
            default:           return ESTADO_NUEVO;
        }
    }

    /**
     * Decodifica un código binario de 2 bits a su texto de estado.
     *
     * @param codigo "00", "01", "10" o "11"
     * @return texto del estado
     */
    private String decodificarEstado(String codigo) {
        switch (codigo) {
            case ESTADO_NUEVO:      return "NUEVO";
            case ESTADO_EJECUTANDO: return "EJECUTANDO";
            case ESTADO_TERMINADO:  return "TERMINADO";
            default:                return "DESCONOCIDO";
        }
    }
}