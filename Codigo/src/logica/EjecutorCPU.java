package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Instruccion;
import modelo.Memoria;

/**
 * Ejecutor del ciclo de instrucción (fetch-decode-execute) para el Proyecto 1.
 *
 * Modelo de tiempo:
 *   - Cada instrucción tiene un "peso" (segundos de CPU) definido en
 *     Instruccion.getPeso(), según la tabla del enunciado.
 *   - El botón "Siguiente" de la GUI = 1 segundo de CPU.
 *   - Una instrucción de peso N tarda N segundos en completarse.
 *   - Las instrucciones son ATÓMICAS: el cambio de contexto ocurre solo
 *     al completarse una instrucción (Stallings, sección 3.4).
 *   - El peso pendiente vive en el BCP (estado del proceso), para que
 *     sobreviva a un cambio de contexto (round-robin).
 *
 * Ciclo:
 *   1. Si no hay instrucción en curso, leer la del PC y calcular su peso.
 *   2. Consumir 1 segundo del peso pendiente.
 *   3. Si el peso llega a 0: ejecutar la instrucción completa
 *      (efectos + avance de PC + sincronización con BCP).
 */
public class EjecutorCPU {

    private CPU cpu;
    private Memoria memoria;
    private BCP bcp;

    /** Indica si el programa ya terminó (INT 20H, EXIT, o error fatal). */
    private boolean programaTerminado;

    /** Máximo de segundos de CPU en modo automático antes de considerar
     *  el proceso colgado (salvaguarda). */
    public static final int MAX_CICLOS_AUTOMATICO = 10000;

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

    /* ==================== CICLO PRINCIPAL (por segundo) ==================== */

    /**
     * Ejecuta UN SEGUNDO de CPU del proceso actual.
     *
     * Si no había instrucción en curso, la carga y calcula su peso.
     * Consume 1 unidad de peso. Si el peso llega a 0, ejecuta la
     * instrucción completa (efectos + avance de PC + sincronización).
     *
     * El peso pendiente vive en el BCP para que sobreviva a un cambio
     * de contexto (round-robin).
     *
     * @return true si la instrucción se completó en este segundo
     * @throws IllegalStateException si el programa ya terminó o no hay
     *         instrucción en la posición del PC
     */
    public boolean ejecutarSegundoDeCPU() {
        if (programaTerminado) {
            return false;
        }

        // 1. Si no hay instrucción en curso, cargarla y calcular su peso
        if (bcp.getPesoPendiente() == 0) {
            int pc = cpu.getPC();
            Instruccion instr = memoria.leerInstruccion(pc);

            if (instr == null) {
                throw new IllegalStateException(
                    "No hay instrucción en la posición " + pc
                    + " (proceso " + bcp.getId() + ")");
            }

            bcp.setPesoPendiente(instr.getPeso());
            cpu.setIR(pc);
        }

        // 2. Consumir 1 segundo
        bcp.setPesoPendiente(bcp.getPesoPendiente() - 1);

        // 3. ¿Se completó la instrucción?
        if (bcp.getPesoPendiente() > 0) {
            return false;   // todavía no
        }

        // 4. Instrucción completada: ejecutar efectos
        int pc = cpu.getPC();
        Instruccion instr = memoria.leerInstruccion(pc);

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
            return true;
        }

        // 5. Avanzar el PC salvo que la instrucción haya sido un salto
        if (!saltoEjecutado) {
            cpu.setPC(pc + 1);
        }

        // 6. Sincronizar CPU -> BCP
        bcp.actualizarDesdeCPU(cpu);

        return true;
    }

    /**
     * Ejecuta el programa completo hasta que termine (INT 20H, EXIT, o error fatal).
     * Pensado para el modo "Automático" de la GUI.
     *
     * @return cantidad de segundos de CPU consumidos
     */
    public int ejecutarHastaTerminar() {
        int segundos = 0;
        while (!programaTerminado && segundos < MAX_CICLOS_AUTOMATICO) {
            ejecutarSegundoDeCPU();
            segundos++;
        }
        if (segundos >= MAX_CICLOS_AUTOMATICO) {
            System.out.println("[WARNING] Proceso " + bcp.getId()
                    + " alcanzó el máximo de segundos (" + MAX_CICLOS_AUTOMATICO
                    + "). Posible ciclo infinito. Terminando por seguridad.");
            bcp.setEstado(EstadoProceso.EXIT);
            bcp.marcarFin();
            programaTerminado = true;
            bcp.actualizarDesdeCPU(cpu);
        }
        return segundos;
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

            case "ADD": {
                int suma = cpu.getAC() + leerRegistro(instr.getArgumento(0));
                if (suma > 32767 || suma < -32768) {
                    cpu.setOverflow(true);
                }
                cpu.setAC(limitarA16Bits(suma));
                return false;
            }

            case "SUB": {
                int resta = cpu.getAC() - leerRegistro(instr.getArgumento(0));
                if (resta > 32767 || resta < -32768) {
                    cpu.setOverflow(true);
                }
                cpu.setAC(limitarA16Bits(resta));
                return false;
            }

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

    private void ejecutarMOV(Instruccion instr) {
        String destino = instr.getArgumento(0);

        if (instr.cantidadArgumentos() >= 2 && instr.esRegistro(1)) {
            escribirRegistro(destino, leerRegistro(instr.getArgumento(1)));
        } else {
            escribirRegistro(destino, instr.getArgumentoComoEntero(1));
        }
    }

    private void ejecutarINC(Instruccion instr) {
        if (instr.cantidadArgumentos() == 0) {
            cpu.setAC(limitarA16Bits(cpu.getAC() + 1));
        } else {
            String reg = instr.getArgumento(0);
            escribirRegistro(reg, leerRegistro(reg) + 1);
        }
    }

    private void ejecutarDEC(Instruccion instr) {
        if (instr.cantidadArgumentos() == 0) {
            cpu.setAC(limitarA16Bits(cpu.getAC() - 1));
        } else {
            String reg = instr.getArgumento(0);
            escribirRegistro(reg, leerRegistro(reg) - 1);
        }
    }

    private void ejecutarSWAP(Instruccion instr) {
        String r1 = instr.getArgumento(0);
        String r2 = instr.getArgumento(1);
        int v1 = leerRegistro(r1);
        int v2 = leerRegistro(r2);
        escribirRegistro(r1, v2);
        escribirRegistro(r2, v1);
    }

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

    private int limitarA16Bits(int valor) {
        int v = valor & 0xFFFF;
        return (v >= 32768) ? v - 65536 : v;
    }

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

    public BCP getBcp() {
        return bcp;
    }

    public boolean isProgramaTerminado() {
        return programaTerminado;
    }
}