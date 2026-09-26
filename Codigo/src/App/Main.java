package App;

import modelo.BCP;
import modelo.CPU;
import modelo.Instruccion;
import modelo.Memoria;
import logica.EjecutorCPU;
import logica.Ensamblador;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Punto de entrada del simulador Mini PC + pruebas del núcleo del proyecto.
 *
 * Las pruebas están dentro del main para que puedas ejecutarlas
 * rápidamente desde NetBeans sin necesidad de tests formales.
 *
 * Cuando la GUI esté adaptada al nuevo BCP, se descomentará
 * el bloque de SwingUtilities.invokeLater(...) al final.
 */
public class Main {

    public static void main(String[] args) {

        probarInstruccion();
        probarEnsamblador();
        probarEjecutorCPU();
        probarSaltos();
        probarPila();

        // ============ LANZAR LA GUI (descomentar cuando esté lista) ============
        // SwingUtilities.invokeLater(() -> {
        //     VentanaPrincipal ventana = new VentanaPrincipal();
        //     ventana.setVisible(true);
        // });
    }

    /* ==================== PRUEBAS DE Instruccion ==================== */

    private static void probarInstruccion() {
        System.out.println("========== PRUEBAS DE Instruccion ==========\n");

        Instruccion inc = new Instruccion("INC");
        System.out.println("Caso 1: " + inc);
        System.out.println("  Cantidad argumentos: " + inc.cantidadArgumentos());

        Instruccion load = new Instruccion("LOAD", Arrays.asList("AX"));
        System.out.println("Caso 2: " + load);
        System.out.println("  Argumento 0: " + load.getArgumento(0));
        System.out.println("  Es registro?: " + load.esRegistro(0));

        Instruccion movRegReg = new Instruccion("MOV", Arrays.asList("BX", "AX"));
        System.out.println("Caso 3: " + movRegReg);
        System.out.println("  Arg0 es registro?: " + movRegReg.esRegistro(0));
        System.out.println("  Arg1 es registro?: " + movRegReg.esRegistro(1));

        Instruccion movRegVal = new Instruccion("MOV", Arrays.asList("BX", "5"));
        System.out.println("Caso 4: " + movRegVal);
        System.out.println("  Arg1 es registro?: " + movRegVal.esRegistro(1));
        System.out.println("  Arg1 como entero: " + movRegVal.getArgumentoComoEntero(1));

        Instruccion intFin = new Instruccion("INT", Arrays.asList("20H"));
        System.out.println("Caso 5: " + intFin);
        System.out.println("  Codigo interrupcion: " + intFin.getCodigoInterrupcion(0));

        System.out.println();
    }

    /* ==================== PRUEBAS DE Ensamblador ==================== */

    private static void probarEnsamblador() {
        System.out.println("========== PRUEBAS DE Ensamblador ==========\n");

        File valido = new File("programa_prueba.asm");
        probarArchivo(valido);

        System.out.println();
    }

    private static void probarArchivo(File archivo) {
        Ensamblador ensamblador = new Ensamblador();

        System.out.println("Archivo: " + archivo.getAbsolutePath());

        if (!ensamblador.esArchivoValido(archivo)) {
            System.out.println("  VALIDACION FALLIDA");
            System.out.println("  Errores encontrados:");
            for (String error : ensamblador.getErroresEncontrados()) {
                System.out.println("   - " + error);
            }
            return;
        }

        System.out.println("  VALIDACION OK");

        List<Instruccion> instrucciones = ensamblador.leerArchivo(archivo);
        System.out.println("  Instrucciones leidas: " + instrucciones.size());
        System.out.println("  Listado:");

        int i = 1;
        for (Instruccion instr : instrucciones) {
            System.out.println("   " + i + ". " + instr);
            i++;
        }
    }

    /* ==================== PRUEBAS DE EjecutorCPU (básico) ==================== */

    private static void probarEjecutorCPU() {
        System.out.println("========== PRUEBAS DE EjecutorCPU ==========\n");

        int limiteKernel = 32;
        Memoria memoria = new Memoria(256, limiteKernel);
        CPU cpu = new CPU(limiteKernel);
        BCP bcp = new BCP(1, 1, limiteKernel, 7);

        memoria.registrarBCP(bcp);
        System.out.println("BCP registrado en direccion: " + bcp.getDireccion());
        System.out.println("PC inicial de la CPU: " + cpu.getPC());
        System.out.println();

        int pos = limiteKernel;
        memoria.escribir(pos++, new Instruccion("MOV", Arrays.asList("AX", "5")));
        memoria.escribir(pos++, new Instruccion("MOV", Arrays.asList("BX", "3")));
        memoria.escribir(pos++, new Instruccion("ADD", Arrays.asList("BX")));
        memoria.escribir(pos++, new Instruccion("INT", Arrays.asList("10H")));
        memoria.escribir(pos++, new Instruccion("MOV", Arrays.asList("DX", "42")));
        memoria.escribir(pos++, new Instruccion("INT", Arrays.asList("10H")));
        memoria.escribir(pos++, new Instruccion("INT", Arrays.asList("20H")));

        System.out.println("Programa cargado: 7 instrucciones desde posicion " + limiteKernel);
        System.out.println();

        EjecutorCPU ejecutor = new EjecutorCPU(cpu, memoria, bcp);

        int paso = 1;
        while (!ejecutor.isProgramaTerminado()) {
            System.out.println("--- Paso " + paso + " ---");
            System.out.println("  Antes   -> PC=" + cpu.getPC()
                    + ", AC=" + cpu.getAC()
                    + ", AX=" + cpu.getAX()
                    + ", BX=" + cpu.getBX()
                    + ", DX=" + cpu.getDX()
                    + ", IR=" + cpu.getIR());

            ejecutor.ejecutarInstruccion();

            System.out.println("  Despues -> PC=" + cpu.getPC()
                    + ", AC=" + cpu.getAC()
                    + ", AX=" + cpu.getAX()
                    + ", BX=" + cpu.getBX()
                    + ", DX=" + cpu.getDX()
                    + ", IR=" + cpu.getIR());
            System.out.println();

            paso++;
            if (paso > 20) {
                System.out.println("!! Demasiados pasos, algo anda mal.");
                break;
            }
        }

        System.out.println("=== Estado final del BCP ===");
        System.out.println("  ID:            " + bcp.getId());
        System.out.println("  Estado:        " + bcp.getEstado());
        System.out.println("  PC:            " + bcp.getPc());
        System.out.println("  AC:            " + bcp.getAc());
        System.out.println("  AX:            " + bcp.getAx());
        System.out.println("  BX:            " + bcp.getBx());
        System.out.println("  DX:            " + bcp.getDx());
        System.out.println("  IR:            " + bcp.getIr());
        System.out.println("  Tiempo fin:    " + bcp.getTiempoFin());
        System.out.println();
    }

