package App;

import gui.VentanaPrincipal;
import javax.swing.SwingUtilities;

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
        // invokeLater garantiza que la UI se construya en el EDT,
        // evitando condiciones de carrera típicas de Swing.
        SwingUtilities.invokeLater(() -> {
            VentanaPrincipal ventana = new VentanaPrincipal();
            ventana.setVisible(true);
        });
    }
}