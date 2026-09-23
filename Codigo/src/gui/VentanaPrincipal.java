package gui;

import modelo.Memoria;
import modelo.CPU;
import modelo.BCP;
import modelo.Instruccion;
import logica.Ensamblador;
import logica.EjecutorCPU;
import logica.CargarMemoria;
import logica.Traductor;
import soporte.CodificadorBinario;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.JProgressBar;
import javax.swing.JOptionPane;
import javax.swing.JComponent;
import javax.swing.BorderFactory;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.Box;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.FileDialog;

import java.io.File;
import java.util.List;

/**
 * Ventana principal del simulador Mini PC.
 *
 * Responsabilidades:
 *   1. Construir y mostrar la interfaz gráfica completa.
 *   2. Permitir cargar un archivo .asm y validarlo con {@link Ensamblador}.
 *   3. Cargar las instrucciones en memoria con {@link CargarMemoria}.
 *   4. Ejecutar el programa paso a paso o de forma automática
 *      usando {@link EjecutorCPU}.
 *   5. Mostrar el estado actual de la CPU, el BCP, la memoria
 *      y el progreso de ejecución.
 *
 * La ventana también permite ajustar el tamaño de memoria y el límite
 * Kernel/Usuario mediante {@link VentanaConfiguracionMemoria}, siempre
 * que no haya un programa cargado.
 *
 * Los botones "Cargar archivo" y "Ajustar memoria" se deshabilitan
 * automáticamente mientras hay un programa cargado, para evitar
 * sobrescribirlo o reconfigurar la memoria en uso.
 *
 * La tabla de memoria permite alternar entre mostrar el binario y su
 * traducción a texto legible (BCP + instrucciones) con el botón "Traducir".
 */
public class VentanaPrincipal extends JFrame {

    /* ==================== MODELO / ESTADO ==================== */

    private Memoria memoria;                  // memoria actual del simulador
    private CPU cpu;                          // CPU actual del simulador
    private EjecutorCPU ejecutor;             // ejecutor asociado a la CPU y memoria
    private List<Instruccion> instrucciones;  // programa cargado (null si no hay ninguno)
    private int instruccionesEjecutadas = 0;  // contador de instrucciones ya ejecutadas

    /* ==================== COMPONENTES DE UI ==================== */

    private JTable tablaInstrucciones;
    private DefaultTableModel modeloInstrucciones;
    private JTable tablaMemoria;
    private DefaultTableModel modeloMemoria;

    private JLabel lblPC, lblIR, lblAC, lblAX, lblBX, lblCX, lblDX, lblEstado, lblId;
    private JLabel lblInfo;
    private JButton btnCargar, btnModo, btnAccion, btnLimpiar, btnEstadisticas, btnAjustarMemoria;
    private JButton btnTraducir;   // alterna entre binario y traduccion
    private JProgressBar progressBar;
    private JLabel lblOverflow; 

    private JPanel panelEstadisticas;  // referencia para forzar repintado

    /* ==================== ESTADO DE LA INTERACCIÓN ==================== */

    private int filaActual = -1;                    // fila resaltada en la tabla (-1 = ninguna)
    private boolean modoPasoAPaso = false;          // false = automático, true = paso a paso
    private boolean modoTraducido = false;          // false = binario, true = traduccion
    private int tamanoMemoriaActual = 256;          // tamaño actual de memoria configurado
    private int limiteKernelActual = 64;            // límite Kernel/Usuario configurado

    /* ==================== CONSTANTES ==================== */

    private static final int POSICIONES_POR_INSTRUCCION = 1;  // 1 posición por instrucción
    private static final int ANCHO_MINIMO_VENTANA = 1400;     // ancho mínimo razonable
    private static final int ALTO_MINIMO_VENTANA = 620;       // alto mínimo razonable

    /* ==================== PALETA DE COLORES ==================== */

    private static final Color COLOR_BACKGROUND = new Color(236, 240, 241);
    private static final Color COLOR_PANEL = new Color(255, 255, 255);
    private static final Color COLOR_TEXT = new Color(44, 62, 80);
    private static final Color COLOR_ROW_ALT = new Color(245, 247, 250);
    private static final Color COLOR_SUCCESS = new Color(46, 204, 113);
    private static final Color COLOR_WARNING = new Color(230, 170, 20);
    private static final Color COLOR_PRIMARY = new Color(41, 128, 185);
    private static final Color COLOR_SECONDARY = new Color(52, 73, 94);

    /* Colores temáticos por sección (estilo "gemas") */
    private static final Color GEMA_ESPACIO = new Color(52, 118, 168);
    private static final Color GEMA_MENTE = new Color(217, 172, 51);
    private static final Color GEMA_REALIDAD = new Color(178, 58, 58);
    private static final Color GEMA_PODER = new Color(122, 78, 145);
    private static final Color GEMA_TIEMPO = new Color(69, 145, 94);
    private static final Color GEMA_ALMA = new Color(204, 122, 61);

