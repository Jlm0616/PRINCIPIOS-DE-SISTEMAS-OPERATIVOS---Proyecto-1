package gui;

import modelo.MemoriaVirtual;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * Panel de configuracion de la memoria virtual (swap).
 *
 * Contiene el campo de tamano total del swap.
 *
 * No crea objetos MemoriaVirtual: eso lo hace quien lo use
 * (VentanaConfiguracion / VentanaPrincipal).
 */
public class PanelConfigMemoriaVirtual extends JPanel {

    private JTextField txtTamanoMemoriaVirtual;

    private static final int TAMANO_MINIMO = MemoriaVirtual.TAMANO_MINIMO;
    private static final int TAMANO_MAXIMO = 65536;

    public PanelConfigMemoriaVirtual(int tamanoActual) {
        construirInterfaz(tamanoActual);
    }

    private void construirInterfaz(int tamanoActual) {
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        Font fontLabel = new Font("Segoe UI", Font.PLAIN, 13);

        // Etiqueta informativa
        JLabel lblInfo = new JLabel("Minimo permitido: " + TAMANO_MINIMO
                + " | Maximo permitido: " + TAMANO_MAXIMO);
        lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(lblInfo, gbc);
        gbc.gridwidth = 1;

        JLabel lblTamano = new JLabel("Tamano total de memoria virtual:");
        lblTamano.setFont(fontLabel);
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(lblTamano, gbc);

        txtTamanoMemoriaVirtual = new JTextField(String.valueOf(tamanoActual), 10);
        gbc.gridx = 1;
        add(txtTamanoMemoriaVirtual, gbc);

        // Texto de ayuda
        JLabel lblAyuda = new JLabel("<html><body style='width: 320px'>"
                + "Area de swap donde se guardan los procesos suspendidos<br>"
                + "(estados READY_SUSPEND y BLOCKED_SUSPEND).<br>"
                + "Cada proceso suspendido ocupa 1 posicion."
                + "</body></html>");
        lblAyuda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        add(lblAyuda, gbc);
    }

    /**
     * Valida los campos del panel.
     *
     * @return true si todo es valido
     */
    public boolean validar() {
        int tamanoIngresado;

        try {
            tamanoIngresado = Integer.parseInt(txtTamanoMemoriaVirtual.getText().trim());
        } catch (NumberFormatException ex) {
            mostrarError("El tamano de memoria virtual debe ser un numero entero.");
            return false;
        }

        if (tamanoIngresado < TAMANO_MINIMO) {
            mostrarError("El tamano de memoria virtual debe ser al menos "
                    + TAMANO_MINIMO + ".");
            return false;
        }

        if (tamanoIngresado > TAMANO_MAXIMO) {
            mostrarError("El tamano de memoria virtual no puede superar "
                    + TAMANO_MAXIMO + ".");
            return false;
        }

        return true;
    }

    private void mostrarError(String mensaje) {
        javax.swing.JOptionPane.showMessageDialog(this, mensaje,
                "Datos invalidos", javax.swing.JOptionPane.ERROR_MESSAGE);
    }

    public int getTamanoMemoriaVirtual() {
        return Integer.parseInt(txtTamanoMemoriaVirtual.getText().trim());
    }
}