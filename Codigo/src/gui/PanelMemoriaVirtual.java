package gui;

import modelo.MemoriaVirtual;

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

/**
 * Panel que muestra el contenido de la memoria virtual (swap).
 *
 * Análogo a PanelMemoria y PanelDisco, pero para el área de swap:
 *   - Arriba: barra de progreso con el porcentaje de uso.
 *   - Centro: tabla con Pos + Contenido.
 *   - Abajo: botón toggle "Mostrar solo ocupadas / Mostrar todas".
 *
 * Cada posición puede contener cualquier Object (valores del BCP,
 * Instruccion, etc.) o estar vacía. El swap guarda el process image
 * completo en posiciones contiguas: BCP (22) + instrucciones (N).
 */
public class PanelMemoriaVirtual extends JPanel {

    private JTable tabla;
    private DefaultTableModel modelo;
    private JButton btnToggle;
    private JProgressBar barraUso;
    private JLabel lblUso;
    private boolean mostrarSoloOcupadas = false;

    private MemoriaVirtual memoriaVirtualActual;

    public PanelMemoriaVirtual() {
        construirInterfaz();
    }

    private void construirInterfaz() {
        setLayout(new BorderLayout(5, 5));
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.MORADO_ACENTO, 2),
                        "Memoria Virtual (Swap)",
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
        modelo = new DefaultTableModel(new Object[]{"Pos", "Contenido"}, 0) {
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

                if (isSelected) {
                    return c;
                }

                c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                c.setForeground(Paleta.TEXTO_NORMAL);
                setFont(Paleta.FUENTE_MONO);
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(tabla);
        add(scroll, BorderLayout.CENTER);

        // === Abajo: botón toggle ===
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

    /**
     * Actualiza la tabla con el contenido del swap.
     *
     * @param memoriaVirtual memoria virtual a mostrar (puede ser null)
     */
    public void actualizar(MemoriaVirtual memoriaVirtual) {
        this.memoriaVirtualActual = memoriaVirtual;
        refrescar();
        actualizarBarra();
    }

    private void refrescar() {
        if (memoriaVirtualActual == null) {
            modelo.setRowCount(0);
            return;
        }

        modelo.setRowCount(0);

        int tamano = memoriaVirtualActual.getTamanoTotal();
        for (int i = 0; i < tamano; i++) {
            Object contenido = memoriaVirtualActual.getContenidoEn(i);
            if (mostrarSoloOcupadas && contenido == null) {
                continue;
            }
            String valor = formatearContenido(contenido);
            modelo.addRow(new Object[]{i, valor});
        }
    }

    private void actualizarBarra() {
        if (memoriaVirtualActual == null) {
            barraUso.setValue(0);
            barraUso.setString("0%");
            lblUso.setText("0 / 0 posiciones usadas");
            return;
        }

        int total = memoriaVirtualActual.getTamanoTotal();
        int usadas = memoriaVirtualActual.getEspacioOcupado();

        int porcentaje = (total > 0) ? (usadas * 100) / total : 0;
        barraUso.setValue(porcentaje);
        barraUso.setString(porcentaje + "%");
        lblUso.setText(usadas + " / " + total + " posiciones usadas");
    }

    private String formatearContenido(Object contenido) {
        if (contenido == null) {
            return "(vacio)";
        }
        // Si es una Instruccion, usar su toString (ej. "LOAD AX")
        // Si es cualquier otro Object, usar su toString
        return contenido.toString();
    }
}