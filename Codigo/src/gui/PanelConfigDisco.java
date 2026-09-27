package gui;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * Panel de configuracion del disco (almacenamiento secundario).
 *
 * Contiene el campo de tamano total del disco.
 *
 * No crea objetos del disco: eso lo hace quien lo use (VentanaConfiguracion).
 */
public class PanelConfigDisco extends JPanel {

    private JTextField txtTamanoDisco;

    private static final int TAMANO_MINIMO = 64;
    private static final int TAMANO_MAXIMO = 65536;

    public PanelConfigDisco(int tamanoActual) {
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

        JLabel lblTamano = new JLabel("Tamano total del disco:");
        lblTamano.setFont(fontLabel);
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(lblTamano, gbc);

        txtTamanoDisco = new JTextField(String.valueOf(tamanoActual), 10);
        gbc.gridx = 1;
        add(txtTamanoDisco, gbc);

        // Texto de ayuda
        JLabel lblAyuda = new JLabel("<html><body style='width: 320px'>"
                + "El disco almacena los archivos .asm de los programas.<br>"
                + "En los primeros registros se guarda el indice de archivos."
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
            tamanoIngresado = Integer.parseInt(txtTamanoDisco.getText().trim());
        } catch (NumberFormatException ex) {
            mostrarError("El tamano del disco debe ser un numero entero.");
            return false;
        }

        if (tamanoIngresado < TAMANO_MINIMO) {
            mostrarError("El tamano del disco debe ser al menos " + TAMANO_MINIMO + ".");
            return false;
        }

        if (tamanoIngresado > TAMANO_MAXIMO) {
            mostrarError("El tamano del disco no puede superar " + TAMANO_MAXIMO + ".");
            return false;
        }

        return true;
    }

    private void mostrarError(String mensaje) {
        javax.swing.JOptionPane.showMessageDialog(this, mensaje,
                "Datos invalidos", javax.swing.JOptionPane.ERROR_MESSAGE);
    }

    public int getTamanoDisco() {
        return Integer.parseInt(txtTamanoDisco.getText().trim());
    }
}