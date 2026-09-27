package gui;

import modelo.Memoria;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * Panel de configuracion de la memoria principal.
 *
 * Contiene los campos de tamano total y limite kernel/usuario,
 * con auto-sugerencia del 20% para el limite.
 *
 * No crea objetos Memoria: eso lo hace quien lo use (VentanaConfiguracion).
 */
public class PanelConfigMemoria extends JPanel {

    private JTextField txtTamanoMemoria;
    private JTextField txtLimiteKernel;

    private boolean limiteEditadoManualmente = false;
    private boolean actualizandoAutomaticamente = false;

    private static final double PORCENTAJE_MINIMO_KERNEL = 0.20;
    private static final int TAMANO_MAXIMO = 65536;
    private static final int POSICIONES_POR_INSTRUCCION = 2;

    public PanelConfigMemoria(int tamanoActual, int limiteActual) {
        construirInterfaz(tamanoActual, limiteActual);
    }

    private void construirInterfaz(int tamanoActual, int limiteActual) {
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        Font fontLabel = new Font("Segoe UI", Font.PLAIN, 13);

        // Etiqueta informativa
        JLabel lblInfo = new JLabel("Minimo permitido: " + Memoria.TAMANO_MINIMO
                + " | Maximo permitido: " + TAMANO_MAXIMO);
        lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(lblInfo, gbc);
        gbc.gridwidth = 1;

        JLabel lblTamano = new JLabel("Tamano total de memoria:");
        lblTamano.setFont(fontLabel);
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(lblTamano, gbc);

        txtTamanoMemoria = new JTextField(String.valueOf(tamanoActual), 10);
        gbc.gridx = 1;
        add(txtTamanoMemoria, gbc);

        JLabel lblLimite = new JLabel("Limite Kernel/Usuario:");
        lblLimite.setFont(fontLabel);
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(lblLimite, gbc);

        txtLimiteKernel = new JTextField(String.valueOf(limiteActual), 10);
        gbc.gridx = 1;
        add(txtLimiteKernel, gbc);

        // Texto de ayuda
        JLabel lblAyuda = new JLabel("<html><body style='width: 320px'>"
                + "Posiciones 0 a (limite-1) = zona Kernel.<br>"
                + "Posiciones limite a (tamano-1) = zona Usuario."
                + "</body></html>");
        lblAyuda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        add(lblAyuda, gbc);

        // Listener del tamaño
        txtTamanoMemoria.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { sugerirLimite(); }
            @Override public void removeUpdate(DocumentEvent e) { sugerirLimite(); }
            @Override public void changedUpdate(DocumentEvent e) { sugerirLimite(); }
        });

        // Listener del límite
        txtLimiteKernel.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { marcarComoManual(); }
            @Override public void removeUpdate(DocumentEvent e) { marcarComoManual(); }
            @Override public void changedUpdate(DocumentEvent e) { marcarComoManual(); }

            private void marcarComoManual() {
                if (!actualizandoAutomaticamente) {
                    limiteEditadoManualmente = true;
                }
            }
        });
    }

    private void sugerirLimite() {
        if (limiteEditadoManualmente) return;
        try {
            int tamano = Integer.parseInt(txtTamanoMemoria.getText().trim());
            int sugerido = (int) Math.ceil(tamano * PORCENTAJE_MINIMO_KERNEL);

            actualizandoAutomaticamente = true;
            try {
                txtLimiteKernel.setText(String.valueOf(sugerido));
            } finally {
                actualizandoAutomaticamente = false;
            }
        } catch (NumberFormatException e) {
            // el usuario está escribiendo
        }
    }

    /**
     * Valida los campos del panel.
     *
     * @return true si todo es valido; false si no (muestra mensaje de error)
     */
    public boolean validar() {
        int tamanoIngresado;
        int limiteIngresado;

        try {
            tamanoIngresado = Integer.parseInt(txtTamanoMemoria.getText().trim());
            limiteIngresado = Integer.parseInt(txtLimiteKernel.getText().trim());
        } catch (NumberFormatException ex) {
            mostrarError("El tamano de memoria y el limite del kernel deben ser numeros enteros.");
            return false;
        }

        if (tamanoIngresado < Memoria.TAMANO_MINIMO) {
            mostrarError("El tamano de memoria debe ser al menos " + Memoria.TAMANO_MINIMO + ".");
            return false;
        }

        if (tamanoIngresado > TAMANO_MAXIMO) {
            mostrarError("El tamano de memoria no puede superar " + TAMANO_MAXIMO + " posiciones.");
            return false;
        }

        if (limiteIngresado >= tamanoIngresado) {
            mostrarError("El limite del Kernel no puede ser igual o mayor al tamano total.");
            return false;
        }

        int minimoKernel = (int) Math.ceil(tamanoIngresado * PORCENTAJE_MINIMO_KERNEL);
        if (limiteIngresado < minimoKernel) {
            int porcentajeMostrar = (int) (PORCENTAJE_MINIMO_KERNEL * 100);
            mostrarError("El limite del Kernel debe ser al menos el " + porcentajeMostrar
                    + "% de la memoria total.");
            return false;
        }

        if (tamanoIngresado - limiteIngresado < POSICIONES_POR_INSTRUCCION) {
            mostrarError("Debe quedar espacio para al menos 1 instruccion en la zona de Usuario.");
            return false;
        }

        return true;
    }

    private void mostrarError(String mensaje) {
        javax.swing.JOptionPane.showMessageDialog(this, mensaje,
                "Datos invalidos", javax.swing.JOptionPane.ERROR_MESSAGE);
    }

    public int getTamanoMemoria() {
        return Integer.parseInt(txtTamanoMemoria.getText().trim());
    }

    public int getLimiteKernel() {
        return Integer.parseInt(txtLimiteKernel.getText().trim());
    }
}