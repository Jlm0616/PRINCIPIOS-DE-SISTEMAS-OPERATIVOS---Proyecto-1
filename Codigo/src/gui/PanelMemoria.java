package gui;

import modelo.BCP;
import modelo.Instruccion;
import modelo.Memoria;

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
 * Panel que muestra el contenido de la memoria principal.
 *
 * Arriba: barra de progreso con el porcentaje de uso.
 * Centro: tabla con Pos | Zona | Valor. La fila del IR se resalta en amarillo.
 * Abajo: boton toggle "Mostrar solo ocupadas / Mostrar todas" + leyenda.
 *
 * La columna "Zona" indica a qué sección del kernel/usuario pertenece cada
 * posición (Stallings, seccion 3.3):
 *   - ListaProc  → Lista de Procesos (punteros a BCPs)
 *   - BCP        → Bloques de Control de Proceso
 *   - TablaMem   → Tabla de Memoria (bloques asignados)
 *   - Usuario    → Instrucciones de los procesos
 *   - Libre      → espacio del kernel no asignado a ninguna sub-zona
 *   - IR         → posicion actual del Instruction Register (amarillo)
 *
 * Para las posiciones de un BCP, ListaProcesos o TablaMemoria, la columna
 * "Valor" muestra el nombre del campo junto con su valor para facilitar la
 * lectura del estado interno del sistema.
 */
public class PanelMemoria extends JPanel {

    private JTable tabla;
    private DefaultTableModel modelo;
    private JButton btnToggle;
    private JProgressBar barraUso;
    private JLabel lblUso;
    private boolean mostrarSoloOcupadas = false;

    private Memoria memoriaActual;
    private int irActual = -1;

    /* Colores suaves por zona (para diferenciar visualmente) */
    private static final Color COLOR_LISTA_PROC = new Color(0xE8, 0xF5, 0xE9);  // verde muy claro
    private static final Color COLOR_BCP        = new Color(0xFF, 0xF8, 0xE1);  // amarillo muy claro
    private static final Color COLOR_TABLA_MEM  = new Color(0xF3, 0xE5, 0xF5);  // lila muy claro
    private static final Color COLOR_USUARIO    = Color.WHITE;
    private static final Color COLOR_LIBRE      = new Color(0xF0, 0xF0, 0xF0);  // gris muy claro
    private static final Color COLOR_IR         = Paleta.AMARILLO_ADVERTENCIA;

    public PanelMemoria() {
        construirInterfaz();
    }

