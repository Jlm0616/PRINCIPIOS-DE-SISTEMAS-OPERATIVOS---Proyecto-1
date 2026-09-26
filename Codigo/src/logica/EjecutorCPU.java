package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Instruccion;
import modelo.Memoria;

/**
 * Ejecutor del ciclo de instrucción (fetch-decode-execute) para el Proyecto 1.
 *
 * A diferencia de la Tarea 1:
 *   - Las instrucciones se leen como objetos Instruccion desde Memoria.
 *   - Ya no hay Traductor: la codificación binaria desaparece.
 *   - El BCP es un objeto persistente que refleja el estado del proceso.
 *   - Se soporta el conjunto ampliado de instrucciones (~16).
 *   - Se manejan interrupciones (INT 20H, 10H, 09H, 21H).
 *
 * Ciclo de ejecución de UNA instrucción:
 *   1. Fetch:  leer la instrucción en la posición del PC.
 *   2. Decode: identificar el opcode y sus argumentos (ya vienen parseados).
 *   3. Execute: ejecutar la operación correspondiente.
 *   4. Update:  avanzar el PC (salvo saltos) y sincronizar CPU -> BCP.
 */
public class EjecutorCPU {

    private CPU cpu;
    private Memoria memoria;
    private BCP bcp;

    /** Indica si el programa ya terminó (INT 20H o EXIT). */
    private boolean programaTerminado;

    /**
     * Crea un ejecutor asociado a una CPU, una memoria y un BCP.
     *
     * @param cpu     CPU sobre la que se ejecutarán las instrucciones
     * @param memoria memoria desde la que se leerán las instrucciones
     * @param bcp     BCP del proceso en ejecución
     */
    public EjecutorCPU(CPU cpu, Memoria memoria, BCP bcp) {
        this.cpu = cpu;
        this.memoria = memoria;
        this.bcp = bcp;
        this.programaTerminado = false;
    }

    /* ==================== CICLO PRINCIPAL ==================== */

    /**
     * Ejecuta UNA instrucción del programa del BCP actual (fetch-decode-execute).
     *
     * @throws IllegalStateException si el programa ya terminó o no hay instrucción
     */
    public void ejecutarInstruccion() {
        if (programaTerminado) {
            throw new IllegalStateException(
                "El programa del proceso " + bcp.getId() + " ya terminó.");
        }

        int pc = cpu.getPC();
        Instruccion instr = memoria.leerInstruccion(pc);

        if (instr == null) {
            throw new IllegalStateException(
                "No hay instrucción en la posición " + pc
                + " (proceso " + bcp.getId() + ")");
        }

        // Guardar en IR el PC de la instrucción actual (informativo)
        cpu.setIR(pc);

        // Ejecutar. Devuelve true si la instrucción modificó el PC (salto).
        boolean saltoEjecutado;
        try {
            saltoEjecutado = ejecutarOperacion(instr, pc);
        } catch (IllegalStateException e) {
            // Desbordamiento o subdesbordamiento de pila
            // (Tabla 3.2 del Stallings: "Bounds violation")
            bcp.setEstado(EstadoProceso.EXIT);
            bcp.marcarFin();
            programaTerminado = true;
            bcp.actualizarDesdeCPU(cpu);
            System.out.println("[ERROR FATAL] Proceso " + bcp.getId()
                    + " terminado: " + e.getMessage());
            return;   // el proceso queda en EXIT, no se propaga la excepción
        }

        // Avanzar el PC salvo que la instrucción haya sido un salto
        if (!saltoEjecutado) {
            cpu.setPC(pc + 1);
        }

        // Sincronizar CPU -> BCP
        bcp.actualizarDesdeCPU(cpu);
    }

    /**
     * Ejecuta el programa completo hasta que termine (INT 20H, EXIT, o error fatal).
     * Pensado para el modo "Automático" de la GUI.
     *
     * @return cantidad de instrucciones ejecutadas
     */
    public int ejecutarHastaTerminar() {
        int contador = 0;
        while (!programaTerminado) {
            ejecutarInstruccion();
            contador++;
        }
        return contador;
    }

    /* ==================== EJECUCIÓN POR OPCODE ==================== */

