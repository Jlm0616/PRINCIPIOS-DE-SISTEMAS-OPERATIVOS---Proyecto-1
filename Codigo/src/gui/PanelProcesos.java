package gui;

import modelo.BCP;
import modelo.EstadoProceso;
import logica.ListaDeTrabajos;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.SwingConstants;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Panel que muestra:
 *   - Arriba: la lista de procesos con su ID y estado.
 *   - Abajo: el BCP del proceso actual (el que esta en la CPU).
 *
 * Se actualiza con actualizarLista(ListaDeTrabajos, BCP) o
 * actualizarBCP(BCP).
 */
public class PanelProcesos extends JPanel {

    private JTable tablaProcesos;
    private DefaultTableModel modeloProcesos;

    private JLabel lblId;
    private JLabel lblPC;
    private JLabel lblIR;
    private JLabel lblAC;
    private JLabel lblAX;
    private JLabel lblBX;
    private JLabel lblCX;
    private JLabel lblDX;
    private JLabel lblOF;
    private JLabel lblEQ;
    private JLabel lblPila;
    private JLabel lblEstado;
    private JLabel lblBase;
    private JLabel lblAlcance;

    public PanelProcesos() {
        construirInterfaz();
    }

    private void construirInterfaz() {
        setLayout(new BorderLayout(5, 5));
        setOpaque(false);

        // ===== Tabla de procesos (arriba) =====
        modeloProcesos = new DefaultTableModel(new Object[]{"Proceso", "Estado"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tablaProcesos = new JTable(modeloProcesos);
        tablaProcesos.setFont(Paleta.FUENTE_MONO);
        tablaProcesos.setRowHeight(24);
        tablaProcesos.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        tablaProcesos.setShowGrid(true);
        tablaProcesos.setFillsViewportHeight(true);
        tablaProcesos.getTableHeader().setFont(Paleta.FUENTE_LABEL_BOLD);

        // Renderer: colores por estado
        tablaProcesos.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (isSelected) {
                    return c;
                }

                // La columna 1 es el estado: colorear segun el valor
                if (column == 1) {
                    String estado = value != null ? value.toString() : "";
                    c.setBackground(colorPorEstado(estado));
                    c.setForeground(Color.WHITE);
                    setFont(Paleta.FUENTE_LABEL_BOLD);
                } else {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : Paleta.FONDO_FILA_ALT);
                    c.setForeground(Paleta.TEXTO_NORMAL);
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        JScrollPane scrollTabla = new JScrollPane(tablaProcesos);
        scrollTabla.setPreferredSize(new Dimension(0, 200));
        scrollTabla.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Paleta.VERDE_OSCURO, 2),
                "Procesos",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                Paleta.FUENTE_TITULO_PANEL,
                Paleta.VERDE_OSCURO
        ));
        add(scrollTabla, BorderLayout.NORTH);