    /* ==================== PRUEBA DE SALTOS ==================== */

    private static void probarSaltos() {
        System.out.println("========== TEST DE SALTOS ==========\n");

        int limiteKernel = 32;
        Memoria mem = new Memoria(256, limiteKernel);
        CPU cpu = new CPU(limiteKernel);
        BCP bcp = new BCP(2, 1, limiteKernel, 5);
        mem.registrarBCP(bcp);

        // Programa:
        //   pos 32: MOV AX, 1
        //   pos 33: JMP +2        -> debe saltar a pos 36
        //   pos 34: MOV AX, 99    -> NO se ejecuta
        //   pos 35: MOV AX, 98    -> NO se ejecuta
        //   pos 36: INT 20H
        int p = limiteKernel;
        mem.escribir(p++, new Instruccion("MOV", Arrays.asList("AX", "1")));
        mem.escribir(p++, new Instruccion("JMP", Arrays.asList("+2")));
        mem.escribir(p++, new Instruccion("MOV", Arrays.asList("AX", "99")));
        mem.escribir(p++, new Instruccion("MOV", Arrays.asList("AX", "98")));
        mem.escribir(p++, new Instruccion("INT", Arrays.asList("20H")));

        EjecutorCPU ejecutor = new EjecutorCPU(cpu, mem, bcp);

        // Paso 1
        ejecutor.ejecutarInstruccion();
        System.out.println("Tras MOV AX,1 -> PC=" + cpu.getPC() + ", AX=" + cpu.getAX());
        System.out.println("   Esperado: PC=33, AX=1");

        // Paso 2 (JMP)
        ejecutor.ejecutarInstruccion();
        System.out.println("Tras JMP +2   -> PC=" + cpu.getPC() + ", AX=" + cpu.getAX());
        System.out.println("   Esperado: PC=36, AX=1   (fix correcto)");
        System.out.println("   Si sale PC=35 -> off-by-one");
        System.out.println("   Si sale PC=34 -> bug viejo");

        // Paso 3 (INT)
        ejecutor.ejecutarInstruccion();
        System.out.println("Tras INT 20H  -> PC=" + cpu.getPC()
                + ", estado=" + bcp.getEstado());
        System.out.println("   Esperado: PC=36, estado=EXIT");
        System.out.println();
    }

    /* ==================== PRUEBA DE PILA (desbordamiento) ==================== */

    private static void probarPila() {
        System.out.println("========== TEST DE PILA (desbordamiento) ==========\n");

        int limiteKernel = 32;
        Memoria mem = new Memoria(256, limiteKernel);
        CPU cpu = new CPU(limiteKernel);
        BCP bcp = new BCP(3, 1, limiteKernel, 8);
        mem.registrarBCP(bcp);

        // El proceso hace PUSH 6 veces. La pila tiene capacidad 5.
        // En el 6to PUSH debe lanzar IllegalStateException, capturarse,
        // y el proceso terminar en EXIT.
        //
        // AX = 7 para que se note el valor apilado.
        int p = limiteKernel;
        mem.escribir(p++, new Instruccion("MOV", Arrays.asList("AX", "7")));
        mem.escribir(p++, new Instruccion("PUSH", Arrays.asList("AX")));  // 1
        mem.escribir(p++, new Instruccion("PUSH", Arrays.asList("AX")));  // 2
        mem.escribir(p++, new Instruccion("PUSH", Arrays.asList("AX")));  // 3
        mem.escribir(p++, new Instruccion("PUSH", Arrays.asList("AX")));  // 4
        mem.escribir(p++, new Instruccion("PUSH", Arrays.asList("AX")));  // 5 -> pila llena
        mem.escribir(p++, new Instruccion("PUSH", Arrays.asList("AX")));  // 6 -> DEBE FALLAR
        mem.escribir(p++, new Instruccion("INT", Arrays.asList("20H")));

        EjecutorCPU ejecutor = new EjecutorCPU(cpu, mem, bcp);

        System.out.println("Ejecutando hasta el final (esperando error de pila)...");
        System.out.println();

        int contador = ejecutor.ejecutarHastaTerminar();

        System.out.println();
        System.out.println("Instrucciones ejecutadas: " + contador);
        System.out.println("Estado final del BCP:     " + bcp.getEstado());
        System.out.println("Tamano de pila final:     " + bcp.getPila().size());
        System.out.println("Tiempo fin:               " + bcp.getTiempoFin());
        System.out.println();
        System.out.println("   Esperado: estado=EXIT, pila=5, error en consola");
        System.out.println();
    }
}