package gui;

import modelo.Disco;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
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
 * El disco tiene 3 zonas (según el profe):
 *   1. Índice de archivos (nombre, inicio, fin)
 *   2. Memoria virtual (swap)
 *   3. Archivos
 *
 * Arriba: barra de uso TOTAL del disco.
 * Centro: pestañas con cada zona.
 * La columna "Pos" siempre muestra la posición REAL del disco.
 */
public class PanelDisco extends JPanel {

    private JTabbedPane tabs;

    // Barra de uso TOTAL (arriba)
    private JProgressBar barraUsoTotal;
    private JLabel lblUsoTotal;

    // ==== Pestaña 1: Índice ====
    private JTable tablaIndice;
    private DefaultTableModel modeloIndice;

    // ==== Pestaña 2: Memoria Virtual ====
    private JTable tablaSwap;
    private DefaultTableModel modeloSwap;
    private JButton btnToggleSwap;
    private boolean mostrarSoloOcupadasSwap = false;

    // ==== Pestaña 3: Archivos ====
    private JTable tablaArchivos;
    private DefaultTableModel modeloArchivos;
    private JButton btnToggleArchivos;
    private boolean mostrarSoloOcupadasArchivos = false;

    private Disco discoActual;

    public PanelDisco() {
        construirInterfaz();
    }