    private void construirInterfaz() {
        setLayout(new BorderLayout(5, 5));
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
                new EmptyBorder(5, 5, 5, 5)
        ));

        // === Arriba: barra de uso ===
        JPanel panelUso = new JPanel(new BorderLayout(5, 2));
        panelUso.setOpaque(false);

        barraUso = new JProgressBar(0, 100);
        barraUso.setStringPainted(true);
        barraUso.setForeground(Paleta.VERDE_PRINCIPAL);
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
        modelo = new DefaultTableModel(new Object[]{"Pos", "Zona", "Valor"}, 0) {
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

        // Anchos de columna
        tabla.getColumnModel().getColumn(0).setPreferredWidth(50);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(100);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(350);

        // Renderer: colorea por zona + resalta IR
        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (isSelected) {
                    return c;
                }

                Object posObj = table.getValueAt(row, 0);
                Object zonaObj = table.getValueAt(row, 1);
                String zona = (zonaObj != null) ? zonaObj.toString() : "";
                int pos = (posObj instanceof Integer) ? (Integer) posObj : -1;

                if (pos == irActual) {
                    c.setBackground(COLOR_IR);
                    c.setForeground(Color.BLACK);
                    setFont(Paleta.FUENTE_MONO_BOLD);
                    return c;
                }

                c.setBackground(colorDeZona(zona));
                c.setForeground(Paleta.TEXTO_NORMAL);
                setFont(Paleta.FUENTE_MONO);
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(tabla);
        add(scroll, BorderLayout.CENTER);

        // === Abajo: boton toggle + leyenda ===
        btnToggle = new JButton("Mostrar solo ocupadas");
        btnToggle.setFont(Paleta.FUENTE_BOTON);
        btnToggle.setBackground(Paleta.VERDE_PRINCIPAL);
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

        JPanel panelSur = new JPanel(new BorderLayout());
        panelSur.setOpaque(false);
        panelSur.add(panelBoton, BorderLayout.WEST);
        panelSur.add(crearLeyenda(), BorderLayout.EAST);
        add(panelSur, BorderLayout.SOUTH);
    }

    private JPanel crearLeyenda() {
        JPanel leyenda = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        leyenda.setOpaque(false);

        leyenda.add(crearItemLeyenda("ListaProc", COLOR_LISTA_PROC));
        leyenda.add(crearItemLeyenda("BCP", COLOR_BCP));
        leyenda.add(crearItemLeyenda("TablaMem", COLOR_TABLA_MEM));
        leyenda.add(crearItemLeyenda("Usuario", COLOR_USUARIO));
        leyenda.add(crearItemLeyenda("Libre", COLOR_LIBRE));
        leyenda.add(crearItemLeyenda("IR", COLOR_IR));

        return leyenda;
    }

    private JPanel crearItemLeyenda(String texto, Color color) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        item.setOpaque(false);

        JLabel cuadro = new JLabel("  ");
        cuadro.setOpaque(true);
        cuadro.setBackground(color);
        cuadro.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        cuadro.setPreferredSize(new Dimension(14, 14));

        JLabel label = new JLabel(texto);
        label.setFont(Paleta.FUENTE_LABEL);
        label.setForeground(Paleta.TEXTO_NORMAL);

        item.add(cuadro);
        item.add(label);
        return item;
    }

    private Color colorDeZona(String zona) {
        if (zona == null) return COLOR_USUARIO;
        switch (zona) {
            case "ListaProc": return COLOR_LISTA_PROC;
            case "BCP":       return COLOR_BCP;
            case "TablaMem":  return COLOR_TABLA_MEM;
            case "Usuario":   return COLOR_USUARIO;
            case "Libre":     return COLOR_LIBRE;
            default:          return COLOR_USUARIO;
        }
    }

    public void actualizar(Memoria memoria, int ir) {
        this.memoriaActual = memoria;
        this.irActual = ir;
        refrescar();
        actualizarBarra();
    }

    private void refrescar() {
        if (memoriaActual == null) {
            modelo.setRowCount(0);
            return;
        }

        modelo.setRowCount(0);

        int tamano = memoriaActual.getTamanoMemoria();
        for (int i = 0; i < tamano; i++) {
            Object contenido = memoriaActual.leer(i);
            if (mostrarSoloOcupadas && contenido == null) {
                continue;
            }
            String zona = calcularZona(i);
            String valor = formatearContenido(i, contenido);
            modelo.addRow(new Object[]{i, zona, valor});
        }

        if (irActual >= 0) {
            for (int i = 0; i < modelo.getRowCount(); i++) {
                Object posObj = modelo.getValueAt(i, 0);
                if (posObj instanceof Integer && ((Integer) posObj) == irActual) {
                    tabla.scrollRectToVisible(tabla.getCellRect(i, 0, true));
                    break;
                }
            }
        }
    }

    private String calcularZona(int pos) {
        if (memoriaActual == null) return "";

        if (pos >= memoriaActual.getInicioListaProcesos()
                && pos <= memoriaActual.getFinListaProcesos()) {
            return "ListaProc";
        }
        if (pos >= memoriaActual.getInicioBCPs()
                && pos <= memoriaActual.getFinBCPs()) {
            return "BCP";
        }
        if (pos >= memoriaActual.getInicioTablaMemoria()
                && pos <= memoriaActual.getFinTablaMemoria()) {
            return "TablaMem";
        }
        if (pos >= memoriaActual.getLimiteKernelUsuario()
                && pos < memoriaActual.getTamanoMemoria()) {
            return "Usuario";
        }
        if (pos < memoriaActual.getLimiteKernelUsuario()) {
            return "Libre";
        }
        return "?";
    }

    private void actualizarBarra() {
        if (memoriaActual == null) {
            barraUso.setValue(0);
            barraUso.setString("0%");
            lblUso.setText("0 / 0 posiciones usadas");
            return;
        }

        int total = memoriaActual.getTamanoMemoria();
        int usadas = 0;
        for (int i = 0; i < total; i++) {
            if (memoriaActual.leer(i) != null) {
                usadas++;
            }
        }

        int porcentaje = (total > 0) ? (usadas * 100) / total : 0;
        barraUso.setValue(porcentaje);
        barraUso.setString(porcentaje + "%");
        lblUso.setText(usadas + " / " + total + " posiciones usadas");
    }

    /**
     * Formatea el contenido de una posición de memoria.
     *
     * Casos:
     *   - null         → "(vacio)"
     *   - Instruccion  → toString()
     *   - ListaProc    → "-> BCP id=X (pos Y)"
     *   - BCP          → "NombreCampo: valor"
     *   - TablaMem     → "Bloque N -> ID Proceso: X", "Inicio: Y", "Tamano: Z"
     *   - Otro         → toString()
     */
    private String formatearContenido(int pos, Object contenido) {
        if (contenido == null) {
            return "(vacio)";
        }
        if (contenido instanceof Instruccion) {
            return contenido.toString();
        }

        // Caso 1: ¿Es parte de la ListaProcesos?
        if (memoriaActual != null
                && pos >= memoriaActual.getInicioListaProcesos()
                && pos <= memoriaActual.getFinListaProcesos()) {
            return formatearListaProceso(pos, contenido);
        }

        // Caso 2: ¿Es parte de un BCP?
        BCP bcp = encontrarBCPPorPosicion(pos);
        if (bcp != null) {
            int offset = pos - bcp.getDireccionBase();
            if (offset == 0) {
                return "=== BCP id=" + bcp.getId() + " ===";
            }
            if (offset > 0 && offset < BCP.POSICIONES_REQUERIDAS) {
                return nombreCampoBCP(offset) + ": " + valorCampoBCP(bcp, offset);
            }
        }

        // Caso 3: ¿Es parte de la TablaMemoria?
        if (memoriaActual != null
                && pos >= memoriaActual.getInicioTablaMemoria()
                && pos <= memoriaActual.getFinTablaMemoria()) {
            return formatearTablaMemoria(pos, contenido);
        }

        return contenido.toString();
    }

    /**
     * Formatea una posición de la ListaProcesos.
     * La ListaProcesos guarda la dirección base del BCP de cada proceso READY.
     */
    private String formatearListaProceso(int pos, Object contenido) {
        if (!(contenido instanceof Integer)) {
            return contenido.toString();
        }
        int dirBCP = (Integer) contenido;

        try {
            BCP bcp = new BCP(memoriaActual, dirBCP);
            return "-> BCP id=" + bcp.getId() + " (pos " + dirBCP + ")";
        } catch (Exception e) {
            return "-> dir=" + dirBCP;
        }
    }

    /**
     * Formatea una posición de la TablaMemoria.
     * Cada bloque ocupa 3 posiciones: [idProceso, inicio, tamano].
     */
    private String formatearTablaMemoria(int pos, Object contenido) {
        int inicio = memoriaActual.getInicioTablaMemoria();
        int offset = pos - inicio;

        int numBloque = offset / 3;
        int posEnBloque = offset % 3;

        switch (posEnBloque) {
            case 0:
                return "ID Proceso: " + contenido;
            case 1:
                return "Inicio: " + contenido;
            case 2:
                return "Tamano: " + contenido;
            default:
                return contenido.toString();
        }
    }

    /**
     * Busca el BCP al que pertenece una posición de memoria.
     */
    private BCP encontrarBCPPorPosicion(int pos) {
        if (memoriaActual == null) return null;

        int inicioBCPs = memoriaActual.getInicioBCPs();
        int finBCPs = memoriaActual.getFinBCPs();

        if (pos < inicioBCPs || pos > finBCPs) return null;

        for (int base = inicioBCPs;
             base + BCP.POSICIONES_REQUERIDAS - 1 <= finBCPs;
             base += BCP.POSICIONES_REQUERIDAS) {

            if (pos >= base && pos < base + BCP.POSICIONES_REQUERIDAS) {
                Object idObj = memoriaActual.leer(base + BCP.OFF_ID);
                if (idObj instanceof Integer) {
                    return new BCP(memoriaActual, base);
                }
                return null;
            }
        }
        return null;
    }

    private String nombreCampoBCP(int offset) {
        switch (offset) {
            case 0:  return "ID";
            case 1:  return "Estado";
            case 2:  return "Prioridad";
            case 3:  return "PC";
            case 4:  return "IR";
            case 5:  return "AC";
            case 6:  return "AX";
            case 7:  return "BX";
            case 8:  return "CX";
            case 9:  return "DX";
            case 10: return "Overflow";
            case 11: return "BanderaIgual";
            case 12: return "PesoPendiente";
            case 13: return "Pila[0]";
            case 14: return "Pila[1]";
            case 15: return "Pila[2]";
            case 16: return "Pila[3]";
            case 17: return "Pila[4]";
            case 18: return "Base";
            case 19: return "Alcance";
            case 20: return "TiempoInicio";
            case 21: return "TiempoFin";
            case 22: return "CPUAsignado";
            case 23: return "ArchivoAbierto[0]";
            case 24: return "ArchivoAbierto[1]";
            case 25: return "ArchivoAbierto[2]";
            case 26: return "ArchivoAbierto[3]";
            case 27: return "ArchivoAbierto[4]";
            case 28: return "SiguienteBCP";
            case 29: return "Direccion";
            default: return "Campo" + offset;
        }
    }

    private String valorCampoBCP(BCP bcp, int offset) {
        Object v = bcp.getMemoria().leer(bcp.getDireccionBase() + offset);
        if (v == null) return "(vacio)";
        return v.toString();
    }
}