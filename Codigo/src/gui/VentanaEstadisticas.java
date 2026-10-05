package gui;

import logica.BCPTerminado;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Ventana modal de estadisticas de procesos terminados.
 *
 * Muestra:
 *   - Un resumen general (total, duracion total, promedio).
 *   - Una tabla con todos los procesos (ID, inicio, fin, duracion, %).
 *   - Un panel de detalle del proceso seleccionado.
 */
public class VentanaEstadisticas extends JDialog {

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    private JTable tabla;
    private DefaultTableModel modelo;
    private List<BCPTerminado> procesos;
    private long duracionTotal;

    // Panel de detalle
    private JLabel lblId;
    private JLabel lblEstado;
    private JLabel lblInicio;
    private JLabel lblFin;
    private JLabel lblDuracion;
    private JLabel lblPorcentaje;
    private JLabel lblPrioridad;
    private JLabel lblBase;
    private JLabel lblAlcance;

    public VentanaEstadisticas(Frame propietario, List<BCPTerminado> procesos) {
        super(propietario, "Estadisticas de Procesos", true);
        this.procesos = procesos;
        this.duracionTotal = calcularDuracionTotal();

        construirInterfaz();
        pack();
        setSize(850, 700);
        setResizable(true);
        setLocationRelativeTo(propietario);
    }

    private long calcularDuracionTotal() {
        long total = 0;
        for (BCPTerminado pt : procesos) {
            long d = pt.getDuracionSegundos();
            if (d > 0) total += d;
        }
        return total;
    }

    private void construirInterfaz() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(Paleta.FONDO_GENERAL);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        setContentPane(mainPanel);

        // ==== Título ====
        JLabel lblTitulo = new JLabel("Estadisticas de Procesos");
        lblTitulo.setFont(Paleta.FUENTE_TITULO);
        lblTitulo.setForeground(Paleta.VERDE_OSCURO);
        mainPanel.add(lblTitulo, BorderLayout.NORTH);

        // ==== Centro: resumen + tabla + detalle ====
        JPanel centro = new JPanel(new BorderLayout(10, 10));
        centro.setOpaque(false);

        // IMPORTANTE: crear el panel de detalle PRIMERO para que los
        // labels existan cuando la tabla dispare mostrarDetalle().
        JPanel panelDetalle = crearPanelDetalle();

        // Panel superior: resumen
        centro.add(crearPanelResumen(), BorderLayout.NORTH);

        // Panel central: tabla
        centro.add(crearPanelTabla(), BorderLayout.CENTER);

        // Panel inferior: detalle (ya creado)
        centro.add(panelDetalle, BorderLayout.SOUTH);

        mainPanel.add(centro, BorderLayout.CENTER);