    private void construirInterfaz() {
        setLayout(new BorderLayout(5, 5));
        setBackground(Paleta.FONDO_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.MORADO_ACENTO, 2),
                        "Disco",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.MORADO_ACENTO
                ),
                new EmptyBorder(5, 5, 5, 5)
        ));

        // ==== Arriba: barra de uso TOTAL del disco ====
        JPanel panelUso = new JPanel(new BorderLayout(5, 2));
        panelUso.setOpaque(false);

        barraUsoTotal = new JProgressBar(0, 100);
        barraUsoTotal.setStringPainted(true);
        barraUsoTotal.setForeground(Paleta.MORADO_ACENTO);
        barraUsoTotal.setBackground(new Color(0xE0, 0xE0, 0xE0));
        barraUsoTotal.setFont(Paleta.FUENTE_LABEL_BOLD);
        barraUsoTotal.setPreferredSize(new Dimension(0, 22));
        barraUsoTotal.setValue(0);
        barraUsoTotal.setString("0%");

        lblUsoTotal = new JLabel("0 / 0 posiciones usadas del disco");
        lblUsoTotal.setFont(Paleta.FUENTE_LABEL);
        lblUsoTotal.setForeground(Paleta.TEXTO_NORMAL);

        panelUso.add(barraUsoTotal, BorderLayout.CENTER);
        panelUso.add(lblUsoTotal, BorderLayout.SOUTH);

        add(panelUso, BorderLayout.NORTH);

        // ==== Centro: pestañas ====
        tabs = new JTabbedPane();
        tabs.setFont(Paleta.FUENTE_LABEL_BOLD);

        tabs.addTab("Índice", crearPanelIndice());
        tabs.addTab("Memoria Virtual", crearPanelSwap());
        tabs.addTab("Archivos", crearPanelArchivos());

        add(tabs, BorderLayout.CENTER);
    }

    /* ==================== PESTAÑA 1: ÍNDICE ==================== */

    private JPanel crearPanelIndice() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setOpaque(false);

        modeloIndice = new DefaultTableModel(
                new Object[]{"Pos", "Nombre", "Inicio", "Fin"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tablaIndice = crearTabla(modeloIndice);
        panel.add(new JScrollPane(tablaIndice), BorderLayout.CENTER);

        return panel;
    }

    /* ==================== PESTAÑA 2: MEMORIA VIRTUAL ==================== */

    private JPanel crearPanelSwap() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setOpaque(false);

        modeloSwap = new DefaultTableModel(new Object[]{"Pos", "Contenido"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tablaSwap = crearTabla(modeloSwap);
        panel.add(new JScrollPane(tablaSwap), BorderLayout.CENTER);

        btnToggleSwap = new JButton("Mostrar solo ocupadas");
        btnToggleSwap.setFont(Paleta.FUENTE_BOTON);
        btnToggleSwap.setBackground(Paleta.MORADO_ACENTO);
        btnToggleSwap.setForeground(Paleta.TEXTO_CLARO);
        btnToggleSwap.setFocusPainted(false);
        btnToggleSwap.addActionListener(e -> {
            mostrarSoloOcupadasSwap = !mostrarSoloOcupadasSwap;
            btnToggleSwap.setText(mostrarSoloOcupadasSwap
                    ? "Mostrar todas" : "Mostrar solo ocupadas");
            refrescarSwap();
        });

        JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panelBoton.setOpaque(false);
        panelBoton.add(btnToggleSwap);
        panel.add(panelBoton, BorderLayout.SOUTH);

        return panel;
    }

    /* ==================== PESTAÑA 3: ARCHIVOS ==================== */

    private JPanel crearPanelArchivos() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setOpaque(false);

        modeloArchivos = new DefaultTableModel(new Object[]{"Pos", "Contenido"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tablaArchivos = crearTabla(modeloArchivos);
        panel.add(new JScrollPane(tablaArchivos), BorderLayout.CENTER);

        btnToggleArchivos = new JButton("Mostrar solo ocupadas");
        btnToggleArchivos.setFont(Paleta.FUENTE_BOTON);
        btnToggleArchivos.setBackground(Paleta.MORADO_ACENTO);
        btnToggleArchivos.setForeground(Paleta.TEXTO_CLARO);
        btnToggleArchivos.setFocusPainted(false);
        btnToggleArchivos.addActionListener(e -> {
            mostrarSoloOcupadasArchivos = !mostrarSoloOcupadasArchivos;
            btnToggleArchivos.setText(mostrarSoloOcupadasArchivos
                    ? "Mostrar todas" : "Mostrar solo ocupadas");
            refrescarArchivos();
        });

        JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panelBoton.setOpaque(false);
        panelBoton.add(btnToggleArchivos);
        panel.add(panelBoton, BorderLayout.SOUTH);

        return panel;
    }

    /* ==================== TABLA GENÉRICA ==================== */

    private JTable crearTabla(DefaultTableModel modelo) {
        JTable tabla = new JTable(modelo);
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
                if (isSelected) return c;
                c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                c.setForeground(Paleta.TEXTO_NORMAL);
                setFont(Paleta.FUENTE_MONO);
                return c;
            }
        });

        return tabla;
    }

    /* ==================== ACTUALIZACIÓN ==================== */

    /**
     * Actualiza la barra total y las 3 pestañas con el contenido del disco.
     */
    public void actualizar(Disco disco) {
        this.discoActual = disco;
        refrescarUsoTotal();
        refrescarIndice();
        refrescarSwap();
        refrescarArchivos();
    }

    /* ==================== USO TOTAL ==================== */

    private void refrescarUsoTotal() {
        if (discoActual == null) {
            barraUsoTotal.setValue(0);
            barraUsoTotal.setString("0%");
            lblUsoTotal.setText("0 / 0 posiciones usadas del disco");
            return;
        }

        int total = discoActual.getTamanoTotal();
        int usadas = discoActual.getEspacioOcupado();
        int porcentaje = (total > 0) ? (usadas * 100) / total : 0;

        barraUsoTotal.setValue(porcentaje);
        barraUsoTotal.setString(porcentaje + "%");
        lblUsoTotal.setText(usadas + " / " + total + " posiciones usadas del disco");
    }

    /* ==================== ÍNDICE ==================== */

    private void refrescarIndice() {
        modeloIndice.setRowCount(0);
        if (discoActual == null) return;

        List<String[]> entradas = discoActual.getIndice();
        int maxArchivos = discoActual.getMaxArchivos();
        int posicionIndice = discoActual.getInicioIndice();

        for (int i = 0; i < maxArchivos; i++) {
            int posReal = posicionIndice + (i * 3);
            if (i < entradas.size()) {
                String[] e = entradas.get(i);
                modeloIndice.addRow(new Object[]{
                    posReal,
                    e[0],
                    e[1],
                    e[2]
                });
            } else {
                modeloIndice.addRow(new Object[]{posReal, "(vacio)", "-", "-"});
            }
        }
    }

    /* ==================== SWAP ==================== */

    private void refrescarSwap() {
        modeloSwap.setRowCount(0);
        if (discoActual == null) return;

        int inicio = discoActual.getInicioSwap();
        int fin = discoActual.getInicioArchivos();

        for (int i = inicio; i < fin; i++) {
            Object contenido = discoActual.leer(i);
            if (mostrarSoloOcupadasSwap && contenido == null) continue;
            String valor = (contenido == null) ? "(vacio)" : contenido.toString();
            modeloSwap.addRow(new Object[]{i, valor});
        }
    }

    /* ==================== ARCHIVOS ==================== */

    private void refrescarArchivos() {
        modeloArchivos.setRowCount(0);
        if (discoActual == null) return;

        int inicio = discoActual.getInicioArchivos();
        int fin = discoActual.getTamanoTotal();

        for (int i = inicio; i < fin; i++) {
            Object contenido = discoActual.leer(i);
            if (mostrarSoloOcupadasArchivos && contenido == null) continue;
            String valor = (contenido == null) ? "(vacio)" : contenido.toString();
            modeloArchivos.addRow(new Object[]{i, valor});
        }
    }
}