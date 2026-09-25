package App;

import gui.VentanaPrincipal;
import modelo.Instruccion;
import logica.Ensamblador;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.List;

/**
 * Punto de entrada del simulador Mini PC + pruebas del Ensamblador.
 *
 * Las pruebas están dentro del main para que puedas ejecutarlas
 * rápidamente desde NetBeans sin necesidad de tests formales.
 */
public class Main {

    public static void main(String[] args) {

        // ============ PRUEBAS DE Instruccion (las que ya tenías) ============
        probarInstruccion();

        // ============ PRUEBAS DE Ensamblador ============
        probarEnsamblador();

        // ============ LANZAR LA GUI ============
        SwingUtilities.invokeLater(() -> {
            VentanaPrincipal ventana = new VentanaPrincipal();
            ventana.setVisible(true);
        });
    }

    /* ==================== PRUEBAS DE Instruccion ==================== */

    private static void probarInstruccion() {
        System.out.println("========== PRUEBAS DE Instruccion ==========\n");

        Instruccion inc = new Instruccion("INC");
        System.out.println("Caso 1: " + inc);
        System.out.println("  Cantidad argumentos: " + inc.cantidadArgumentos());

        Instruccion load = new Instruccion("LOAD", java.util.Arrays.asList("AX"));
        System.out.println("Caso 2: " + load);
        System.out.println("  Argumento 0: " + load.getArgumento(0));
        System.out.println("  Es registro?: " + load.esRegistro(0));

        Instruccion movRegReg = new Instruccion("MOV", java.util.Arrays.asList("BX", "AX"));
        System.out.println("Caso 3: " + movRegReg);
        System.out.println("  Arg0 es registro?: " + movRegReg.esRegistro(0));
        System.out.println("  Arg1 es registro?: " + movRegReg.esRegistro(1));

        Instruccion movRegVal = new Instruccion("MOV", java.util.Arrays.asList("BX", "5"));
        System.out.println("Caso 4: " + movRegVal);
        System.out.println("  Arg1 es registro?: " + movRegVal.esRegistro(1));
        System.out.println("  Arg1 como entero: " + movRegVal.getArgumentoComoEntero(1));

        Instruccion intFin = new Instruccion("INT", java.util.Arrays.asList("20H"));
        System.out.println("Caso 5: " + intFin);
        System.out.println("  Codigo interrupcion: " + intFin.getCodigoInterrupcion(0));

        System.out.println();
    }

    /* ==================== PRUEBAS DE Ensamblador ==================== */

    private static void probarEnsamblador() {
        System.out.println("========== PRUEBAS DE Ensamblador ==========\n");

        // --- Caso A: archivo válido ---
        System.out.println("--- Caso A: archivo valido ---");
        File valido = new File("C:\\Users\\julia\\Documents\\Sistemas Operativos\\Proyecto 1\\Ejemplos\\ejemplo.asm");
        probarArchivo(valido);

        // --- Caso B: archivo con errores ---
        System.out.println("\n--- Caso B: archivo con errores ---");
        File malo = new File("C:\\Users\\julia\\Documents\\Sistemas Operativos\\Proyecto 1\\Ejemplos\\ejemplo2.asm");
        probarArchivo(malo);

        // --- Caso C: archivo inexistente ---
        System.out.println("\n--- Caso C: archivo inexistente ---");
        File noExiste = new File("no_existe.asm");
        probarArchivo(noExiste);

        // --- Caso D: extensión incorrecta ---
        System.out.println("\n--- Caso D: extension incorrecta ---");
        File noAsm = new File("programa_prueba.txt");
        probarArchivo(noAsm);

        System.out.println();
    }

    /**
     * Ejecuta el flujo completo sobre un archivo: valida y, si es válido,
     * lee las instrucciones y las imprime.
     */
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
}