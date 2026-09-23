package logica;

import modelo.CPU;
import modelo.Memoria;
import modelo.BCP;

/**
 * Ejecutor del ciclo de instrucción de la CPU (fetch-decode-execute).
 *
 * Se encarga de:
 *   1. Leer UNA posición de memoria a partir del PC (16 bits).
 *   2. Decodificar el primer byte en opcode + registro (4 bits cada uno).
 *   3. Decodificar el segundo byte como valor numérico.
 *   4. Ejecutar la operación sobre los registros de la CPU.
 *   5. Avanzar el PC en 1 posición.
 *   6. Sincronizar el estado de la CPU con el BCP (que vive en la RAM).
 *
 * Formato de la instrucción completa (16 bits):
 *   [ opcode (4) | registro (4) | signo (1) | magnitud (7) ]
 *     bits 0-3     bits 4-7      bit 8      bits 9-15
 *
 * El segundo byte contiene el operando (valor o dirección) en binario.
 */
public class EjecutorCPU {

    private CPU cpu;              // CPU sobre la que se ejecutan las instrucciones
    private Memoria memoria;      // memoria de donde se leen las instrucciones
    private BCP bcp;              // BCP del proceso en ejecución (vive en la RAM)
    private Traductor traductor;  // decodifica bytes a opcode/registro/valor

    /**
     * Crea un ejecutor asociado a una CPU y una memoria.
     *
     * Inicializa un BCP con id 1, cuyas posiciones viven en la zona
     * de Kernel de la memoria recibida. También crea un Traductor
     * por defecto.
     *
     * @param cpu     CPU sobre la que se ejecutará
     * @param memoria memoria desde la que se leerán las instrucciones
     *                y donde vivirá el BCP del proceso
     */
    public EjecutorCPU(CPU cpu, Memoria memoria) {
        this.cpu = cpu;
        this.memoria = memoria;
        this.bcp = new BCP(memoria, 1);
        this.traductor = new Traductor();
    }

    /**
     * Ejecuta una instrucción completa (fetch-decode-execute).
     *
     * Pasos:
     *   1. Lee UNA posición de memoria a partir del PC (16 bits).
     *   2. La guarda en el IR.
     *   3. Del primer byte extrae opcode (bits 0-3) y registro (bits 4-7).
     *   4. Del segundo byte extrae el valor numérico.
     *   5. Ejecuta la operación correspondiente.
     *   6. Avanza el PC en 1 posición.
     *   7. Copia el estado de la CPU al BCP (en RAM) y lo marca
     *      como "EJECUTANDO".
     */
    public void ejecutarInstruccion() {
        int pc = cpu.getPC();

        // 1. Leer UNA posición: la instrucción completa (16 bits)
        String instruccionCompleta = memoria.leer(pc);

        // 2. Guardar la instrucción completa en IR
        cpu.setIR(Integer.parseInt(instruccionCompleta, 2));

        // 3. Separar en dos bytes (opcode+registro | valor)
        String primerByte  = instruccionCompleta.substring(0, 8);
        String segundoByte = instruccionCompleta.substring(8, 16);

        // 4. Decodificación: opcode (4 bits) + registro (4 bits)
        String codigoOpcode   = primerByte.substring(0, 4);
        String codigoRegistro = primerByte.substring(4, 8);
        int valor = traductor.decodificarValor(segundoByte);

        String opcode   = traductor.decodificarOpcode(codigoOpcode);
        String registro = traductor.decodificarRegistro(codigoRegistro);

        // 5. Ejecutar la operación
        ejecutarOperacion(opcode, registro, valor);

        // 6. Avanzar el PC en 1 posición
        cpu.setPC(pc + 1);

        /* -------- Sincronización con el BCP (en RAM) -------- */
        bcp.actualizarDesdeCPU(cpu, "EJECUTANDO");
    }

    /**
     * Ejecuta una secuencia de instrucciones consecutivas.
     *
     * Al terminar, marca el BCP como "TERMINADO".
     *
     * @param cantidadInstrucciones número de instrucciones a ejecutar
     */
    public void ejecutarPrograma(int cantidadInstrucciones) {
        for (int i = 0; i < cantidadInstrucciones; i++) {
            ejecutarInstruccion();
        }

        bcp.setEstado("TERMINADO");
    }
    
    /**
     * Trunca un valor a 16 bits con signo (complemento a 2).
     * Simula el comportamiento de un registro real de 16 bits.
     */
    private int limitarA16Bits(int valor) {
        int v = valor & 0xFFFF;
        return (v >= 32768) ? v - 65536 : v;
    }

    /**
     * Ejecuta la operación indicada por el opcode sobre el registro y valor dados.
     *
     * Operaciones soportadas:
     *   MOV   -> registro = valor
     *   LOAD  -> AC = registro
     *   STORE -> registro = AC
     *   ADD   -> AC = AC + registro
     *   SUB   -> AC = AC - registro
     *
     * Si el opcode no coincide con ninguna operación, no hace nada.
     *
     * @param opcode   nombre de la operación
     * @param registro registro sobre el que actúa
     * @param valor    operando numérico (usado solo por MOV)
     */
    private void ejecutarOperacion(String opcode, String registro, int valor) {
        switch (opcode) {
            case "MOV":
                escribirRegistro(registro, valor);
                break;   // NO resetear overflow (sticky)
            case "LOAD":
                cpu.setAC(leerRegistro(registro));
                break;   // NO resetear overflow (sticky)
            case "STORE":
                escribirRegistro(registro, cpu.getAC());
                break;   // NO resetear overflow (sticky)
            case "ADD":
                int suma = cpu.getAC() + leerRegistro(registro);
                if (suma > 32767 || suma < -32768) {
                    cpu.setOverflow(true);   // solo activa, nunca resetea
                }
                cpu.setAC(limitarA16Bits(suma));
                break;
            case "SUB":
                int resta = cpu.getAC() - leerRegistro(registro);
                if (resta > 32767 || resta < -32768) {
                    cpu.setOverflow(true);
                }
                cpu.setAC(limitarA16Bits(resta));
                break;
        }
    }

    /**
     * Lee el valor de un registro de propósito general de la CPU.
     *
     * @param registro nombre del registro ("AX", "BX", "CX", "DX")
     * @return el valor del registro, o 0 si el nombre no coincide
     */
    private int leerRegistro(String registro) {
        switch (registro) {
            case "AX": return cpu.getAX();
            case "BX": return cpu.getBX();
            case "CX": return cpu.getCX();
            case "DX": return cpu.getDX();
        }
        return 0;
    }

    /**
     * Escribe un valor en un registro de propósito general de la CPU.
     *
     * Si el nombre del registro no coincide con ninguno válido, no hace nada.
     *
     * @param registro nombre del registro ("AX", "BX", "CX", "DX")
     * @param valor    valor a escribir
     */
    private void escribirRegistro(String registro, int valor) {
        int valorLimitado = limitarA16Bits(valor);
        switch (registro) {
            case "AX": cpu.setAX(valorLimitado); break;
            case "BX": cpu.setBX(valorLimitado); break;
            case "CX": cpu.setCX(valorLimitado); break;
            case "DX": cpu.setDX(valorLimitado); break;
        }
    }

    /** @return el BCP asociado a este ejecutor. */
    public BCP getBcp() {
        return bcp;
    }
}