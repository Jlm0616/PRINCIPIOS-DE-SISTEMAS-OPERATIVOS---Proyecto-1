package gui;

import modelo.Memoria;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * Panel de configuracion de la memoria principal.
 *
 * Contiene los campos de tamano total y limite kernel/usuario,
 * con auto-sugerencia del 30% para el limite.
 *
 * REGLA: el limite del kernel SIEMPRE debe ser al menos el 30% de la
 * memoria total. Si con ese 30% no caben los BCPs configurados, el
 * sistema lo avisara al inicializar y el usuario debera agrandar la RAM
 * o reducir maxProcesos.
 *
 * No crea objetos Memoria: eso lo hace quien lo use (VentanaConfiguracion).
 */
public class PanelConfigMemoria extends JPanel {

    private JTextField txtTamanoMemoria;
    private JTextField txtLimiteKernel;

    private boolean limiteEditadoManualmente = false;
    private boolean actualizandoAutomaticamente = false;

    /** Porcentaje minimo del kernel respecto a la memoria total. */
    private static final double PORCENTAJE_MINIMO_KERNEL = 0.30;

    /** Tamano maximo permitido de memoria (para evitar valores absurdos). */
    private static final int TAMANO_MAXIMO = 65536;

    /** Posiciones minimas que deben quedar en la zona de usuario. */
    private static final int POSICIONES_MINIMAS_USUARIO = 1;

    /** Cantidad de procesos configurada (para mostrar info en la ayuda). */
    private final int maxProcesos;

    public PanelConfigMemoria(int tamanoActual, int limiteActual, int maxProcesos) {
        this.maxProcesos = maxProcesos;
        construirInterfaz(tamanoActual, limiteActual);
    }

    private void construirInterfaz(int tamanoActual, int limiteActual) {
        setLayout(new GridBagLayout());
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Memoria principal",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(15, 20, 15, 20)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // ==== Etiqueta informativa (arriba, ocupa 2 columnas) ====
        int porcentajeMostrar = (int) (PORCENTAJE_MINIMO_KERNEL * 100);
        JLabel lblInfo = new JLabel("Minimo permitido: " + Memoria.TAMANO_MINIMO
                + "  |  Maximo permitido: " + TAMANO_MAXIMO
                + "  |  Kernel minimo: " + porcentajeMostrar + "%");
        lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblInfo.setForeground(Paleta.TEXTO_NORMAL);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(lblInfo, gbc);
        gbc.gridwidth = 1;

        // ==== Fila 1: Tamano total de memoria ====
        JLabel lblTamano = crearLabelCampo("Tamano total de memoria");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        add(lblTamano, gbc);

        txtTamanoMemoria = crearCampoTexto(String.valueOf(tamanoActual));
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtTamanoMemoria, gbc);

        // ==== Fila 2: Limite Kernel/Usuario ====
        JLabel lblLimite = crearLabelCampo("Limite Kernel/Usuario");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        add(lblLimite, gbc);

        txtLimiteKernel = crearCampoTexto(String.valueOf(limiteActual));
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(txtLimiteKernel, gbc);

        // ==== Fila 3: Texto de ayuda ====
        int kernelMinimoReal = Memoria.getTamanoKernelMinimo(maxProcesos);
        JLabel lblAyuda = new JLabel("<html><body style='width: 420px'>"
                + "Posiciones 0 a (limite-1) = zona <b>Kernel</b>.<br>"
                + "Posiciones limite a (tamano-1) = zona <b>Usuario</b>.<br><br>"
                + "El kernel debe ocupar al menos el <b>" + porcentajeMostrar + "%</b> "
                + "de la memoria.<br>"
                + "Con <b>" + maxProcesos + " procesos</b> configurados, el kernel "
                + "necesita al menos <b>" + kernelMinimoReal + "</b> posiciones.<br><br>"
                + "Si el 30% no alcanza, aumenta la memoria o reduce "
                + "max_procesos en config.txt."
                + "</body></html>");
        lblAyuda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblAyuda.setForeground(new Color(0x60, 0x60, 0x60));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 6, 6, 6);
        add(lblAyuda, gbc);

        // ==== Listeners (igual que antes) ====
        txtTamanoMemoria.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { sugerirLimite(); }
            @Override public void removeUpdate(DocumentEvent e) { sugerirLimite(); }
            @Override public void changedUpdate(DocumentEvent e) { sugerirLimite(); }
        });

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

    /**
     * Crea una etiqueta con el estilo de la paleta (fuente mono, color normal).
     */
    private JLabel crearLabelCampo(String texto) {
        JLabel label = new JLabel(texto + ":");
        label.setFont(Paleta.FUENTE_LABEL_BOLD);
        label.setForeground(Paleta.TEXTO_NORMAL);
        return label;
    }

    /**
     * Crea un campo de texto estilizado (fuente mono, borde verde, padding).
     */
    private JTextField crearCampoTexto(String valorInicial) {
        JTextField campo = new JTextField(valorInicial, 12);
        campo.setFont(Paleta.FUENTE_MONO);
        campo.setForeground(Paleta.TEXTO_NORMAL);
        campo.setBackground(Color.WHITE);
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Paleta.VERDE_PRINCIPAL, 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        return campo;
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
            // el usuario esta escribiendo, ignorar
        }
    }

    /**
     * Valida los campos del panel.
     *
     * Reglas:
     *   1. Memoria y kernel deben ser enteros.
     *   2. Memoria >= Memoria.TAMANO_MINIMO y <= TAMANO_MAXIMO.
     *   3. Kernel < memoria.
     *   4. Kernel >= 30% de la memoria.
     *   5. Debe quedar al menos 1 posicion de usuario.
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
                    + "% de la memoria total (" + minimoKernel + " posiciones para "
                    + tamanoIngresado + " de memoria).");
            return false;
        }

        if (tamanoIngresado - limiteIngresado < POSICIONES_MINIMAS_USUARIO) {
            mostrarError("Debe quedar espacio para al menos " + POSICIONES_MINIMAS_USUARIO
                    + " instruccion(es) en la zona de Usuario.");
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