    /**
     * Crea la ventana principal, aplica el Look & Feel, construye
     * la interfaz e inicializa el sistema (memoria y CPU por defecto).
     */
    public VentanaPrincipal() {
        super("Tarea 1 - Mini PC - Simulador de CPU");
        aplicarLookAndFeel();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1150, 720);
        setMinimumSize(new Dimension(ANCHO_MINIMO_VENTANA, ALTO_MINIMO_VENTANA));
        setLocationRelativeTo(null);

        inicializarComponentes();
        inicializarSistema();
    }

    /**
     * Intenta aplicar el Look & Feel "Nimbus".
     *
     * Si no está disponible, se mantiene el Look & Feel por defecto
     * sin lanzar error.
     */
    private void aplicarLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // Si Nimbus no esta disponible, se usa el look and feel por defecto
        }
    }

    /**
     * Inicializa la memoria, la CPU y el ejecutor con la configuración actual.
     */
    private void inicializarSistema() {
        memoria = new Memoria(tamanoMemoriaActual, limiteKernelActual);
        cpu = new CPU(limiteKernelActual);
        ejecutor = new EjecutorCPU(cpu, memoria);
        actualizarRegistros();
        llenarTablaMemoria();
    }

    /**
     * Construye y organiza los paneles principales de la ventana:
     * encabezado, panel central (tablas y registros) y panel de estadísticas.
     */
    private void inicializarComponentes() {
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(COLOR_BACKGROUND);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        setContentPane(mainPanel);

        JPanel encabezado = crearPanelEncabezado();
        mainPanel.add(encabezado, BorderLayout.NORTH);
        mainPanel.add(crearPanelCentral(), BorderLayout.CENTER);
        mainPanel.add(crearPanelEstadisticas(), BorderLayout.SOUTH);
    }

    /**
     * Crea el encabezado de la ventana (título + barra de botones).
     *
     * @return el panel de encabezado completo
     */
    private JPanel crearPanelEncabezado() {
        JPanel titulo = new JPanel(new BorderLayout());
        titulo.setBackground(COLOR_SECONDARY);
        titulo.setBorder(new EmptyBorder(15, 20, 15, 20));
        JLabel lblTitulo = new JLabel("Mini PC - Simulador de Ejecucion de Instrucciones");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitulo.setForeground(Color.WHITE);
        titulo.add(lblTitulo, BorderLayout.WEST);

        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);
        encabezado.add(titulo, BorderLayout.NORTH);
        encabezado.add(crearPanelBotones(), BorderLayout.SOUTH);

        return encabezado;
    }

    /**
     * Crea la barra de botones de acción y registra sus listeners.
     *
     * @return el panel con los seis botones principales
     */
    private JPanel crearPanelBotones() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 12));
        panel.setBackground(COLOR_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, COLOR_PRIMARY),
                new EmptyBorder(8, 15, 8, 15)
        ));

        Dimension buttonSize = new Dimension(150, 38);

        btnCargar          = crearBoton("Cargar archivo", GEMA_ESPACIO, buttonSize);
        btnModo            = crearBoton("Modo: Automatico", GEMA_PODER, buttonSize);
        btnAccion          = crearBoton("Ejecutar", GEMA_TIEMPO, buttonSize);
        btnLimpiar         = crearBoton("Limpiar", GEMA_REALIDAD, buttonSize);
        btnEstadisticas    = crearBoton("Estadisticas", GEMA_MENTE, buttonSize);
        btnAjustarMemoria  = crearBoton("Ajustar memoria", GEMA_ALMA, buttonSize);

        panel.add(btnCargar);
        panel.add(btnModo);
        panel.add(btnAccion);
        panel.add(btnLimpiar);
        panel.add(btnEstadisticas);
        panel.add(btnAjustarMemoria);

        btnCargar.addActionListener(e -> cargarArchivo());
        btnModo.addActionListener(e -> alternarModo());
        btnAccion.addActionListener(e -> ejecutarAccion());
        btnLimpiar.addActionListener(e -> limpiar());
        btnEstadisticas.addActionListener(e -> mostrarEstadisticas());
        btnAjustarMemoria.addActionListener(e -> abrirVentanaConfiguracion());

        return panel;
    }

    /**
     * Alterna entre modo automático y paso a paso.
     *
     * Actualiza los textos de los botones "Modo" y "Acción" para reflejar
     * el modo actual.
     */
    private void alternarModo() {
        modoPasoAPaso = !modoPasoAPaso;
        if (modoPasoAPaso) {
            btnModo.setText("Modo: Paso a paso");
            btnAccion.setText("Siguiente");
        } else {
            btnModo.setText("Modo: Automatico");
            btnAccion.setText("Ejecutar");
        }
    }

    /**
     * Ejecuta la acción del botón principal según el modo actual:
     * un paso (modo paso a paso) o el programa completo (modo automático).
     */
    private void ejecutarAccion() {
        if (modoPasoAPaso) {
            ejecutarUnPaso();
        } else {
            ejecutarProgramaCompleto();
        }
    }

    /**
     * Abre la ventana de configuración de memoria.
     *
     * Solo se permite si no hay un programa cargado (instrucciones == null).
     * Si el usuario confirma, se reconstruyen memoria, CPU y ejecutor
     * con los nuevos valores.
     */
    private void abrirVentanaConfiguracion() {
        if (instrucciones != null) {
            JOptionPane.showMessageDialog(this,
                    "No se puede cambiar la configuracion de memoria mientras hay un programa cargado.\n"
                    + "Presiona 'Limpiar' primero.",
                    "Memoria en uso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        VentanaConfiguracionMemoria dialogo = new VentanaConfiguracionMemoria(
                this, memoria.getTamanoMemoria(), memoria.getLimiteKernelUsuario());
        dialogo.setVisible(true);

        if (dialogo.isConfirmado()) {
            tamanoMemoriaActual = dialogo.getTamanoMemoria();
            limiteKernelActual = dialogo.getLimiteKernel();

            memoria = new Memoria(tamanoMemoriaActual, limiteKernelActual);
            cpu = new CPU(limiteKernelActual);
            ejecutor = new EjecutorCPU(cpu, memoria);
            instrucciones = null;
            instruccionesEjecutadas = 0;
            filaActual = -1;
            modoTraducido = false;   // volver a binario
            if (btnTraducir != null) {
                btnTraducir.setText("Traducir");
            }
            modeloInstrucciones.setRowCount(0);
            modeloMemoria.setRowCount(0);
            actualizarRegistros();
            llenarTablaMemoria();
            progressBar.setValue(0);
            progressBar.setString("0%");
            actualizarMensajeEstado("Memoria configurada: " + tamanoMemoriaActual
                    + " posiciones, Kernel 0-" + (limiteKernelActual - 1)
                    + ", Usuario " + limiteKernelActual + "-" + (tamanoMemoriaActual - 1));
            actualizarEstadoBotones();
        }
    }

    /**
     * Crea un botón estilizado con color de fondo y tamaño fijos.
     *
     * El color del texto se ajusta automáticamente (negro o blanco)
     * según la luminosidad del fondo para garantizar contraste.
     *
     * @param texto texto del botón
     * @param color color de fondo (y base para el efecto hover)
     * @param size  tamaño preferido del botón
     * @return el botón configurado
     */
    private JButton crearBoton(String texto, Color color, Dimension size) {
        JButton boton = new JButton(texto);
        boton.setPreferredSize(size);

        double luminosidad = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
        Color colorTexto = luminosidad > 150 ? Color.BLACK : Color.WHITE;

        boton.setForeground(colorTexto);
        boton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        boton.setFocusPainted(false);
        boton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Fuerza el color de fondo sin que Nimbus interfiera
        boton.setUI(new javax.swing.plaf.metal.MetalButtonUI());
        boton.setBackground(color);
        boton.setOpaque(true);
        boton.setContentAreaFilled(true);
        boton.setBorderPainted(false);

        boton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                boton.setBackground(color.darker());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                boton.setBackground(color);
            }
        });

        return boton;
    }

    /**
     * Crea el panel central con las tres secciones principales:
     * programa cargado, memoria y registros de la CPU/BCP.
     *
     * @return el panel central armado
     */
    private JPanel crearPanelCentral() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(COLOR_BACKGROUND);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.insets = new Insets(8, 0, 0, 0);

        // --- Tabla de instrucciones del programa ---
        modeloInstrucciones = new DefaultTableModel(new Object[]{"Instruccion", "Binario"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaInstrucciones = crearTablaEstilizada(modeloInstrucciones, GEMA_ESPACIO);
        JScrollPane scrollInstrucciones = envolverConTitulo(tablaInstrucciones, "Programa cargado", GEMA_ESPACIO);
        scrollInstrucciones.setPreferredSize(new Dimension(400, 0));

        // --- Tabla de memoria (kernel + usuario) ---
        modeloMemoria = new DefaultTableModel(new Object[]{"Pos", "Valor"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaMemoria = crearTablaEstilizada(modeloMemoria, GEMA_REALIDAD);
        JScrollPane scrollMemoria = envolverConTitulo(tablaMemoria, "Memoria", GEMA_REALIDAD);
        scrollMemoria.setPreferredSize(new Dimension(230, 0));

        // Botón "Traducir" debajo de la tabla de memoria
        btnTraducir = new JButton("Traducir");
        btnTraducir.setToolTipText("Alterna entre binario y su traduccion a texto legible");
        btnTraducir.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnTraducir.addActionListener(e -> {
            modoTraducido = !modoTraducido;
            btnTraducir.setText(modoTraducido ? "Ver binario" : "Traducir");
            llenarTablaMemoria();
        });

        // Panel contenedor: scroll arriba, botón abajo
        JPanel panelMemoria = new JPanel(new BorderLayout(0, 5));
        panelMemoria.setOpaque(false);
        panelMemoria.add(scrollMemoria, BorderLayout.CENTER);
        panelMemoria.add(btnTraducir, BorderLayout.SOUTH);

        // --- Panel de registros de CPU y BCP ---
        JPanel panelRegistros = crearPanelRegistros();
        JScrollPane scrollRegistros = envolverConTitulo(panelRegistros, "CPU / BCP", GEMA_PODER);
        scrollRegistros.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // Limitamos la altura del panel de registros para que no invada el panel sur
        scrollRegistros.setPreferredSize(new Dimension(0, 400));
        scrollRegistros.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.42;
        panel.add(scrollInstrucciones, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.23;
        panel.add(panelMemoria, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.35;
        panel.add(scrollRegistros, gbc);

        return panel;
    }

    /**
     * Crea una tabla estilizada con encabezado coloreado, filas alternas
     * y resaltado de la fila actual en la tabla de instrucciones.
     *
     * @param modelo      modelo de datos de la tabla
     * @param colorHeader color del encabezado y de selección
     * @return la tabla configurada
     */
    private JTable crearTablaEstilizada(DefaultTableModel modelo, Color colorHeader) {
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(26);
        tabla.setFont(new Font("Consolas", Font.PLAIN, 13));
        tabla.setGridColor(new Color(230, 230, 230));
        tabla.setShowGrid(true);
        tabla.setIntercellSpacing(new Dimension(1, 1));
        tabla.setSelectionBackground(colorHeader.brighter());
        tabla.setSelectionForeground(Color.WHITE);
        tabla.setFillsViewportHeight(true);

        // Encabezado personalizado
        tabla.getTableHeader().setPreferredSize(new Dimension(0, 32));
        tabla.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setOpaque(true);
                label.setBackground(colorHeader);
                label.setForeground(Color.WHITE);
                label.setFont(new Font("Segoe UI", Font.BOLD, 12));
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                return label;
            }
        });

        // Renderer de celdas: filas alternas + resaltado de la fila actual
        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (table == tablaInstrucciones && row == filaActual) {
                    c.setBackground(COLOR_WARNING);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : COLOR_ROW_ALT);
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        return tabla;
    }

    /**
     * Envuelve un componente en un JScrollPane con borde titulado.
     *
     * @param componente componente a envolver
     * @param titulo     título del borde
     * @param color      color del borde y del título
     * @return el JScrollPane configurado
     */
    private JScrollPane envolverConTitulo(JComponent componente, String titulo, Color color) {
        JScrollPane scroll = new JScrollPane(componente);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(color, 2),
                titulo,
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 13),
                color
        ));
        return scroll;
    }

    /**
     * Crea el panel de registros de la CPU y del BCP.
     *
     * Muestra PC, IR, AC, AX, BX, CX, DX y el estado del proceso.
     *
     * @return el panel con las etiquetas de registros
     */
    private JPanel crearPanelRegistros() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.weightx = 1;

        Font labelFont = new Font("Consolas", Font.BOLD, 14);
        Font irFont = new Font("Consolas", Font.BOLD, 12);

        lblId  = crearLabelRegistro("ID:", "-", labelFont, GEMA_MENTE);
        lblPC  = crearLabelRegistro("PC:", "-", labelFont, GEMA_ESPACIO);
        lblIR  = crearLabelRegistro("IR:", "-", irFont, GEMA_REALIDAD);
        lblAC  = crearLabelRegistro("AC:", "-", labelFont, COLOR_SUCCESS);
        lblAX  = crearLabelRegistro("AX:", "-", labelFont, COLOR_WARNING.darker());
        lblBX  = crearLabelRegistro("BX:", "-", labelFont, new Color(52, 152, 219));
        lblCX  = crearLabelRegistro("CX:", "-", labelFont, GEMA_PODER);
        lblDX  = crearLabelRegistro("DX:", "-", labelFont, new Color(230, 126, 34));
        lblOverflow = crearLabelRegistro("OF:", "0", labelFont, new Color(192, 57, 43));  // ← NUEVO

        int y = 0;

        gbc.gridy = y++;
        gbc.gridx = 0; gbc.gridwidth = 1; panel.add(lblId, gbc);
        gbc.gridx = 1; panel.add(lblPC, gbc);

        gbc.gridy = y++;
        gbc.gridx = 0; panel.add(lblAC, gbc);
        gbc.gridx = 1; panel.add(lblAX, gbc);

        gbc.gridy = y++;
        gbc.gridx = 0; gbc.gridwidth = 2; panel.add(lblIR, gbc);

        gbc.gridy = y++;
        gbc.gridx = 0; gbc.gridwidth = 1; panel.add(lblBX, gbc);
        gbc.gridx = 1; panel.add(lblCX, gbc);

        gbc.gridy = y++;
        gbc.gridx = 0; gbc.gridwidth = 2; panel.add(lblDX, gbc);

        // Etiqueta de la bandera de overflow (OF)
        gbc.gridy = y++;
        gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(lblOverflow, gbc);   // ← NUEVO

        // Etiqueta de estado del proceso (BCP)
        gbc.gridy = y++;
        gbc.gridx = 0; gbc.gridwidth = 2;
        lblEstado = new JLabel("Estado: -", SwingConstants.CENTER);
        lblEstado.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblEstado.setForeground(COLOR_TEXT);
        lblEstado.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GEMA_PODER, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        lblEstado.setOpaque(true);
        lblEstado.setBackground(new Color(245, 245, 245));
        panel.add(lblEstado, gbc);

        gbc.gridy = y;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(Box.createGlue(), gbc);

        return panel;
    }

    /**
     * Crea una etiqueta estilizada para un registro (PC, AC, AX, ...).
     *
     * @param nombre nombre del registro (con dos puntos, ej. "PC:")
     * @param valor  valor inicial a mostrar
     * @param font   fuente de la etiqueta
     * @param color  color del texto y del borde
     * @return la etiqueta configurada
     */
    private JLabel crearLabelRegistro(String nombre, String valor, Font font, Color color) {
        JLabel label = new JLabel(nombre + " " + valor, SwingConstants.CENTER);
        label.setFont(font);
        label.setForeground(color);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        label.setOpaque(true);
        label.setBackground(new Color(248, 249, 250));
        return label;
    }

    /**
     * Crea el panel inferior de estadísticas, con barra de progreso
     * y etiqueta de mensaje de estado.
     *
     * @return el panel de estadísticas
     */
    private JPanel crearPanelEstadisticas() {
        panelEstadisticas = new JPanel(new BorderLayout(10, 0));
        panelEstadisticas.setBackground(COLOR_PANEL);
        panelEstadisticas.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, COLOR_SECONDARY),
                new EmptyBorder(10, 15, 10, 15)
        ));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(COLOR_SUCCESS);
        progressBar.setBackground(new Color(236, 240, 241));
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressBar.setPreferredSize(new Dimension(0, 26));
        progressBar.setOpaque(true);

        lblInfo = new JLabel("Listo para cargar un programa...");
        lblInfo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblInfo.setForeground(COLOR_TEXT);
        lblInfo.setOpaque(true);
        lblInfo.setBackground(COLOR_PANEL);

        panelEstadisticas.add(lblInfo, BorderLayout.WEST);
        panelEstadisticas.add(progressBar, BorderLayout.CENTER);

        return panelEstadisticas;
    }

    /**
     * Abre un diálogo para seleccionar un archivo .asm, lo valida,
     * lo carga en memoria y actualiza la interfaz.
     *
     * Flujo:
     *   1. Se verifica que no haya un programa ya cargado.
     *   2. Se muestra un FileDialog (filtro visual ".asm").
     *   3. Se valida con {@link Ensamblador#esArchivoValido(File)}.
     *   4. Se lee con {@link Ensamblador#leerArchivo(File)}.
     *   5. Se verifica que el programa quepa en la memoria de usuario.
     *   6. Se carga con {@link CargarMemoria#cargar(List, Memoria)}.
     *   7. Se actualizan las tablas, registros y barra de progreso.
     *
     * Si cualquier paso falla, se muestra un mensaje y el estado previo
     * del programa cargado se preserva.
     */
    private void cargarArchivo() {
        if (instrucciones != null) {
            JOptionPane.showMessageDialog(this,
                    "Ya hay un programa cargado.\n"
                    + "Presiona 'Limpiar' antes de cargar otro archivo.",
                    "Memoria en uso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        FileDialog fileDialog = new FileDialog(this, "Seleccionar archivo ASM", FileDialog.LOAD);
        fileDialog.setSize(900, 650);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        fileDialog.setLocation((screenSize.width - 900) / 2, (screenSize.height - 650) / 2);
        fileDialog.setFile("*.asm");   // solo un patrón: FileDialog de AWT no soporta varios
        fileDialog.setDirectory(System.getProperty("user.home"));
        fileDialog.setVisible(true);

        String directorio = fileDialog.getDirectory();
        String archivoSeleccionado = fileDialog.getFile();

        if (archivoSeleccionado != null && directorio != null) {
            File archivo = new File(directorio, archivoSeleccionado);

            // Verificación real de extensión (el filtro del FileDialog es solo visual)
            String nombre = archivo.getName().toLowerCase();
            if (!nombre.endsWith(".asm")) {
                JOptionPane.showMessageDialog(this,
                        "Por favor selecciona un archivo .asm\n\nArchivo seleccionado: " + archivo.getName(),
                        "Formato incorrecto", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                Ensamblador ensamblador = new Ensamblador();

                if (!ensamblador.esArchivoValido(archivo)) {
                    JOptionPane.showMessageDialog(this,
                            "El archivo no es valido:\n" + ensamblador.getPrimerErrorEncontrado(),
                            "Archivo invalido", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Se lee en variable LOCAL: si algo falla, 'instrucciones' no se toca
                List<Instruccion> nuevasInstrucciones = ensamblador.leerArchivo(archivo);

                if (nuevasInstrucciones.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "El archivo esta vacio o no se pudo leer.\nAsegurate de que tenga instrucciones validas.",
                            "Archivo vacio", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                int posicionesNecesarias = nuevasInstrucciones.size() * POSICIONES_POR_INSTRUCCION;
                if (!memoria.cabeProgramaDeUsuario(posicionesNecesarias)) {
                    JOptionPane.showMessageDialog(this,
                            "El programa no cabe en la memoria disponible.\n"
                            + "Tamano del programa: " + posicionesNecesarias + " posiciones\n"
                            + "Memoria disponible: " + memoria.getEspacioUsuarioDisponible() + " posiciones\n\n"
                            + "Usa 'Ajustar memoria' para ampliarla, o presiona 'Limpiar' si ya hay un programa cargado.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Todo validado: ahora sí se asigna al campo
                instrucciones = nuevasInstrucciones;

                CargarMemoria cargador = new CargarMemoria();
                cargador.cargar(instrucciones, memoria);

                instruccionesEjecutadas = 0;
                llenarTablaInstrucciones();
                llenarTablaMemoria();
                actualizarRegistros();
                actualizarBarraProgreso();
                actualizarMensajeEstado("Programa cargado: " + instrucciones.size()
                        + " instrucciones desde " + archivo.getName());
                actualizarEstadoBotones();

            } catch (NumberFormatException e) {
                instrucciones = null;
                actualizarEstadoBotones();
                mostrarError("Error de formato numerico al leer el archivo:\n" + e.getMessage());
            } catch (RuntimeException e) {
                instrucciones = null;
                actualizarEstadoBotones();
                mostrarError("Error inesperado al leer el archivo:\n" + e.getMessage());
            }
        }
    }

    /**
     * Llena la tabla de instrucciones con el programa cargado.
     *
     * Muestra cada instrucción en formato ensamblador y su binario
     * equivalente (opcode + registro + valor).
     */
    private void llenarTablaInstrucciones() {
        modeloInstrucciones.setRowCount(0);
        Traductor traductor = new Traductor();

        for (Instruccion instr : instrucciones) {
            // Texto ensamblador
            String texto;
            if ("MOV".equals(instr.getOpcode())) {
                texto = instr.getOpcode() + " " + instr.getRegistro() + ", " + instr.getValor();
            } else {
                texto = instr.getOpcode() + " " + instr.getRegistro();
            }

            // Binario con separadores visuales (solo para mostrar)
            String completo = traductor.instruccionCompleta(instr);
            String binario = completo.substring(0, 4)   // opcode
                           + " "
                           + completo.substring(4, 8)   // registro
                           + " "
                           + completo.substring(8, 16); // valor

            modeloInstrucciones.addRow(new Object[]{texto, binario});
        }
    }

    /**
     * Llena la tabla de memoria con:
     *   - Las posiciones del kernel que tienen datos (el BCP).
     *   - Una fila "..." para indicar el resto del kernel.
     *   - Las posiciones del usuario ocupadas por el programa.
     *
     * Si {@code modoTraducido} es true, muestra la traduccion legible
     * en lugar del binario (tanto en el BCP como en las instrucciones).
     *
     * Si no hay programa cargado, igual se muestra el BCP del kernel
     * (que siempre existe) y luego el separador.
     */
    private void llenarTablaMemoria() {
        modeloMemoria.setRowCount(0);

        int limiteKernel = memoria.getLimiteKernelUsuario();

        // --- 1. Zona kernel: BCP ---
        int posicionesBCP = Math.min(BCP.POSICIONES_REQUERIDAS, limiteKernel);

        for (int i = 0; i < posicionesBCP; i++) {
            String binario = memoria.leer(i);
            String valor = modoTraducido
                    ? describirCampoBCP(i, binario)
                    : binario;
            modeloMemoria.addRow(new Object[]{i, valor});
        }

        // Separador: resto del kernel (vacío)
        if (limiteKernel > posicionesBCP) {
            modeloMemoria.addRow(new Object[]{"...", "..."});
        }

        // --- 2. Zona usuario: instrucciones ---
        if (instrucciones == null) {
            return;
        }

        int inicioUsuario = limiteKernel;
        int finUsuario = inicioUsuario + instrucciones.size() * POSICIONES_POR_INSTRUCCION;

        for (int i = inicioUsuario; i < finUsuario; i++) {
            String binario = memoria.leer(i);
            String valor = modoTraducido
                    ? traducirSimple(binario)
                    : binario;
            modeloMemoria.addRow(new Object[]{i, valor});
        }
    }

    /**
     * Ejecuta una única instrucción (modo paso a paso).
     *
     * Resalta la fila correspondiente en la tabla, actualiza registros,
     * barra de progreso y mensaje de estado. Si el programa termina,
     * marca el BCP como "TERMINADO".
     */
    private void ejecutarUnPaso() {
        if (instrucciones == null || instrucciones.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Primero carga un archivo .asm");
            return;
        }
        if (instruccionesEjecutadas >= instrucciones.size()) {
            JOptionPane.showMessageDialog(this, "El programa ya termino de ejecutarse.");
            return;
        }

        filaActual = instruccionesEjecutadas;
        tablaInstrucciones.scrollRectToVisible(tablaInstrucciones.getCellRect(filaActual, 0, true));

        ejecutor.ejecutarInstruccion();
        instruccionesEjecutadas++;
        llenarTablaMemoria();

        if (instruccionesEjecutadas >= instrucciones.size()) {
            finalizarEjecucion();
        } else {
            tablaInstrucciones.repaint();
            actualizarRegistros();
            actualizarBarraProgreso();
            actualizarMensajeEstado("Ejecutando instruccion " + instruccionesEjecutadas
                    + "/" + instrucciones.size());
        }

        // Forzar repintado general (Swing no siempre lo hace solo)
        revalidate();
        repaint();
    }

    /**
     * Ejecuta el programa completo de una sola vez (modo automático).
     *
     * Al terminar, marca el BCP como "TERMINADO" y actualiza
     * registros, barra y mensajes.
     */
    private void ejecutarProgramaCompleto() {
        if (instrucciones == null || instrucciones.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Primero carga un archivo .asm");
            return;
        }
        actualizarMensajeEstado("Ejecutando programa...");
        while (instruccionesEjecutadas < instrucciones.size()) {
            ejecutor.ejecutarInstruccion();
            instruccionesEjecutadas++;
        }
        llenarTablaMemoria();
        finalizarEjecucion();

        revalidate();
        repaint();
    }

    /**
     * Acciones comunes al terminar la ejecución de un programa:
     * marca el BCP como TERMINADO, limpia el resaltado y actualiza
     * registros, barra y mensaje de estado.
     */
    private void finalizarEjecucion() {
        ejecutor.getBcp().setEstado("TERMINADO");
        filaActual = -1;
        tablaInstrucciones.repaint();
        actualizarRegistros();
        actualizarBarraProgreso();
        llenarTablaMemoria();
        actualizarMensajeEstado("Programa completado!");
        progressBar.setForeground(COLOR_SUCCESS);
    }

    /**
     * Refresca las etiquetas de registros y del estado del BCP
     * con los valores actuales de la CPU.
     */
    private void actualizarRegistros() {
        lblId.setText("ID: " + ejecutor.getBcp().getId());
        lblPC.setText("PC: " + cpu.getPC());

        // IR se muestra como binario de 16 bits (con ceros a la izquierda)
        String irBinario = String.format("%16s", Integer.toBinaryString(cpu.getIR())).replace(' ', '0');
        lblIR.setText("IR: " + irBinario);

        lblAC.setText("AC: " + cpu.getAC());
        lblAX.setText("AX: " + cpu.getAX());
        lblBX.setText("BX: " + cpu.getBX());
        lblCX.setText("CX: " + cpu.getCX());
        lblDX.setText("DX: " + cpu.getDX());
        
        // Bandera de overflow (OF)
        lblOverflow.setText("OF: " + (cpu.getOverflow() ? "1" : "0"));
        if (cpu.getOverflow()) {
            lblOverflow.setBackground(new Color(231, 76, 60, 80));   // rojo suave
        } else {
            lblOverflow.setBackground(new Color(248, 249, 250));      // gris claro
        }

        lblEstado.setText("Estado: " + ejecutor.getBcp().getEstado());
        actualizarColorEstado();
    }

    /**
     * Cambia el color de fondo de la etiqueta de estado según
     * el estado actual del BCP.
     */
    private void actualizarColorEstado() {
        String estado = ejecutor.getBcp().getEstado();
        if ("TERMINADO".equals(estado)) {
            lblEstado.setBackground(new Color(46, 204, 113, 60));
        } else if ("EJECUTANDO".equals(estado)) {
            lblEstado.setBackground(new Color(230, 170, 20, 60));
        } else {
            lblEstado.setBackground(new Color(245, 245, 245));
        }
    }

    /**
     * Actualiza la barra de progreso según la cantidad de instrucciones
     * ya ejecutadas.
     */
    private void actualizarBarraProgreso() {
        if (instrucciones != null && !instrucciones.isEmpty()) {
            int progreso = (instruccionesEjecutadas * 100) / instrucciones.size();
            progressBar.setValue(progreso);
            progressBar.setString(progreso + "%");
            if (panelEstadisticas != null) {
                panelEstadisticas.repaint();
            }
        }
    }

    /**
     * Actualiza la etiqueta de mensaje de estado.
     *
     * @param mensaje texto a mostrar
     */
    private void actualizarMensajeEstado(String mensaje) {
        lblInfo.setText(mensaje);
        if (panelEstadisticas != null) {
            panelEstadisticas.repaint();
        }
    }

    /**
     * Muestra un mensaje de error estándar en un JOptionPane.
     *
     * @param mensaje texto a mostrar
     */
    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Sincroniza el estado de los botones que dependen de si hay
     * un programa cargado o no.
     *
     * - "Cargar archivo": se deshabilita si ya hay un programa (para
     *   evitar sobrescribirlo sin querer). Se habilita si no hay nada.
     * - "Ajustar memoria": se deshabilita si hay un programa (no se
     *   puede reconfigurar mientras hay instrucciones en memoria).
     *   Se habilita si no hay nada.
     */
    private void actualizarEstadoBotones() {
        boolean hayPrograma = (instrucciones != null);
        btnCargar.setEnabled(!hayPrograma);
        btnAjustarMemoria.setEnabled(!hayPrograma);
    }

    /**
     * Reinicia el sistema completo: crea memoria, CPU y ejecutor nuevos,
     * limpia las tablas, los registros y la barra de progreso.
     *
     * Vuelve también al modo automático y reactiva los botones
     * "Cargar archivo" y "Ajustar memoria".
     */
    private void limpiar() {
        memoria = new Memoria(tamanoMemoriaActual, limiteKernelActual);
        cpu = new CPU(limiteKernelActual);
        ejecutor = new EjecutorCPU(cpu, memoria);
        instrucciones = null;
        instruccionesEjecutadas = 0;
        filaActual = -1;
        modoTraducido = false;
        if (btnTraducir != null) {
            btnTraducir.setText("Traducir");
        }
        modeloInstrucciones.setRowCount(0);
        modeloMemoria.setRowCount(0);
        actualizarRegistros();
        llenarTablaMemoria();
        progressBar.setValue(0);
        progressBar.setString("0%");
        progressBar.setForeground(COLOR_SUCCESS);
        actualizarMensajeEstado("Sistema limpiado (memoria: " + tamanoMemoriaActual
                + " posiciones, kernel 0-" + (limiteKernelActual - 1) + ")");
        actualizarEstadoBotones();
        modoPasoAPaso = false;
        btnModo.setText("Modo: Automatico");
        btnAccion.setText("Ejecutar");

        revalidate();
        repaint();
    }

    /**
     * Muestra un diálogo con las estadísticas actuales del programa:
     * total de instrucciones, ejecutadas, restantes y estado del BCP.
     */
    private void mostrarEstadisticas() {
        if (instrucciones == null) {
            JOptionPane.showMessageDialog(this, "No hay programa cargado.");
            return;
        }
        String mensaje = "Estadisticas del programa:\n\n"
                + "Total de instrucciones: " + instrucciones.size() + "\n"
                + "Ejecutadas: " + instruccionesEjecutadas + "\n"
                + "Restantes: " + (instrucciones.size() - instruccionesEjecutadas) + "\n"
                + "Estado: " + ejecutor.getBcp().getEstado();

        JOptionPane.showMessageDialog(this, mensaje, "Estadisticas", JOptionPane.INFORMATION_MESSAGE);
    }

    /* ==================== TRADUCCION DE MEMORIA ==================== */

    /**
     * Describe el significado de un campo del BCP según su posición.
     *
     * @param posicion posición dentro del BCP (0-7)
     * @param binario  valor binario guardado
     * @return texto descriptivo (ej. "ID = 1", "PC = 64", "Estado = NUEVO")
     */
    private String describirCampoBCP(int posicion, String binario) {
        if (binario == null) return "(vacio)";

        try {
            switch (posicion) {
                case 0:  // ID
                    return "ID = " + CodificadorBinario.desdeBinario(binario);
                case 1:  // Estado (2 bits)
                    switch (binario) {
                        case "00": return "Estado = NUEVO";
                        case "01": return "Estado = EJECUTANDO";
                        case "10": return "Estado = TERMINADO";
                        default:   return "Estado = DESCONOCIDO";
                    }
                case 2:  // PC (16 bits sin signo)
                    return "PC = " + CodificadorBinario.desdeBinario(binario);
                case 3:  // AC (16 bits con signo)
                    return "AC = " + CodificadorBinario.desdeBinarioConSigno(binario);
                case 4:  // AX
                    return "AX = " + CodificadorBinario.desdeBinarioConSigno(binario);
                case 5:  // BX
                    return "BX = " + CodificadorBinario.desdeBinarioConSigno(binario);
                case 6:  // CX
                    return "CX = " + CodificadorBinario.desdeBinarioConSigno(binario);
                case 7:  // DX
                    return "DX = " + CodificadorBinario.desdeBinarioConSigno(binario);
                case 8:  // Flags (overflow)
                    return "OF = " + ("1".equals(binario) ? "1 (overflow)" : "0");
                default:
                    return "(desconocido)";
            }
        } catch (Exception e) {
            return "(formato invalido)";
        }
    }

    /**
     * Devuelve la traducción simple de una instrucción binaria
     * (ej. "MOV AX, 5").
     *
     * @param binario string de 16 bits
     * @return texto de la instrucción, o "(invalida)" si no se puede traducir
     */
    private String traducirSimple(String binario) {
        if (binario == null || binario.length() != 16 || !binario.matches("[01]+")) {
            return "(invalida)";
        }

        Traductor traductor = new Traductor();
        String opcode   = traductor.decodificarOpcode(binario.substring(0, 4));
        String registro = traductor.decodificarRegistro(binario.substring(4, 8));
        int    valor    = traductor.decodificarValor(binario.substring(8, 16));

        if (opcode == null || registro == null) {
            return "(desconocida)";
        }

        if ("MOV".equals(opcode)) {
            return opcode + " " + registro + ", " + valor;
        }
        return opcode + " " + registro;
    }
}