package gui;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import java.awt.Color;

/**
 * Panel que simula la pantalla y el teclado de la minicomputadora.
 *
 * - La "pantalla" muestra los mensajes de salida (INT 10H).
 * - La "consola" permite ingresar valores (INT 09H, numerico 0-255).
 *
 * Cuando el usuario presiona "Enviar", se invoca el callback
 * configurado con setOnEnviar(...), pasandole el valor ingresado.
 */
public class PanelPantalla extends JPanel {

    private JTextArea areaPantalla;
    private JTextField campoEntrada;
    private JButton btnEnviar;
    private Consumer<Integer> onEnviar;

    // Crea el panel y construye su interfaz gráfica.
    public PanelPantalla() {
        construirInterfaz();
    }

    // Construye la interfaz del panel (pantalla + entrada + botón).
    private void construirInterfaz() {
        setLayout(new BorderLayout(5, 5));
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Pantalla",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(5, 5, 5, 5)
        ));

        // --- Area de salida (pantalla) ---
        areaPantalla = new JTextArea();
        areaPantalla.setEditable(false);
        areaPantalla.setFont(Paleta.FUENTE_CONSOLA);
        areaPantalla.setBackground(Color.WHITE);
        areaPantalla.setForeground(Paleta.TEXTO_NORMAL);
        areaPantalla.setLineWrap(true);
        areaPantalla.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(areaPantalla);
        scroll.setPreferredSize(new Dimension(0, 200));
        add(scroll, BorderLayout.CENTER);

        // --- Panel inferior: entrada + boton ---
        JPanel panelEntrada = new JPanel(new BorderLayout(5, 0));
        panelEntrada.setOpaque(false);

        campoEntrada = new JTextField();
        campoEntrada.setFont(Paleta.FUENTE_CONSOLA);
        campoEntrada.setEnabled(false);
        campoEntrada.addActionListener(e -> enviarValor());

        btnEnviar = new JButton("Enviar");
        btnEnviar.setFont(Paleta.FUENTE_BOTON);
        btnEnviar.setBackground(Paleta.VERDE_PRINCIPAL);
        btnEnviar.setForeground(Paleta.TEXTO_CLARO);
        btnEnviar.setFocusPainted(false);
        btnEnviar.setEnabled(false);
        btnEnviar.addActionListener(e -> enviarValor());

        panelEntrada.add(campoEntrada, BorderLayout.CENTER);
        panelEntrada.add(btnEnviar, BorderLayout.EAST);

        add(panelEntrada, BorderLayout.SOUTH);
    }

    /**
     * Agrega una linea a la salida de la pantalla.
     */
    public void agregarMensaje(String linea) {
        areaPantalla.append(linea + "\n");
        // Auto-scroll al final
        areaPantalla.setCaretPosition(areaPantalla.getDocument().getLength());
    }

    /**
     * Limpia toda la pantalla.
     */
    public void limpiar() {
        areaPantalla.setText("");
    }

    /**
     * Habilita o deshabilita el campo de entrada y el boton Enviar.
     * Se habilita cuando un proceso esta BLOCKED esperando INT 09H.
     */
    public void habilitarEntrada(boolean habilitada) {
        campoEntrada.setEnabled(habilitada);
        btnEnviar.setEnabled(habilitada);
        if (habilitada) {
            campoEntrada.requestFocus();
        }
    }

    /**
     * Configura el callback que se invoca cuando el usuario ingresa un valor.
     * El valor debe ser numerico entre 0 y 255.
     */
    public void setOnEnviar(Consumer<Integer> callback) {
        this.onEnviar = callback;
    }

    // Valida el valor ingresado y notifica al callback si es correcto.
    private void enviarValor() {
        String texto = campoEntrada.getText().trim();
        if (texto.isEmpty()) {
            return;
        }

        int valor;
        try {
            valor = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            agregarMensaje(">> Error: '" + texto + "' no es un numero valido.");
            campoEntrada.setText("");
            return;
        }

        if (valor < 0 || valor > 255) {
            agregarMensaje(">> Error: el valor debe estar entre 0 y 255.");
            campoEntrada.setText("");
            return;
        }

        // Mostrar el valor ingresado en la pantalla
        agregarMensaje(">> Ingresar valor: " + valor);

        // Limpiar el campo
        campoEntrada.setText("");

        // Deshabilitar la entrada (hasta el proximo INT 09H)
        habilitarEntrada(false);

        // Notificar al callback
        if (onEnviar != null) {
            onEnviar.accept(valor);
        }
    }
}