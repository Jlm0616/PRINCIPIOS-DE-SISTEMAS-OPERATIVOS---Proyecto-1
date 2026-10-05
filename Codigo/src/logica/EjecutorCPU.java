package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Instruccion;
import modelo.Memoria;
import modelo.Disco;

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
 *
 * Las interrupciones (INT) se delegan a la clase Interrupciones, que
 * encapsula la logica de las llamadas al sistema.
 *
 * IMPORTANTE: DX es un String. Esto permite guardar nombres de archivo
 * (ej. "datos.txt") para INT 21H. Para operaciones aritmeticas con DX,
 * se usa getDXAsInt() / setDXAsInt() que parsean/convierten el string.
 */
public class EjecutorCPU {

    private CPU cpu;
    private Memoria memoria;
    private BCP bcp;
    private Interrupciones interrupciones;

    /** Indica si el programa ya terminó (INT 20H, EXIT, o error fatal). */
    private boolean programaTerminado;

    /** Máximo de segundos de CPU en modo automático antes de considerar
     *  el proceso colgado (salvaguarda). */
    public static final int MAX_CICLOS_AUTOMATICO = 10000;

    public EjecutorCPU(CPU cpu, Memoria memoria, BCP bcp, Disco disco) {
        this.cpu = cpu;
        this.memoria = memoria;
        this.bcp = bcp;
        this.interrupciones = new Interrupciones(disco);
        this.programaTerminado = false;
    }

    /* ==================== CICLO PRINCIPAL (por segundo) ==================== */

    public boolean ejecutarSegundoDeCPU() {
        if (programaTerminado) {
            return false;
        }

        if (bcp.getPesoPendiente() == 0) {
            int pc = cpu.getPC();
            Instruccion instr = memoria.leerInstruccion(pc);

            if (instr == null) {
                bcp.setEstado(EstadoProceso.EXIT);
                bcp.marcarFin();
                programaTerminado = true;
                bcp.actualizarDesdeCPU(cpu);
                System.out.println("[FIN] Proceso " + bcp.getId()
                        + " terminó: no hay más instrucciones en la posición " + pc);
                return true;
            }

            bcp.setPesoPendiente(instr.getPeso());
            cpu.setIR(pc);
        }

        bcp.setPesoPendiente(bcp.getPesoPendiente() - 1);

        if (bcp.getPesoPendiente() > 0) {
            return false;
        }

        int pc = cpu.getPC();
        Instruccion instr = memoria.leerInstruccion(pc);

        boolean saltoEjecutado;
        try {
            saltoEjecutado = ejecutarOperacion(instr, pc);
        } catch (IllegalStateException e) {
            bcp.setEstado(EstadoProceso.EXIT);
            bcp.marcarFin();
            programaTerminado = true;
            bcp.actualizarDesdeCPU(cpu);
            System.out.println("[ERROR FATAL] Proceso " + bcp.getId()
                    + " terminado: " + e.getMessage());
            return true;
        }

        if (!saltoEjecutado) {
            cpu.setPC(pc + 1);
        }

        bcp.actualizarDesdeCPU(cpu);

        return true;
    }

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

    /**
     * Ejecuta MOV destino, fuente.
     *
     * Casos:
     *   1. MOV DX, "texto"     → asigna string literal a DX
     *   2. MOV reg, reg        → copia valor entre registros
     *   3. MOV reg, numero     → asigna numero a registro
     */
    private void ejecutarMOV(Instruccion instr) {
        String destino = instr.getArgumento(0);

        // Caso 1: MOV DX, "texto" (string literal)
        if (instr.cantidadArgumentos() >= 2 && instr.esStringLiteral(1)) {
            escribirRegistroString(destino, instr.getStringLiteral(1));
            return;
        }

        // Caso 2: MOV reg, reg
        if (instr.cantidadArgumentos() >= 2 && instr.esRegistro(1)) {
            escribirRegistro(destino, leerRegistro(instr.getArgumento(1)));
            return;
        }

        // Caso 3: MOV reg, numero
        escribirRegistro(destino, instr.getArgumentoComoEntero(1));
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

    private void ejecutarINT(int codigo) {
        Interrupciones.ResultadoInterrupcion resultado =
                interrupciones.ejecutar(codigo, bcp);

        switch (resultado) {
            case RUNNING:
                break;
            case BLOQUEADO:
                bcp.setEstado(EstadoProceso.BLOCKED);
                break;
            case TERMINADO:
                bcp.setEstado(EstadoProceso.EXIT);
                programaTerminado = true;
                break;
        }
    }

    /* ==================== CALLBACKS DE INTERRUPCIONES ==================== */

    public void setSalidaPantalla(java.util.function.Consumer<String> callback) {
        interrupciones.setSalidaPantalla(callback);
    }

    public void setSolicitudTeclado(java.util.function.Consumer<BCP> callback) {
        interrupciones.setSolicitudTeclado(callback);
    }

    public void setSolicitudArchivo(java.util.function.Consumer<BCP> callback) {
        interrupciones.setSolicitudArchivo(callback);
    }

    /* ==================== HELPERS GENERALES ==================== */

    private int limitarA16Bits(int valor) {
        int v = valor & 0xFFFF;
        return (v >= 32768) ? v - 65536 : v;
    }

    /**
     * Lee el valor de un registro por nombre.
     *
     * DX se parsea como entero (getDXAsInt).
     * Si DX contiene un texto no numerico, devuelve 0.
     *
     * AH y AL son mitades de AX.
     */
    private int leerRegistro(String nombre) {
        switch (nombre) {
            case "AC": return cpu.getAC();
            case "AX": return cpu.getAX();
            case "BX": return cpu.getBX();
            case "CX": return cpu.getCX();
            case "DX": return cpu.getDXAsInt();   // ← CAMBIO: parsea String
            case "AH": return (cpu.getAX() >> 8) & 0xFF;
            case "AL": return cpu.getAX() & 0xFF;
            default:
                throw new IllegalArgumentException("Registro desconocido: " + nombre);
        }
    }

    /**
     * Escribe un valor entero en un registro por nombre.
     *
     * DX se escribe como String (setDXAsInt).
     */
    private void escribirRegistro(String nombre, int valor) {
        int valorLimitado = limitarA16Bits(valor);
        switch (nombre) {
            case "AC": cpu.setAC(valorLimitado); break;
            case "AX": cpu.setAX(valorLimitado); break;
            case "BX": cpu.setBX(valorLimitado); break;
            case "CX": cpu.setCX(valorLimitado); break;
            case "DX": cpu.setDXAsInt(valorLimitado); break;   // ← CAMBIO
            case "AH": {
                int al = cpu.getAX() & 0xFF;
                cpu.setAX(((valorLimitado & 0xFF) << 8) | al);
                break;
            }
            case "AL": {
                int ah = (cpu.getAX() >> 8) & 0xFF;
                cpu.setAX((ah << 8) | (valorLimitado & 0xFF));
                break;
            }
            default:
                throw new IllegalArgumentException("Registro desconocido: " + nombre);
        }
    }

    /**
     * Escribe un String en un registro.
     *
     * Solo DX acepta strings. Otros registros intentan parsear el string
     * como entero.
     */
    private void escribirRegistroString(String nombre, String valor) {
        if ("DX".equals(nombre)) {
            cpu.setDX(valor);
        } else {
            try {
                escribirRegistro(nombre, Integer.parseInt(valor));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "El registro " + nombre + " solo acepta valores numericos, no: " + valor);
            }
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