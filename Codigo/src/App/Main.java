package App;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Memoria;
import logica.GestorProcesos;
import logica.ListaDeTrabajos;
import logica.ParticionadorFijo;
import logica.ResultadoCarga;

import java.io.File;

/**
 * Prueba end-to-end del gestor de procesos (Proyecto 1).
 *
 * Carga varios archivos .asm, ejecuta paso a paso mostrando el estado
 * del sistema (proceso actual, cola, BCPs) y luego ejecuta todo
 * automáticamente.
 */
public class Main {

    public static void main(String[] args) {
        probarGestorProcesos();
    }

    private static void probarGestorProcesos() {
        System.out.println("========== PRUEBA DEL GESTOR DE PROCESOS ==========\n");

        // --- 1. Configurar el sistema ---
        int tamanoMemoria = 256;
        int limiteKernel = 32;      // primeras 32 posiciones para kernel
        int maxProcesos = 5;        // hasta 5 procesos según el enunciado

        Memoria memoria = new Memoria(tamanoMemoria, limiteKernel);
        CPU cpu = new CPU(limiteKernel);
        ListaDeTrabajos listaDeTrabajos = new ListaDeTrabajos();

        int espacioUsuario = memoria.getEspacioUsuarioDisponible();
        ParticionadorFijo particionador = new ParticionadorFijo(
                limiteKernel, espacioUsuario, maxProcesos);

        GestorProcesos gestor = new GestorProcesos(
                memoria, cpu, listaDeTrabajos, particionador);

        System.out.println("Configuracion:");
        System.out.println("  Memoria total:      " + tamanoMemoria);
        System.out.println("  Kernel:             0 .. " + (limiteKernel - 1));
        System.out.println("  Usuario:            " + limiteKernel + " .. " + (tamanoMemoria - 1));
        System.out.println("  Tamano particion:   " + particionador.getTamanoParticion());
        System.out.println("  Max procesos:       " + maxProcesos);
        System.out.println();

        // --- 2. Cargar archivos .asm ---
        // (Deben estar en la raíz del proyecto)
        cargarArchivo(gestor, "C:\\Users\\julia\\Documents\\Sistemas Operativos\\Proyecto 1\\Ejemplos\\ejemplo.asm");
        cargarArchivo(gestor, "C:\\Users\\julia\\Documents\\Sistemas Operativos\\Proyecto 1\\Ejemplos\\ejemplo2.asm");
        cargarArchivo(gestor, "C:\\Users\\julia\\Documents\\Sistemas Operativos\\Proyecto 1\\Ejemplos\\ejemplo3.asm");

        System.out.println("\nCola de trabajos despues de cargar:");
        mostrarCola(listaDeTrabajos);
        System.out.println();

        // --- 3. Ejecución paso a paso ---
        System.out.println("========== EJECUCION PASO A PASO ==========\n");

        int paso = 1;
        while (gestor.hayProcesosActivos() && paso <= 30) {
            System.out.println("--- Paso " + paso + " ---");

            BCP antes = gestor.getProcesoActual();
            if (antes != null) {
                System.out.println("  Proceso en CPU: ID=" + antes.getId()
                        + ", peso pendiente=" + antes.getPesoPendiente()
                        + ", PC=" + antes.getPc());
            } else {
                System.out.println("  (no hay proceso en CPU)");
            }

            boolean ejecutado = gestor.ejecutarUnPaso();
            if (!ejecutado) {
                System.out.println("  No quedan procesos para ejecutar.");
                break;
            }

            BCP despues = gestor.getProcesoActual();
            if (despues != null) {
                System.out.println("  Despues: ID=" + despues.getId()
                        + ", estado=" + despues.getEstado()
                        + ", peso pendiente=" + despues.getPesoPendiente()
                        + ", PC=" + despues.getPc()
                        + ", AC=" + despues.getAc()
                        + ", AX=" + despues.getAx()
                        + ", BX=" + despues.getBx()
                        + ", DX=" + despues.getDx());
            }
            System.out.println("  Cola:");
            mostrarCola(listaDeTrabajos);
            System.out.println();
            paso++;
        }

        // --- 4. Mostrar estado final ---
        System.out.println("========== ESTADISTICAS FINALES ==========\n");

        if (gestor.getProcesosTerminados().isEmpty()) {
            System.out.println("  (no hay procesos terminados)");
        } else {
            for (BCP bcp : gestor.getProcesosTerminados()) {
                System.out.println("  Proceso " + bcp.getId()
                        + ": inicio=" + bcp.getTiempoInicio()
                        + ", fin=" + bcp.getTiempoFin()
                        + ", duracion=" + bcp.getDuracionSegundos() + "s"
                        + ", estado final=" + bcp.getEstado());
            }
        }
        System.out.println();
    }

    private static void cargarArchivo(GestorProcesos gestor, String nombreArchivo) {
        File archivo = new File(nombreArchivo);
        System.out.print("Cargando " + nombreArchivo + " ... ");

        if (!archivo.exists()) {
            System.out.println("NO EXISTE (ignorado)");
            return;
        }

        ResultadoCarga resultado = gestor.cargarPrograma(archivo);

        switch (resultado.getEstado()) {
            case EXITO:
                System.out.println("OK (ID=" + resultado.getBcp().getId() + ")");
                break;
            case ERROR:
                System.out.println("ERROR: " + resultado.getMensajeError());
                break;
            case EN_ESPERA:
                System.out.println("EN ESPERA (no hay particion libre)");
                break;
        }
    }

    private static void mostrarCola(ListaDeTrabajos lista) {
        if (lista.estaVacia()) {
            System.out.println("    (vacia)");
            return;
        }
        for (BCP bcp : lista.toList()) {
            System.out.println("    -> ID=" + bcp.getId()
                    + ", estado=" + bcp.getEstado()
                    + ", PC=" + bcp.getPc()
                    + ", prio=" + bcp.getPrioridad());
        }
    }

    private static void mostrarBCPs(Memoria memoria) {
        System.out.println("BCPs registrados en la zona kernel:");
        for (BCP bcp : memoria.getBCPsRegistrados()) {
            System.out.println("  ID=" + bcp.getId()
                    + ", estado=" + bcp.getEstado()
                    + ", PC=" + bcp.getPc()
                    + ", AC=" + bcp.getAc()
                    + ", duracion=" + bcp.getDuracionSegundos() + "s");
        }
        if (memoria.getCantidadBCPs() == 0) {
            System.out.println("  (ninguno)");
        }
    }
}