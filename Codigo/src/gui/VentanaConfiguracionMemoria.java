package gui;

import modelo.Memoria;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

/**
 * Ventana modal cuya única responsabilidad es pedir al usuario
 * el tamaño de memoria y el límite Kernel/Usuario, validarlos,
 * y dejarlos disponibles mediante getters si el usuario confirma.
 *
 * No crea objetos Memoria ni CPU: esa decisión le corresponde
 * a quien abre esta ventana (VentanaPrincipal).
 *
 * Reglas de validación:
 *   - Tamaño total >= Memoria.TAMANO_MINIMO (128)
 *   - Tamaño total <= 65536 (2^16, límite práctico del simulador)
 *   - Límite Kernel >= 20% del tamaño total
 *   - Límite Kernel < tamaño total
 *   - Zona Usuario >= 2 posiciones (1 instrucción)
 *
 * El campo "límite Kernel" se auto-sugiere como el 20% del tamaño
 * mientras el usuario no lo haya editado manualmente.
 */
public class VentanaConfiguracionMemoria extends JDialog {

    /* ==================== CAMPOS DEL FORMULARIO ==================== */

    private JTextField txtTamanoMemoria;
    private JTextField txtLimiteKernel;

    /* ==================== ESTADO ==================== */

    private int tamanoMemoria;                        // valor confirmado por el usuario
    private int limiteKernel;                         // valor confirmado por el usuario
    private boolean confirmado;                       // true si el usuario pulsó Aceptar
    private boolean limiteEditadoManualmente = false; // si el usuario tocó el campo límite
    private boolean actualizandoAutomaticamente = false; // evita marcar como manual la auto-sugerencia

    /* ==================== CONSTANTES ==================== */

    private static final double PORCENTAJE_MINIMO_KERNEL = 0.20; // mínimo del kernel
    private static final int TAMANO_MAXIMO = 65536;              // 2^16
    private static final int POSICIONES_POR_INSTRUCCION = 2;     // cada instrucción ocupa 2 posiciones

    /**
     * Crea la ventana modal de configuración.
     *
     * @param propietario   ventana padre (para centrado y modalidad)
     * @param tamanoActual  tamaño de memoria actual (valor inicial del campo)
     * @param limiteActual  límite Kernel/Usuario actual (valor inicial del campo)
     */
    public VentanaConfiguracionMemoria(Frame propietario, int tamanoActual, int limiteActual) {
        super(propietario, "Configurar memoria", true);
        this.confirmado = false;
        this.tamanoMemoria = tamanoActual;
        this.limiteKernel = limiteActual;

        construirInterfaz(tamanoActual, limiteActual);
        pack();
        setResizable(false);
        setLocationRelativeTo(propietario);
    }

