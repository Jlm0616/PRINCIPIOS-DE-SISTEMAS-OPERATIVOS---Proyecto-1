package App;

import gui.VentanaPrincipal;
import javax.swing.SwingUtilities;

import modelo.Instruccion;
import java.util.List;
import java.util.Arrays;

/**
 * Punto de entrada del simulador Mini PC.
 *
 * Lanza la ventana principal dentro del Event Dispatch Thread (EDT),
 * que es el hilo obligatorio para crear y manipular componentes Swing.
 */
public class Main {

    /** 
     * Inicia la aplicación.
     *
     * @param args argumentos de línea de comandos (no se usan)
     */
    public static void main(String[] args) {

        // Caso 1: instruccion sin argumentos
        Instruccion inc = new Instruccion("INC");
        System.out.println("Caso 1: " + inc);
        System.out.println("  Cantidad argumentos: " + inc.cantidadArgumentos());

        // Caso 2: un argumento (registro)
        Instruccion load = new Instruccion("LOAD", Arrays.asList("AX"));
        System.out.println("Caso 2: " + load);
        System.out.println("  Argumento 0: " + load.getArgumento(0));
        System.out.println("  Es registro?: " + load.esRegistro(0));

        // Caso 3: dos argumentos, registro + registro
        Instruccion movRegReg = new Instruccion("MOV", Arrays.asList("BX", "AX"));
        System.out.println("Caso 3: " + movRegReg);
        System.out.println("  Arg0 es registro?: " + movRegReg.esRegistro(0));
        System.out.println("  Arg1 es registro?: " + movRegReg.esRegistro(1));

        // Caso 4: dos argumentos, registro + valor numerico
        Instruccion movRegVal = new Instruccion("MOV", Arrays.asList("BX", "5"));
        System.out.println("Caso 4: " + movRegVal);
        System.out.println("  Arg1 es registro?: " + movRegVal.esRegistro(1));
        System.out.println("  Arg1 como entero: " + movRegVal.getArgumentoComoEntero(1));

        // Caso 5: SWAP con dos registros
        Instruccion swap = new Instruccion("SWAP", Arrays.asList("AX", "BX"));
        System.out.println("Caso 5: " + swap);

        // Caso 6: JMP con desplazamiento negativo
        Instruccion jmp = new Instruccion("JMP", Arrays.asList("-2"));
        System.out.println("Caso 6: " + jmp);
        System.out.println("  Desplazamiento: " + jmp.getArgumentoComoEntero(0));

        // Caso 7: PARAM con 3 valores
        Instruccion param = new Instruccion("PARAM", Arrays.asList("5", "10", "3"));
        System.out.println("Caso 7: " + param);
        System.out.println("  Cantidad argumentos: " + param.cantidadArgumentos());

        // Caso 8: INT con codigo de interrupcion
        Instruccion intFin = new Instruccion("INT", Arrays.asList("20H"));
        System.out.println("Caso 8: " + intFin);
    }
}