        // ===== Panel BCP actual (abajo) =====
        JPanel panelBCP = crearPanelBCP();
        JScrollPane scrollBCP = new JScrollPane(panelBCP);
        scrollBCP.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Paleta.MORADO_ACENTO, 2),
                "BCP actual CPU1",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                Paleta.FUENTE_TITULO_PANEL,
                Paleta.MORADO_ACENTO
        ));
        add(scrollBCP, BorderLayout.CENTER);
    }

    private JPanel crearPanelBCP() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.weightx = 1;

        lblId     = crearLabel("ID", "-");
        lblEstado = crearLabel("Estado", "-");
        lblPC     = crearLabel("PC", "-");
        lblIR     = crearLabel("IR", "-");
        lblAC     = crearLabel("AC", "-");
        lblAX     = crearLabel("AX", "-");
        lblBX     = crearLabel("BX", "-");
        lblCX     = crearLabel("CX", "-");
        lblDX     = crearLabel("DX", "-");
        lblOF     = crearLabel("OF", "0");
        lblEQ     = crearLabel("EQ", "0");
        lblPila   = crearLabel("Pila", "[]");
        lblBase   = crearLabel("Base", "-");
        lblAlcance = crearLabel("Alcance", "-");

        int y = 0;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblId, gbc);
        gbc.gridx = 1; panel.add(lblEstado, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblPC, gbc);
        gbc.gridx = 1; panel.add(lblIR, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblAC, gbc);
        gbc.gridx = 1; panel.add(lblAX, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblBX, gbc);
        gbc.gridx = 1; panel.add(lblCX, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblDX, gbc);
        gbc.gridx = 1; panel.add(lblOF, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblEQ, gbc);
        gbc.gridx = 1; panel.add(lblBase, gbc);
        y++;

        gbc.gridx = 0; gbc.gridy = y; panel.add(lblAlcance, gbc);
        gbc.gridx = 1; panel.add(lblPila, gbc);
        y++;

        return panel;
    }

    private JLabel crearLabel(String nombre, String valorInicial) {
        JLabel label = new JLabel(nombre + ": " + valorInicial);
        label.setFont(Paleta.FUENTE_MONO);
        label.setForeground(Paleta.TEXTO_NORMAL);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE0, 0xE0, 0xE0), 1),
                new EmptyBorder(3, 6, 3, 6)
        ));
        label.setOpaque(true);
        label.setBackground(new Color(0xF8, 0xF9, 0xFA));
        return label;
    }

    private Color colorPorEstado(String estado) {
        if (estado == null) return Paleta.ESTADO_EXIT;
        switch (estado.toUpperCase()) {
            case "NEW":             return Paleta.ESTADO_NEW;
            case "READY":           return Paleta.ESTADO_READY;
            case "RUNNING":         return Paleta.ESTADO_RUNNING;
            case "BLOCKED":         return Paleta.ESTADO_BLOCKED;
            case "EXIT":            return Paleta.ESTADO_EXIT;
            case "READY_SUSPEND":   return Paleta.ESTADO_SUSPENDIDO;
            case "BLOCKED_SUSPEND": return Paleta.ESTADO_SUSPENDIDO;
            default:                return Paleta.ESTADO_EXIT;
        }
    }

    /* ==================== ACTUALIZACION ==================== */

    /**
     * Actualiza la tabla de procesos con la lista de trabajos dada.
     */
    public void actualizarLista(ListaDeTrabajos lista) {
        modeloProcesos.setRowCount(0);
        if (lista == null) return;

        for (BCP bcp : lista.toList()) {
            modeloProcesos.addRow(new Object[]{
                "ID " + bcp.getId(),
                bcp.getEstado().toString()
            });
        }
    }

    /**
     * Actualiza el panel BCP actual con los datos del BCP dado.
     * Si bcp es null, muestra guiones.
     */
    public void actualizarBCP(BCP bcp) {
        if (bcp == null) {
            lblId.setText("ID: -");
            lblEstado.setText("Estado: -");
            lblPC.setText("PC: -");
            lblIR.setText("IR: -");
            lblAC.setText("AC: -");
            lblAX.setText("AX: -");
            lblBX.setText("BX: -");
            lblCX.setText("CX: -");
            lblDX.setText("DX: -");
            lblOF.setText("OF: 0");
            lblEQ.setText("EQ: 0");
            lblPila.setText("Pila: []");
            lblBase.setText("Base: -");
            lblAlcance.setText("Alcance: -");
            return;
        }

        lblId.setText("ID: " + bcp.getId());
        lblEstado.setText("Estado: " + bcp.getEstado());
        lblPC.setText("PC: " + bcp.getPc());
        lblIR.setText("IR: " + bcp.getIr());
        lblAC.setText("AC: " + bcp.getAc());
        lblAX.setText("AX: " + bcp.getAx());
        lblBX.setText("BX: " + bcp.getBx());
        lblCX.setText("CX: " + bcp.getCx());
        lblDX.setText("DX: " + bcp.getDx());
        lblOF.setText("OF: " + (bcp.getOverflow() ? "1" : "0"));
        lblEQ.setText("EQ: " + (bcp.getBanderaIgual() ? "1" : "0"));
        lblPila.setText("Pila: " + bcp.getPila().toString());
        lblBase.setText("Base: " + bcp.getBase());
        lblAlcance.setText("Alcance: " + bcp.getAlcance());
    }
}