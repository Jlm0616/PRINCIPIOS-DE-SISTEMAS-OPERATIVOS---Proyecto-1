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
 * El disco tiene 4 zonas:
 *   1. Índice de archivos ASM (nombre, inicio, fin, zona)
 *   2. Índice de archivos PROCESO (nombre, inicio, fin, zona)
 *   3. Memoria virtual (swap)
 *   4. Archivos
 *
 * La columna "Zona" del índice indica dónde está el contenido:
 *   - PRINCIPAL → en la zona de archivos.
 *   - VIRTUAL   → en la zona de swap.
 *
 * La columna "Zona" del índice se colorea:
 *   - PRINCIPAL → verde claro
 *   - VIRTUAL   → naranja claro
 *
 * La columna "Sección" del índice se colorea:
 *   - ASM      → azul claro
 *   - PROCESO  → amarillo claro
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

    /* Colores por sección del índice */
    private static final Color COLOR_ASM     = new Color(0xE3, 0xF2, 0xFD);  // azul muy claro
    private static final Color COLOR_PROCESO = new Color(0xFF, 0xF8, 0xE1);  // amarillo muy claro

    /* Colores por zona */
    private static final Color COLOR_PRINCIPAL = new Color(0xE8, 0xF5, 0xE9);  // verde claro
    private static final Color COLOR_VIRTUAL   = new Color(0xFF, 0xF3, 0xE0);  // naranja claro

    // Crea el panel y construye su interfaz gráfica.
    public PanelDisco() {
        construirInterfaz();
    }

    // Construye la interfaz del panel (barra de uso + pestañas).
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

    // Crea el panel de la pestaña Índice con su tabla.
    private JPanel crearPanelIndice() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setOpaque(false);

        // Columnas: Pos | Sección | Nombre | Inicio | Fin | Zona
        modeloIndice = new DefaultTableModel(
                new Object[]{"Pos", "Sección", "Nombre", "Inicio", "Fin", "Zona"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tablaIndice = crearTablaConRendererIndice(modeloIndice);
        panel.add(new JScrollPane(tablaIndice), BorderLayout.CENTER);

        return panel;
    }

    /* ==================== PESTAÑA 2: MEMORIA VIRTUAL ==================== */

    // Crea el panel de la pestaña Memoria Virtual (swap) con su tabla.
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

    // Crea el panel de la pestaña Archivos con su tabla.
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

    /* ==================== TABLAS ==================== */

    // Crea una tabla con renderer alternado (filas pares blancas, impares gris).
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

    /**
     * Tabla del índice con renderer especial:
     *   - Columna "Sección" (1): color por sección (ASM=azul, PROCESO=amarillo)
     *   - Columna "Zona" (5): color por zona (PRINCIPAL=verde, VIRTUAL=naranja)
     */
    private JTable crearTablaConRendererIndice(DefaultTableModel modelo) {
        JTable tabla = new JTable(modelo);
        tabla.setFont(Paleta.FUENTE_MONO);
        tabla.setRowHeight(22);
        tabla.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        tabla.setShowGrid(true);
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setFont(Paleta.FUENTE_LABEL_BOLD);

        // Anchos de columna
        tabla.getColumnModel().getColumn(0).setPreferredWidth(50);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(80);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(150);
        tabla.getColumnModel().getColumn(3).setPreferredWidth(60);
        tabla.getColumnModel().getColumn(4).setPreferredWidth(60);
        tabla.getColumnModel().getColumn(5).setPreferredWidth(100);

        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (isSelected) return c;

                // Columna "Sección" (1): color por tipo
                if (column == 1) {
                    String seccion = (value != null) ? value.toString() : "";
                    if ("ASM".equals(seccion)) {
                        c.setBackground(COLOR_ASM);
                        c.setForeground(new Color(0x0D, 0x47, 0xA1));
                        setFont(Paleta.FUENTE_MONO_BOLD);
                    } else if ("PROCESO".equals(seccion)) {
                        c.setBackground(COLOR_PROCESO);
                        c.setForeground(new Color(0xBF, 0x60, 0x00));
                        setFont(Paleta.FUENTE_MONO_BOLD);
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                        c.setForeground(Paleta.TEXTO_NORMAL);
                    }
                    return c;
                }

                // Columna "Zona" (5): color por zona
                if (column == 5) {
                    String zona = (value != null) ? value.toString() : "";
                    if ("PRINCIPAL".equals(zona)) {
                        c.setBackground(COLOR_PRINCIPAL);
                        c.setForeground(new Color(0x1B, 0x5E, 0x20));
                        setFont(Paleta.FUENTE_MONO_BOLD);
                    } else if ("VIRTUAL".equals(zona)) {
                        c.setBackground(COLOR_VIRTUAL);
                        c.setForeground(new Color(0xBF, 0x60, 0x00));
                        setFont(Paleta.FUENTE_MONO_BOLD);
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                        c.setForeground(Paleta.TEXTO_NORMAL);
                    }
                    return c;
                }

                c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                c.setForeground(Paleta.TEXTO_NORMAL);
                setFont(Paleta.FUENTE_MONO);
                return c;
            }
        });

        return tabla;
    }

    /* ==================== ACTUALIZACIÓN ==================== */

    // Refresca todas las vistas del panel con el disco indicado.
    public void actualizar(Disco disco) {
        this.discoActual = disco;
        refrescarUsoTotal();
        refrescarIndice();
        refrescarSwap();
        refrescarArchivos();
    }

    /* ==================== USO TOTAL ==================== */

    // Actualiza la barra de uso total del disco.
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

    // Refresca la tabla del índice (secciones ASM y PROCESO).
    private void refrescarIndice() {
        modeloIndice.setRowCount(0);
        if (discoActual == null) return;

        int maxArchivos = discoActual.getMaxArchivos();
        int posicionesPorEntrada = Disco.POSICIONES_POR_ENTRADA_INDICE;

        // === Sección ASM ===
        int inicioAsm = discoActual.getInicioIndiceAsm();
        List<String[]> entradasAsm = discoActual.getIndice(Disco.TIPO_ASM);
        int idxAsm = 0;

        for (int i = 0; i < maxArchivos; i++) {
            int posReal = inicioAsm + (i * posicionesPorEntrada);
            if (idxAsm < entradasAsm.size()) {
                String[] e = entradasAsm.get(idxAsm++);
                modeloIndice.addRow(new Object[]{
                    posReal, "ASM", e[0], e[1], e[2], e.length > 3 ? e[3] : "-"
                });
            } else {
                modeloIndice.addRow(new Object[]{posReal, "ASM", "(vacio)", "-", "-", "-"});
            }
        }

        // === Sección PROCESO ===
        int inicioProc = discoActual.getInicioIndiceProceso();
        List<String[]> entradasProc = discoActual.getIndice(Disco.TIPO_PROCESO);
        int idxProc = 0;

        for (int i = 0; i < maxArchivos; i++) {
            int posReal = inicioProc + (i * posicionesPorEntrada);
            if (idxProc < entradasProc.size()) {
                String[] e = entradasProc.get(idxProc++);
                modeloIndice.addRow(new Object[]{
                    posReal, "PROCESO", e[0], e[1], e[2], e.length > 3 ? e[3] : "-"
                });
            } else {
                modeloIndice.addRow(new Object[]{posReal, "PROCESO", "(vacio)", "-", "-", "-"});
            }
        }
    }

    /* ==================== SWAP ==================== */

    // Refresca la tabla del swap (respetando el filtro activo).
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

    // Refresca la tabla de archivos (respetando el filtro activo).
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