    /**
     * Ejecuta la operación correspondiente al opcode de la instrucción.
     *
     * @param instr instrucción a ejecutar
     * @param pc    PC actual (dirección de la instrucción en ejecución)
     * @return true si la instrucción modificó el PC (salto tomado), false si no
     */
    private boolean ejecutarOperacion(Instruccion instr, int pc) {
        String opcode = instr.getOpcode();

        switch (opcode) {
            case "LOAD":
                cpu.setAC(leerRegistro(instr.getArgumento(0)));
                return false;

            case "STORE":
                escribirRegistro(instr.getArgumento(0), cpu.getAC());
                return false;

            case "MOV":
                ejecutarMOV(instr);
                return false;

            case "ADD":
                int suma = cpu.getAC() + leerRegistro(instr.getArgumento(0));
                if (suma > 32767 || suma < -32768) {
                    cpu.setOverflow(true);
                }
                cpu.setAC(limitarA16Bits(suma));
                return false;

            case "SUB":
                int resta = cpu.getAC() - leerRegistro(instr.getArgumento(0));
                if (resta > 32767 || resta < -32768) {
                    cpu.setOverflow(true);
                }
                cpu.setAC(limitarA16Bits(resta));
                return false;

            case "INC":
                ejecutarINC(instr);
                return false;

            case "DEC":
                ejecutarDEC(instr);
                return false;

            case "SWAP":
                ejecutarSWAP(instr);
                return false;

            case "CMP":
                ejecutarCMP(instr);
                return false;

            case "JMP":
                cpu.setPC(pc + 1 + instr.getArgumentoComoEntero(0));
                return true;

            case "JE":
                if (cpu.getBanderaIgual()) {
                    cpu.setPC(pc + 1 + instr.getArgumentoComoEntero(0));
                    return true;
                }
                return false;

            case "JNE":
                if (!cpu.getBanderaIgual()) {
                    cpu.setPC(pc + 1 + instr.getArgumentoComoEntero(0));
                    return true;
                }
                return false;

            case "PUSH":
                bcp.apilar(leerRegistro(instr.getArgumento(0)));
                return false;

            case "POP":
                escribirRegistro(instr.getArgumento(0), bcp.desapilar());
                return false;

            case "PARAM":
                for (int i = 0; i < instr.cantidadArgumentos(); i++) {
                    bcp.apilar(instr.getArgumentoComoEntero(i));
                }
                return false;

            case "INT":
                ejecutarINT(instr.getCodigoInterrupcion(0));
                return false;

            default:
                throw new UnsupportedOperationException(
                    "Opcode no soportado: " + opcode);
        }
    }

    /* ==================== HELPERS POR INSTRUCCIÓN ==================== */

    /**
     * Ejecuta MOV. Soporta:
     *   MOV reg_destino, reg_origen
     *   MOV reg_destino, valor
     */
    private void ejecutarMOV(Instruccion instr) {
        String destino = instr.getArgumento(0);

        if (instr.cantidadArgumentos() >= 2 && instr.esRegistro(1)) {
            // MOV reg, reg
            escribirRegistro(destino, leerRegistro(instr.getArgumento(1)));
        } else {
            // MOV reg, valor
            escribirRegistro(destino, instr.getArgumentoComoEntero(1));
        }
    }

    /**
     * Ejecuta INC. Soporta:
     *   INC        -> AC = AC + 1
     *   INC reg    -> reg = reg + 1
     */
    private void ejecutarINC(Instruccion instr) {
        if (instr.cantidadArgumentos() == 0) {
            cpu.setAC(limitarA16Bits(cpu.getAC() + 1));
        } else {
            String reg = instr.getArgumento(0);
            escribirRegistro(reg, leerRegistro(reg) + 1);
        }
    }

    /**
     * Ejecuta DEC. Soporta:
     *   DEC        -> AC = AC - 1
     *   DEC reg    -> reg = reg - 1
     */
    private void ejecutarDEC(Instruccion instr) {
        if (instr.cantidadArgumentos() == 0) {
            cpu.setAC(limitarA16Bits(cpu.getAC() - 1));
        } else {
            String reg = instr.getArgumento(0);
            escribirRegistro(reg, leerRegistro(reg) - 1);
        }
    }

