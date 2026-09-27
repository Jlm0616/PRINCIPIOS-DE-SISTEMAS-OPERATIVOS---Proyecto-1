package gui;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Panel que muestra el uso de los recursos del sistema:
 *   - Memoria principal (usadas / totales posiciones).
 *   - Disco (usadas / totales posiciones).
 *
 * Cada recurso tiene una barra de progreso con el porcentaje
 * y una etiqueta con el detalle numerico.
 */
public class PanelRecursos extends JPanel {

    private JProgressBar barraMemoria;
    private JLabel lblMemoria;
    private JProgressBar barraDisco;
    private JLabel lblDisco;

    public PanelRecursos() {
        construirInterfaz();
    }

    private void construirInterfaz() {
        setLayout(new GridBagLayout());
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Recursos",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(10, 10, 10, 10)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1.0;

        // ===== MEMORIA =====
        JLabel lblTituloMemoria = new JLabel("Memoria principal");
        lblTituloMemoria.setFont(Paleta.FUENTE_LABEL_BOLD);
        lblTituloMemoria.setForeground(Paleta.VERDE_OSCURO);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        add(lblTituloMemoria, gbc);

        barraMemoria = new JProgressBar(0, 100);
        barraMemoria.setStringPainted(true);
        barraMemoria.setForeground(Paleta.VERDE_PRINCIPAL);
        barraMemoria.setBackground(new Color(0xE0, 0xE0, 0xE0));
        barraMemoria.setFont(Paleta.FUENTE_LABEL_BOLD);
        barraMemoria.setPreferredSize(new Dimension(0, 26));
        barraMemoria.setValue(0);
        barraMemoria.setString("0%");

        gbc.gridy = 1;
        add(barraMemoria, gbc);

        lblMemoria = new JLabel("0 / 0 posiciones usadas");
        lblMemoria.setFont(Paleta.FUENTE_LABEL);
        lblMemoria.setForeground(Paleta.TEXTO_NORMAL);

        gbc.gridy = 2;
        add(lblMemoria, gbc);

        // ===== DISCO =====
        JLabel lblTituloDisco = new JLabel("Disco");
        lblTituloDisco.setFont(Paleta.FUENTE_LABEL_BOLD);
        lblTituloDisco.setForeground(Paleta.MORADO_ACENTO);

        gbc.gridy = 3;
        gbc.insets = new Insets(15, 5, 5, 5);
        add(lblTituloDisco, gbc);

        barraDisco = new JProgressBar(0, 100);
        barraDisco.setStringPainted(true);
        barraDisco.setForeground(Paleta.MORADO_ACENTO);
        barraDisco.setBackground(new Color(0xE0, 0xE0, 0xE0));
        barraDisco.setFont(Paleta.FUENTE_LABEL_BOLD);
        barraDisco.setPreferredSize(new Dimension(0, 26));
        barraDisco.setValue(0);
        barraDisco.setString("0%");

        gbc.gridy = 4;
        gbc.insets = new Insets(5, 5, 5, 5);
        add(barraDisco, gbc);

        lblDisco = new JLabel("0 / 0 posiciones usadas");
        lblDisco.setFont(Paleta.FUENTE_LABEL);
        lblDisco.setForeground(Paleta.TEXTO_NORMAL);

        gbc.gridy = 5;
        add(lblDisco, gbc);
    }

    public void actualizarMemoria(int usadas, int totales) {
        int porcentaje = (totales > 0) ? (usadas * 100) / totales : 0;
        barraMemoria.setValue(porcentaje);
        barraMemoria.setString(porcentaje + "%");
        lblMemoria.setText(usadas + " / " + totales + " posiciones usadas");
    }

    public void actualizarDisco(int usadas, int totales) {
        int porcentaje = (totales > 0) ? (usadas * 100) / totales : 0;
        barraDisco.setValue(porcentaje);
        barraDisco.setString(porcentaje + "%");
        lblDisco.setText(usadas + " / " + totales + " posiciones usadas");
    }
}