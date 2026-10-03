package gui;

import modelo.Disco;

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
 * Configura los 3 parametros del disco:
 *   1. Tamaño total del disco (indice + swap + archivos).
 *   2. Cantidad maxima de archivos en el indice.
 *   3. Tamaño de la memoria virtual (swap).
 *
 * El disco se divide en 3 zonas:
 *   [Indice] [Memoria Virtual] [Archivos]
 *
 * No crea objetos Disco: eso lo hace quien lo use (VentanaConfiguracion).
 */
public class PanelConfigDisco extends JPanel {

    private JTextField txtTamanoDisco;
    private JTextField txtMaxArchivos;
    private JTextField txtTamanoSwap;

    private static final int TAMANO_DISCO_MINIMO = Disco.TAMANO_MINIMO;
    private static final int TAMANO_DISCO_MAXIMO = 65536;

    private static final int MAX_ARCHIVOS_MINIMO = Disco.MAX_ARCHIVOS_MINIMO;
    private static final int MAX_ARCHIVOS_MAXIMO = 100;

    private static final int TAMANO_SWAP_MINIMO = Disco.TAMANO_SWAP_MINIMO;
    private static final int TAMANO_SWAP_MAXIMO = 65536;

    public PanelConfigDisco(int tamanoActual, int maxArchivosActual, int swapActual) {
        construirInterfaz(tamanoActual, maxArchivosActual, swapActual);
    }

    private void construirInterfaz(int tamanoActual, int maxArchivosActual, int swapActual) {
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        Font fontLabel = new Font("Segoe UI", Font.PLAIN, 13);

        // Etiqueta informativa
        JLabel lblInfo = new JLabel("Disco minimo: " + TAMANO_DISCO_MINIMO
                + " | Swap minimo: " + TAMANO_SWAP_MINIMO
                + " | Max archivos: " + MAX_ARCHIVOS_MAXIMO);
        lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(lblInfo, gbc);
        gbc.gridwidth = 1;

        // ==== Tamaño total del disco ====
        JLabel lblTamano = new JLabel("Tamano total del disco:");
        lblTamano.setFont(fontLabel);
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(lblTamano, gbc);

        txtTamanoDisco = new JTextField(String.valueOf(tamanoActual), 10);
        gbc.gridx = 1;
        add(txtTamanoDisco, gbc);

        // ==== Máximo de archivos ====
        JLabel lblMaxArch = new JLabel("Cantidad maxima de archivos:");
        lblMaxArch.setFont(fontLabel);
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(lblMaxArch, gbc);

        txtMaxArchivos = new JTextField(String.valueOf(maxArchivosActual), 10);
        gbc.gridx = 1;
        add(txtMaxArchivos, gbc);

        // ==== Tamaño del swap ====
        JLabel lblSwap = new JLabel("Tamano de memoria virtual (swap):");
        lblSwap.setFont(fontLabel);
        gbc.gridx = 0;
        gbc.gridy = 3;
        add(lblSwap, gbc);

        txtTamanoSwap = new JTextField(String.valueOf(swapActual), 10);
        gbc.gridx = 1;
        add(txtTamanoSwap, gbc);

        // Texto de ayuda
        JLabel lblAyuda = new JLabel("<html><body style='width: 380px'>"
                + "El disco se divide en 3 zonas:<br>"
                + "&nbsp;&nbsp;<b>Indice:</b> maxArchivos * 3 posiciones "
                + "(nombre, inicio, fin).<br>"
                + "&nbsp;&nbsp;<b>Memoria Virtual:</b> tamaño del swap.<br>"
                + "&nbsp;&nbsp;<b>Archivos:</b> resto del disco.<br><br>"
                + "Los archivos .asm se guardan en la zona de archivos.<br>"
                + "Los procesos suspendidos van a la zona de swap."
                + "</body></html>");
        lblAyuda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0;
        gbc.gridy = 4;
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
        int maxArchivosIngresado;
        int swapIngresado;

        try {
            tamanoIngresado = Integer.parseInt(txtTamanoDisco.getText().trim());
            maxArchivosIngresado = Integer.parseInt(txtMaxArchivos.getText().trim());
            swapIngresado = Integer.parseInt(txtTamanoSwap.getText().trim());
        } catch (NumberFormatException ex) {
            mostrarError("Todos los campos deben ser numeros enteros.");
            return false;
        }

        // Validar tamaño del disco
        if (tamanoIngresado < TAMANO_DISCO_MINIMO) {
            mostrarError("El tamaño del disco debe ser al menos " + TAMANO_DISCO_MINIMO + ".");
            return false;
        }
        if (tamanoIngresado > TAMANO_DISCO_MAXIMO) {
            mostrarError("El tamaño del disco no puede superar " + TAMANO_DISCO_MAXIMO + ".");
            return false;
        }

        // Validar maxArchivos
        if (maxArchivosIngresado < MAX_ARCHIVOS_MINIMO) {
            mostrarError("Debe haber al menos " + MAX_ARCHIVOS_MINIMO + " archivo.");
            return false;
        }
        if (maxArchivosIngresado > MAX_ARCHIVOS_MAXIMO) {
            mostrarError("El maximo de archivos no puede superar " + MAX_ARCHIVOS_MAXIMO + ".");
            return false;
        }

        // Validar swap
        if (swapIngresado < TAMANO_SWAP_MINIMO) {
            mostrarError("El tamaño de la memoria virtual debe ser al menos "
                    + TAMANO_SWAP_MINIMO + ".");
            return false;
        }
        if (swapIngresado > TAMANO_SWAP_MAXIMO) {
            mostrarError("El tamaño de la memoria virtual no puede superar "
                    + TAMANO_SWAP_MAXIMO + ".");
            return false;
        }

        // Validar que las 3 zonas caben en el disco
        int espacioIndice = maxArchivosIngresado * Disco.POSICIONES_POR_ENTRADA_INDICE;
        int espacioOcupado = espacioIndice + swapIngresado;
        int espacioArchivos = tamanoIngresado - espacioOcupado;

        if (espacioArchivos < Disco.ESPACIO_ARCHIVOS_MINIMO) {
            mostrarError("El disco es muy pequeño:\n"
                    + "  Indice (" + maxArchivosIngresado + " archivos x 3) = " + espacioIndice + "\n"
                    + "  Swap = " + swapIngresado + "\n"
                    + "  Total ocupado = " + espacioOcupado + "\n"
                    + "  Espacio para archivos = " + espacioArchivos
                    + " (minimo " + Disco.ESPACIO_ARCHIVOS_MINIMO + ")\n\n"
                    + "Aumenta el tamaño del disco o reduce maxArchivos/swap.");
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

    public int getMaxArchivos() {
        return Integer.parseInt(txtMaxArchivos.getText().trim());
    }

    public int getTamanoSwap() {
        return Integer.parseInt(txtTamanoSwap.getText().trim());
    }
}