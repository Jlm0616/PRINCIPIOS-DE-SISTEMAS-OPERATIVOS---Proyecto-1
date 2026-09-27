package gui;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Panel que muestra el contenido del disco.
 *
 * Arriba: barra de progreso con el porcentaje de uso.
 * Centro: tabla con el indice de archivos.
 * Abajo: boton toggle "Mostrar solo ocupadas / Mostrar todas".
 */
public class PanelDisco extends JPanel {

    private JTable tabla;
    private DefaultTableModel modelo;
    private JButton btnToggle;
    private JProgressBar barraUso;
    private JLabel lblUso;
    private boolean mostrarSoloOcupadas = false;

    private List<String[]> entradas;
    private int tamanoTotal = 512;

    public PanelDisco() {
        construirInterfaz();
    }

    private void construirInterfaz() {
        setLayout(new BorderLayout(5, 5));
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.MORADO_ACENTO, 2),
                        "Valor en disco",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.MORADO_ACENTO
                ),
                new EmptyBorder(5, 5, 5, 5)
        ));

        // === Arriba: barra de uso ===
        JPanel panelUso = new JPanel(new BorderLayout(5, 2));
        panelUso.setOpaque(false);

        barraUso = new JProgressBar(0, 100);
        barraUso.setStringPainted(true);
        barraUso.setForeground(Paleta.MORADO_ACENTO);
        barraUso.setBackground(new Color(0xE0, 0xE0, 0xE0));
        barraUso.setFont(Paleta.FUENTE_LABEL_BOLD);
        barraUso.setPreferredSize(new Dimension(0, 22));
        barraUso.setValue(0);
        barraUso.setString("0%");

        lblUso = new JLabel("0 / 0 posiciones usadas");
        lblUso.setFont(Paleta.FUENTE_LABEL);
        lblUso.setForeground(Paleta.TEXTO_NORMAL);

        panelUso.add(barraUso, BorderLayout.CENTER);
        panelUso.add(lblUso, BorderLayout.SOUTH);

        add(panelUso, BorderLayout.NORTH);

        // === Centro: tabla ===
        modelo = new DefaultTableModel(new Object[]{"Pos", "Valor"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabla = new JTable(modelo);
        tabla.setFont(Paleta.FUENTE_MONO);
        tabla.setRowHeight(22);
        tabla.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        tabla.setShowGrid(true);
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setFont(Paleta.FUENTE_LABEL_BOLD);

        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(tabla);
        add(scroll, BorderLayout.CENTER);

        // === Abajo: boton toggle ===
        btnToggle = new JButton("Mostrar solo ocupadas");
        btnToggle.setFont(Paleta.FUENTE_BOTON);
        btnToggle.setBackground(Paleta.MORADO_ACENTO);
        btnToggle.setForeground(Paleta.TEXTO_CLARO);
        btnToggle.setFocusPainted(false);
        btnToggle.addActionListener(e -> {
            mostrarSoloOcupadas = !mostrarSoloOcupadas;
            btnToggle.setText(mostrarSoloOcupadas
                    ? "Mostrar todas" : "Mostrar solo ocupadas");
            refrescar();
        });

        JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panelBoton.setOpaque(false);
        panelBoton.add(btnToggle);
        add(panelBoton, BorderLayout.SOUTH);
    }

    public void actualizar(List<String[]> indice, int tamanoTotal) {
        this.entradas = indice;
        this.tamanoTotal = tamanoTotal;
        refrescar();
        actualizarBarra();
    }

    private void refrescar() {
        modelo.setRowCount(0);

        if (entradas == null) {
            return;
        }

        for (int i = 0; i < entradas.size(); i++) {
            String[] entrada = entradas.get(i);
            String nombre = entrada[0];
            String direccion = entrada[1];
            modelo.addRow(new Object[]{i, nombre + "  ->  " + direccion});
        }

        if (!mostrarSoloOcupadas) {
            for (int i = entradas.size(); i < tamanoTotal; i++) {
                modelo.addRow(new Object[]{i, "(vacio)"});
            }
        }
    }

    private void actualizarBarra() {
        int usadas = (entradas != null) ? entradas.size() : 0;
        int porcentaje = (tamanoTotal > 0) ? (usadas * 100) / tamanoTotal : 0;
        barraUso.setValue(porcentaje);
        barraUso.setString(porcentaje + "%");
        lblUso.setText(usadas + " / " + tamanoTotal + " posiciones usadas");
    }
}