    /**
     * Construye y organiza todos los componentes de la interfaz:
     * etiquetas, campos de texto, texto de ayuda, botones y listeners.
     *
     * @param tamanoActual valor inicial del campo "tamaño total"
     * @param limiteActual valor inicial del campo "límite Kernel"
     */
    private void construirInterfaz(int tamanoActual, int limiteActual) {
        JPanel panelFormulario = new JPanel(new GridBagLayout());
        panelFormulario.setBorder(new EmptyBorder(20, 20, 10, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        Font fontLabel = new Font("Segoe UI", Font.PLAIN, 13);

        // Etiqueta informativa con el mínimo y el máximo permitidos
        JLabel lblInfo = new JLabel("Minimo permitido: " + Memoria.TAMANO_MINIMO
                + " | Maximo permitido: " + TAMANO_MAXIMO);
        lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panelFormulario.add(lblInfo, gbc);
        gbc.gridwidth = 1;

        JLabel lblTamano = new JLabel("Tamano total de memoria:");
        lblTamano.setFont(fontLabel);
        gbc.gridx = 0;
        gbc.gridy = 1;
        panelFormulario.add(lblTamano, gbc);

        txtTamanoMemoria = new JTextField(String.valueOf(tamanoActual), 8);
        gbc.gridx = 1;
        panelFormulario.add(txtTamanoMemoria, gbc);

        JLabel lblLimite = new JLabel("Limite Kernel/Usuario:");
        lblLimite.setFont(fontLabel);
        gbc.gridx = 0;
        gbc.gridy = 2;
        panelFormulario.add(lblLimite, gbc);

        txtLimiteKernel = new JTextField(String.valueOf(limiteActual), 8);
        gbc.gridx = 1;
        panelFormulario.add(txtLimiteKernel, gbc);

        // Texto de ayuda: explica qué significa el límite con un ejemplo visual
        JLabel lblAyuda = new JLabel("<html><body style='width: 260px'>"
                + "Posiciones 0 a (limite-1) = zona Kernel.<br>"
                + "Posiciones limite a (tamano-1) = zona Usuario."
                + "</body></html>");
        lblAyuda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panelFormulario.add(lblAyuda, gbc);

        JButton btnAceptar = new JButton("Aceptar");
        JButton btnCancelar = new JButton("Cancelar");
        btnAceptar.addActionListener(e -> validarYConfirmar());
        btnCancelar.addActionListener(e -> {
            confirmado = false;
            dispose();
        });

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        panelBotones.setBorder(new EmptyBorder(0, 20, 15, 20));
        panelBotones.add(btnCancelar);
        panelBotones.add(btnAceptar);

        setLayout(new BorderLayout());
        add(panelFormulario, BorderLayout.CENTER);
        add(panelBotones, BorderLayout.SOUTH);

        // Listener del tamaño: auto-sugiere el límite Kernel mientras no se edite manualmente
        txtTamanoMemoria.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { sugerirLimite(); }
            @Override
            public void removeUpdate(DocumentEvent e) { sugerirLimite(); }
            @Override
            public void changedUpdate(DocumentEvent e) { sugerirLimite(); }
        });

        // Listener del límite: marca que el usuario lo editó manualmente
        txtLimiteKernel.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { marcarComoManual(); }
            @Override
            public void removeUpdate(DocumentEvent e) { marcarComoManual(); }
            @Override
            public void changedUpdate(DocumentEvent e) { marcarComoManual(); }

            private void marcarComoManual() {
                if (!actualizandoAutomaticamente) {
                    limiteEditadoManualmente = true;
                }
            }
        });
    }

    /**
     * Calcula y escribe en el campo "límite Kernel" una sugerencia del 20%
     * del tamaño actual, SOLO si el usuario no lo ha editado manualmente.
     *
     * Si el campo de tamaño no es un número válido (el usuario está
     * escribiendo), se ignora silenciosamente.
     */
    private void sugerirLimite() {
        if (limiteEditadoManualmente) {
            return;
        }
        try {
            int tamano = Integer.parseInt(txtTamanoMemoria.getText().trim());
            int sugerido = (int) Math.ceil(tamano * PORCENTAJE_MINIMO_KERNEL);

            // Se activa la bandera para que el listener del límite no lo
            // marque como edición manual cuando lo cambiamos desde código.
            // Se usa try/finally para garantizar que la bandera se
            // desactive incluso si setText lanzara una excepción.
            actualizandoAutomaticamente = true;
            try {
                txtLimiteKernel.setText(String.valueOf(sugerido));
            } finally {
                actualizandoAutomaticamente = false;
            }
        } catch (NumberFormatException e) {
            // mientras el usuario escribe, el texto puede quedar incompleto momentáneamente; se ignora
        }
    }

    /**
     * Valida los campos del formulario y, si todo es correcto,
     * guarda los valores y cierra la ventana con confirmado = true.
     *
     * Si alguna validación falla, muestra un mensaje de error
     * y deja la ventana abierta para que el usuario corrija.
     *
     * Orden de validaciones:
     *   1. Ambas entradas son enteros.
     *   2. Tamaño >= TAMANO_MINIMO.
     *   3. Tamaño <= TAMANO_MAXIMO.
     *   4. Límite Kernel < tamaño total.
     *   5. Límite Kernel >= 20% del tamaño.
     *   6. Zona Usuario >= 1 instrucción (2 posiciones).
     */
    private void validarYConfirmar() {
        int tamanoIngresado;
        int limiteIngresado;

        try {
            tamanoIngresado = Integer.parseInt(txtTamanoMemoria.getText().trim());
            limiteIngresado = Integer.parseInt(txtLimiteKernel.getText().trim());
        } catch (NumberFormatException ex) {
            mostrarError("El tamano de memoria y el limite del kernel deben ser numeros enteros.");
            return;
        }

        if (tamanoIngresado < Memoria.TAMANO_MINIMO) {
            mostrarError("El tamano de memoria debe ser al menos " + Memoria.TAMANO_MINIMO + ".");
            return;
        }

        if (tamanoIngresado > TAMANO_MAXIMO) {
            mostrarError("El tamano de memoria no puede superar " + TAMANO_MAXIMO + " posiciones\n"
                    + "(limite practico para este simulador educativo).");
            return;
        }

        if (limiteIngresado >= tamanoIngresado) {
            mostrarError("El limite del Kernel no puede ser igual o mayor al tamano total,\n"
                    + "porque no quedaria espacio para la zona de Usuario.");
            return;
        }

        int minimoKernel = (int) Math.ceil(tamanoIngresado * PORCENTAJE_MINIMO_KERNEL);
        if (limiteIngresado < minimoKernel) {
            int porcentajeMostrar = (int) (PORCENTAJE_MINIMO_KERNEL * 100);
            mostrarError("El limite del Kernel debe ser al menos el " + porcentajeMostrar
                    + "% de la memoria total.\n"
                    + "Para " + tamanoIngresado + " posiciones, el minimo es " + minimoKernel + ".");
            return;
        }

        if (tamanoIngresado - limiteIngresado < POSICIONES_POR_INSTRUCCION) {
            mostrarError("Debe quedar espacio para al menos 1 instruccion ("
                    + POSICIONES_POR_INSTRUCCION + " posiciones) en la zona de Usuario.");
            return;
        }

        this.tamanoMemoria = tamanoIngresado;
        this.limiteKernel = limiteIngresado;
        this.confirmado = true;
        dispose();
    }

    /**
     * Muestra un mensaje de error estándar en un JOptionPane.
     *
     * @param mensaje texto a mostrar
     */
    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos invalidos", JOptionPane.ERROR_MESSAGE);
    }

    /** @return true si el usuario confirmó con "Aceptar"; false si canceló. */
    public boolean isConfirmado() {
        return confirmado;
    }

    /** @return el tamaño de memoria confirmado por el usuario. */
    public int getTamanoMemoria() {
        return tamanoMemoria;
    }

    /** @return el límite Kernel/Usuario confirmado por el usuario. */
    public int getLimiteKernel() {
        return limiteKernel;
    }
}