        // ==== Botón cerrar ====
        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.setFont(Paleta.FUENTE_BOTON);
        btnCerrar.setBackground(Paleta.VERDE_PRINCIPAL);
        btnCerrar.setForeground(Paleta.TEXTO_CLARO);
        btnCerrar.setFocusPainted(false);
        btnCerrar.addActionListener(e -> dispose());

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        panelBotones.setOpaque(false);
        panelBotones.add(btnCerrar);
        mainPanel.add(panelBotones, BorderLayout.SOUTH);
    }

    /* ==================== RESUMEN ==================== */

    private JPanel crearPanelResumen() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Resumen",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(10, 15, 10, 15)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 10, 3, 10);
        gbc.weightx = 1;

        long promedio = (procesos.size() > 0) ? duracionTotal / procesos.size() : 0;
        long masLargo = 0;
        long masCorto = Long.MAX_VALUE;
        for (BCPTerminado pt : procesos) {
            long d = pt.getDuracionSegundos();
            if (d > 0) {
                if (d > masLargo) masLargo = d;
                if (d < masCorto) masCorto = d;
            }
        }
        if (masCorto == Long.MAX_VALUE) masCorto = 0;

        JLabel l1 = crearLabelResumen("Total de procesos:", String.valueOf(procesos.size()));
        JLabel l2 = crearLabelResumen("Duracion total:", duracionTotal + " s");
        JLabel l3 = crearLabelResumen("Duracion promedio:", promedio + " s");
        JLabel l4 = crearLabelResumen("Proceso mas largo:", masLargo + " s");
        JLabel l5 = crearLabelResumen("Proceso mas corto:", masCorto + " s");

        gbc.gridx = 0; gbc.gridy = 0; panel.add(l1, gbc);
        gbc.gridx = 1; panel.add(l2, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(l3, gbc);
        gbc.gridx = 1; panel.add(l4, gbc);
        gbc.gridx = 0; gbc.gridy = 2; panel.add(l5, gbc);

        return panel;
    }

    private JLabel crearLabelResumen(String titulo, String valor) {
        JLabel label = new JLabel(titulo + " " + valor);
        label.setFont(Paleta.FUENTE_MONO);
        label.setForeground(Paleta.TEXTO_NORMAL);
        return label;
    }

    /* ==================== TABLA ==================== */

    private JPanel crearPanelTabla() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Paleta.MORADO_ACENTO, 2),
                "Procesos terminados",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                Paleta.FUENTE_TITULO_PANEL,
                Paleta.MORADO_ACENTO
        ));

        modelo = new DefaultTableModel(
                new Object[]{"ID", "Inicio", "Fin", "Duracion", "% del total", "Prioridad"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (BCPTerminado pt : procesos) {
            long dur = pt.getDuracionSegundos();
            double porcentaje = (duracionTotal > 0) ? (dur * 100.0 / duracionTotal) : 0;
            modelo.addRow(new Object[]{
                "ID " + pt.getId(),
                formatearHora(pt.getTiempoInicio()),
                formatearHora(pt.getTiempoFin()),
                dur + " s",
                String.format("%.1f%%", porcentaje),
                String.valueOf(pt.getPrioridad())
            });
        }

        tabla = new JTable(modelo);
        tabla.setFont(Paleta.FUENTE_MONO);
        tabla.setRowHeight(26);
        tabla.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        tabla.setShowGrid(true);
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setFont(Paleta.FUENTE_LABEL_BOLD);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                    c.setForeground(Paleta.TEXTO_NORMAL);
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int fila = tabla.getSelectedRow();
                mostrarDetalle(fila);
            }
        });

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setPreferredSize(new Dimension(0, 200));
        panel.add(scroll, BorderLayout.CENTER);

        // Seleccionar primera fila DESPUÉS de que todos los labels existan
        if (!procesos.isEmpty()) {
            tabla.setRowSelectionInterval(0, 0);
        } else {
            mostrarDetalle(-1);
        }

        return panel;
    }

    /* ==================== DETALLE ==================== */

    private JPanel crearPanelDetalle() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                        "Detalle del proceso seleccionado",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        Paleta.FUENTE_TITULO_PANEL,
                        Paleta.VERDE_OSCURO
                ),
                new EmptyBorder(10, 15, 10, 15)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.weightx = 1;

        lblId        = crearLabelDetalle("ID", "-");
        lblEstado    = crearLabelDetalle("Estado", "-");
        lblInicio    = crearLabelDetalle("Hora inicio", "-");
        lblFin       = crearLabelDetalle("Hora fin", "-");
        lblDuracion  = crearLabelDetalle("Duracion", "-");
        lblPorcentaje= crearLabelDetalle("% del total", "-");
        lblPrioridad = crearLabelDetalle("Prioridad", "-");
        lblBase      = crearLabelDetalle("Base", "-");
        lblAlcance   = crearLabelDetalle("Alcance", "-");

        int y = 0;
        gbc.gridx = 0; gbc.gridy = y; panel.add(lblId, gbc);
        gbc.gridx = 1; panel.add(lblEstado, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblInicio, gbc);
        gbc.gridx = 1; panel.add(lblFin, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblDuracion, gbc);
        gbc.gridx = 1; panel.add(lblPorcentaje, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblPrioridad, gbc);
        gbc.gridx = 1; panel.add(lblBase, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblAlcance, gbc);

        return panel;
    }

    private JLabel crearLabelDetalle(String nombre, String valorInicial) {
        JLabel label = new JLabel(nombre + ": " + valorInicial);
        label.setFont(Paleta.FUENTE_MONO);
        label.setForeground(Paleta.TEXTO_NORMAL);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE0, 0xE0, 0xE0), 1),
                new EmptyBorder(5, 8, 5, 8)
        ));
        label.setOpaque(true);
        label.setBackground(new Color(0xF8, 0xF9, 0xFA));
        return label;
    }

    private void mostrarDetalle(int fila) {
        if (lblId == null) return;   // seguridad

        if (fila < 0 || fila >= procesos.size()) {
            lblId.setText("ID: -");
            lblEstado.setText("Estado: -");
            lblInicio.setText("Hora inicio: -");
            lblFin.setText("Hora fin: -");
            lblDuracion.setText("Duracion: -");
            lblPorcentaje.setText("% del total: -");
            lblPrioridad.setText("Prioridad: -");
            lblBase.setText("Base: -");
            lblAlcance.setText("Alcance: -");
            return;
        }

        BCPTerminado pt = procesos.get(fila);
        long dur = pt.getDuracionSegundos();
        double porcentaje = (duracionTotal > 0) ? (dur * 100.0 / duracionTotal) : 0;

        lblId.setText("ID: " + pt.getId());
        lblEstado.setText("Estado: " + pt.getEstado());
        lblInicio.setText("Hora inicio: " + formatearHora(pt.getTiempoInicio()));
        lblFin.setText("Hora fin: " + formatearHora(pt.getTiempoFin()));
        lblDuracion.setText("Duracion: " + dur + " s (" + formatearDuracion(dur) + ")");
        lblPorcentaje.setText(String.format("%% del total: %.1f%%", porcentaje));
        lblPrioridad.setText("Prioridad: " + pt.getPrioridad());
        lblBase.setText("Base: " + pt.getBase());
        lblAlcance.setText("Alcance: " + pt.getAlcance() + " instrucciones");
    }

    /* ==================== HELPERS ==================== */

    private String formatearHora(LocalDateTime t) {
        if (t == null) return "-";
        return t.format(FORMATO_HORA);
    }

    private String formatearDuracion(long segundos) {
        if (segundos < 0) return "-";
        long min = segundos / 60;
        long seg = segundos % 60;
        if (min > 0) {
            return min + " min " + seg + " s";
        }
        return seg + " s";
    }
}