    /**
     * Ejecuta SWAP reg1, reg2 (intercambia los valores).
     */
    private void ejecutarSWAP(Instruccion instr) {
        String r1 = instr.getArgumento(0);
        String r2 = instr.getArgumento(1);
        int v1 = leerRegistro(r1);
        int v2 = leerRegistro(r2);
        escribirRegistro(r1, v2);
        escribirRegistro(r2, v1);
    }

    /**
     * Ejecuta CMP reg1, reg2. Deja el resultado en la banderaIgual de la CPU.
     */
    private void ejecutarCMP(Instruccion instr) {
        int v1 = leerRegistro(instr.getArgumento(0));
        int v2 = leerRegistro(instr.getArgumento(1));
        cpu.setBanderaIgual(v1 == v2);
    }

    /**
     * Ejecuta una interrupción.
     *
     * @param codigo código decimal de la interrupción (ej. 0x20 = 32)
     */
    private void ejecutarINT(int codigo) {
        switch (codigo) {
            case 0x20:  // 20H -> fin del programa
                bcp.setEstado(EstadoProceso.EXIT);
                bcp.marcarFin();
                programaTerminado = true;
                break;

            case 0x10:  // 10H -> imprimir DX en pantalla
                System.out.println("[PANTALLA] DX = " + cpu.getDX());
                break;

            case 0x09:  // 09H -> leer teclado (numérico 0-255)
                // TODO: conectar con la consola de teclado de la GUI
                System.out.println("[TECLADO] pendiente de implementación");
                break;

            case 0x21:  // 21H -> manejo de archivos
                // TODO: conectar con el almacenamiento secundario
                System.out.println("[ARCHIVOS] pendiente de implementación");
                break;

            default:
                throw new UnsupportedOperationException(
                    "Interrupción no soportada: " + Integer.toHexString(codigo) + "H");
        }
    }

    /* ==================== HELPERS GENERALES ==================== */

    /**
     * Trunca un valor a 16 bits con signo (complemento a 2).
     * Simula el comportamiento de un registro real de 16 bits.
     *
     * @param valor valor a truncar
     * @return valor truncado a 16 bits con signo
     */
    private int limitarA16Bits(int valor) {
        int v = valor & 0xFFFF;
        return (v >= 32768) ? v - 65536 : v;
    }

    /**
     * Lee el valor de un registro de la CPU.
     *
     * @param nombre nombre del registro ("AC", "AX", "BX", "CX", "DX")
     * @return el valor del registro
     * @throws IllegalArgumentException si el nombre no es válido
     */
    private int leerRegistro(String nombre) {
        switch (nombre) {
            case "AC": return cpu.getAC();
            case "AX": return cpu.getAX();
            case "BX": return cpu.getBX();
            case "CX": return cpu.getCX();
            case "DX": return cpu.getDX();
            default:
                throw new IllegalArgumentException("Registro desconocido: " + nombre);
        }
    }

    /**
     * Escribe un valor en un registro de la CPU.
     *
     * @param nombre nombre del registro ("AC", "AX", "BX", "CX", "DX")
     * @param valor  valor a escribir (se trunca a 16 bits)
     * @throws IllegalArgumentException si el nombre no es válido
     */
    private void escribirRegistro(String nombre, int valor) {
        int valorLimitado = limitarA16Bits(valor);
        switch (nombre) {
            case "AC": cpu.setAC(valorLimitado); break;
            case "AX": cpu.setAX(valorLimitado); break;
            case "BX": cpu.setBX(valorLimitado); break;
            case "CX": cpu.setCX(valorLimitado); break;
            case "DX": cpu.setDX(valorLimitado); break;
            default:
                throw new IllegalArgumentException("Registro desconocido: " + nombre);
        }
    }

    /* ==================== GETTERS ==================== */

    /** @return el BCP asociado a este ejecutor. */
    public BCP getBcp() {
        return bcp;
    }

    /** @return true si el programa ya terminó. */
    public boolean isProgramaTerminado() {
        return programaTerminado;
    }
}