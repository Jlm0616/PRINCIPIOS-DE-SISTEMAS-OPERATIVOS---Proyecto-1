package gui;

import logica.GestorProcesos;
import logica.ListaDeTrabajos;
import logica.ParticionadorFijo;
import logica.ResultadoCarga;
import logica.BCPTerminado;
import modelo.BCP;
import modelo.CPU;
import modelo.Memoria;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.FileDialog;
import java.awt.Toolkit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Ventana principal del simulador (Proyecto 1).
 *
 * La configuracion de memoria (tamano total y limite kernel) se guarda
 * en campos de instancia para que "Limpiar" NO la resetee.
 *
 * La unica forma de cambiar la configuracion es a traves del boton
 * "Configurar", que abre VentanaConfiguracionMemoria.
 */
public class VentanaPrincipal extends JFrame {

    /* ==================== CONFIGURACION ==================== */

    private static final int TAMANO_MEMORIA_DEFAULT = 256;
    private static final int LIMITE_KERNEL_DEFAULT = (int) Math.ceil(TAMANO_MEMORIA_DEFAULT * 0.20);
    private static final int MAX_PROCESOS_DEFAULT = 5;
    private static final int TAMANO_DISCO_DEFAULT = 512;

    /* ==================== CONFIGURACION ACTUAL (persistente) ==================== */

    private int tamanoMemoriaActual = TAMANO_MEMORIA_DEFAULT;
    private int limiteKernelActual = LIMITE_KERNEL_DEFAULT;
    
    private int tamanoDiscoActual = TAMANO_DISCO_DEFAULT;

    /* ==================== MODELO ==================== */

    private Memoria memoria;
    private CPU cpu;
    private ListaDeTrabajos listaDeTrabajos;
    private ParticionadorFijo particionador;
    private GestorProcesos gestor;

    private List<String[]> indiceArchivos;

    /* ==================== PANELES ==================== */

    private PanelProcesos panelProcesos;
    private PanelMemoria panelMemoria;
    private PanelDisco panelDisco;
    private PanelPantalla panelPantalla;

    /* ==================== BOTONES ==================== */

    private JButton btnCargar;
    private JButton btnEjecutar;
    private JButton btnPasoAPaso;
    private JButton btnConfigurar;
    private JButton btnLimpiar;
    private JButton btnEstadisticas;

    /* ==================== CONSTRUCTOR ==================== */

    public VentanaPrincipal() {
        super("Proyecto 1 de SO");
        aplicarLookAndFeel();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1200, 700));
        setLocationRelativeTo(null);

        inicializarSistema();
        inicializarComponentes();
        refrescarTodo();
    }

    private void aplicarLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // usar default
        }
    }

    private void inicializarSistema() {
        memoria = new Memoria(tamanoMemoriaActual, limiteKernelActual);
        cpu = new CPU(limiteKernelActual);
        listaDeTrabajos = new ListaDeTrabajos();
        particionador = new ParticionadorFijo(
                limiteKernelActual,
                memoria.getEspacioUsuarioDisponible(),
                MAX_PROCESOS_DEFAULT);
        gestor = new GestorProcesos(memoria, cpu, listaDeTrabajos, particionador);
        indiceArchivos = new ArrayList<>();
    }

    private void inicializarComponentes() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(Paleta.FONDO_GENERAL);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(mainPanel);

        // === Encabezado: titulo + botones ===
        mainPanel.add(crearEncabezado(), BorderLayout.NORTH);

        // === Centro: Procesos | Memoria | Disco ===
        JPanel centro = new JPanel(new GridLayout(1, 3, 10, 10));
        centro.setOpaque(false);

        panelProcesos = new PanelProcesos();
        panelMemoria = new PanelMemoria();
        panelDisco = new PanelDisco();

        centro.add(panelProcesos);
        centro.add(panelMemoria);
        centro.add(panelDisco);
        mainPanel.add(centro, BorderLayout.CENTER);

        // === Sur: Pantalla ===
        panelPantalla = new PanelPantalla();
        mainPanel.add(panelPantalla, BorderLayout.SOUTH);

        // Configurar callbacks del gestor
        configurarCallbacks();
    }

    /**
     * Configura los callbacks de interrupciones del gestor.
     *
     * Se debe llamar cada vez que se recrea el gestor (en el constructor
     * y en limpiar()/abrirConfiguracion()).
     */
    private void configurarCallbacks() {
        // INT 10H: salida a pantalla
        gestor.setSalidaPantalla(mensaje -> {
            panelPantalla.agregarMensaje(mensaje);
        });

        // INT 09H: solicitud de teclado
        gestor.setSolicitudTeclado(bcp -> {
            panelPantalla.habilitarEntrada(true);
            panelPantalla.agregarMensaje(">> Proceso " + bcp.getId()
                    + " esperando input de teclado (0-255)...");
        });

        // INT 21H: solicitud de archivos
        gestor.setSolicitudArchivo(bcp -> {
            panelPantalla.agregarMensaje(">> Proceso " + bcp.getId()
                    + " solicita operacion de archivo (pendiente)");
        });

        // Callback de la consola (cuando el usuario envia un valor)
        panelPantalla.setOnEnviar(valor -> {
            boolean desbloqueado = gestor.desbloquearProceso(valor);
            if (desbloqueado) {
                panelPantalla.agregarMensaje(">> Valor " + valor
                        + " enviado al proceso desbloqueado (DX=" + valor + ")");
                refrescarTodo();
            } else {
                panelPantalla.agregarMensaje(">> No hay procesos esperando input.");
            }
        });
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);

        // --- Titulo ---
        JPanel panelTitulo = new JPanel(new BorderLayout());
        panelTitulo.setBackground(Paleta.FONDO_TITULO);
        panelTitulo.setBorder(new EmptyBorder(12, 20, 12, 20));

        JLabel lblTitulo = new JLabel("Proyecto 1 de SO");
        lblTitulo.setFont(Paleta.FUENTE_TITULO);
        lblTitulo.setForeground(Paleta.TEXTO_CLARO);
        panelTitulo.add(lblTitulo, BorderLayout.WEST);

        encabezado.add(panelTitulo, BorderLayout.NORTH);

        // --- Botones ---
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panelBotones.setBackground(Paleta.FONDO_PANEL);
        panelBotones.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, Paleta.VERDE_OSCURO));

        btnCargar       = crearBoton("Cargar archivos", Paleta.VERDE_PRINCIPAL);
        btnEjecutar     = crearBoton("Ejecutar", Paleta.VERDE_PRINCIPAL);
        btnPasoAPaso    = crearBoton("Paso a paso", Paleta.MORADO_ACENTO);
        btnConfigurar   = crearBoton("Configurar", Paleta.AMARILLO_ADVERTENCIA);
        btnLimpiar      = crearBoton("Limpiar", Paleta.ROJO_PELIGRO);
        btnEstadisticas = crearBoton("Estadisticas", Paleta.AMARILLO_ADVERTENCIA);

        panelBotones.add(btnCargar);
        panelBotones.add(btnEjecutar);
        panelBotones.add(btnPasoAPaso);
        panelBotones.add(btnConfigurar);
        panelBotones.add(btnLimpiar);
        panelBotones.add(btnEstadisticas);

        btnCargar.addActionListener(e -> cargarArchivo());
        btnEjecutar.addActionListener(e -> ejecutarAutomatico());
        btnPasoAPaso.addActionListener(e -> ejecutarUnPaso());
        btnConfigurar.addActionListener(e -> abrirConfiguracion());
        btnLimpiar.addActionListener(e -> limpiar());
        btnEstadisticas.addActionListener(e -> mostrarEstadisticas());

        encabezado.add(panelBotones, BorderLayout.SOUTH);

        return encabezado;
    }

    private JButton crearBoton(String texto, Color color) {
        JButton boton = new JButton(texto);
        boton.setPreferredSize(new Dimension(150, 36));
        boton.setFont(Paleta.FUENTE_BOTON);
        boton.setBackground(color);
        boton.setForeground(Paleta.TEXTO_CLARO);
        boton.setFocusPainted(false);
        boton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        boton.setUI(new javax.swing.plaf.metal.MetalButtonUI());
        boton.setOpaque(true);
        boton.setBorderPainted(false);
        return boton;
    }

    /* ==================== ACCIONES ==================== */

    private void cargarArchivo() {
        FileDialog fileDialog = new FileDialog(this, "Seleccionar archivo ASM", FileDialog.LOAD);
        fileDialog.setSize(900, 650);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        fileDialog.setLocation((screen.width - 900) / 2, (screen.height - 650) / 2);
        fileDialog.setFile("*.asm");
        fileDialog.setDirectory(System.getProperty("user.home"));
        fileDialog.setVisible(true);

        String directorio = fileDialog.getDirectory();
        String archivoSeleccionado = fileDialog.getFile();

        if (archivoSeleccionado == null || directorio == null) {
            return;
        }

        File archivo = new File(directorio, archivoSeleccionado);

        if (!archivo.getName().toLowerCase().endsWith(".asm")) {
            JOptionPane.showMessageDialog(this,
                    "Por favor selecciona un archivo .asm",
                    "Formato incorrecto", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ResultadoCarga resultado = gestor.cargarPrograma(archivo);

        switch (resultado.getEstado()) {
            case EXITO:
                panelPantalla.agregarMensaje(">> Cargado: " + archivo.getName()
                        + " (ID=" + resultado.getBcp().getId() + ")");
                indiceArchivos.add(new String[]{
                    archivo.getName(),
                    "dir " + resultado.getBcp().getBase()
                });
                break;

            case ERROR:
                JOptionPane.showMessageDialog(this,
                        "Error al cargar:\n" + resultado.getMensajeError(),
                        "Archivo invalido", JOptionPane.ERROR_MESSAGE);
                return;

            case EN_ESPERA:
                panelPantalla.agregarMensaje(">> " + archivo.getName()
                        + " en espera (no hay particion libre)");
                break;
        }

        refrescarTodo();
    }

    private void ejecutarUnPaso() {
        if (!gestor.hayProcesosActivos()) {
            JOptionPane.showMessageDialog(this,
                    "No hay procesos para ejecutar.\nCarga un archivo .asm primero.");
            return;
        }

        boolean ejecutado = gestor.ejecutarUnPaso();
        if (ejecutado) {
            refrescarTodo();
        }
    }

    private void ejecutarAutomatico() {
        if (!gestor.hayProcesosActivos()) {
            JOptionPane.showMessageDialog(this,
                    "No hay procesos para ejecutar.\nCarga un archivo .asm primero.");
            return;
        }

        try {
            int pasos = gestor.ejecutarAutomatico();
            panelPantalla.agregarMensaje(">> Ejecucion automatica completada ("
                    + pasos + " pasos)");
            refrescarTodo();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error durante la ejecucion:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirConfiguracion() {
        if (gestor.hayProcesosActivos()) {
            JOptionPane.showMessageDialog(this,
                    "No se puede configurar mientras hay procesos activos.\n"
                    + "Presiona 'Limpiar' primero.",
                    "Sistema en uso", JOptionPane.WARNING_MESSAGE);
            return;
        }

    VentanaConfiguracion dialogo = new VentanaConfiguracion(
            this,
            tamanoMemoriaActual,
            limiteKernelActual,
            tamanoDiscoActual);   // ← podés agregar tamanoDiscoActual persistente

    dialogo.setVisible(true);

    if (!dialogo.isConfirmado()) {
        return;
    }

    int nuevoTamano = dialogo.getTamanoMemoria();
    int nuevoLimite = dialogo.getLimiteKernel();
    int nuevoDisco = dialogo.getTamanoDisco();

        this.tamanoMemoriaActual = nuevoTamano;
        this.limiteKernelActual = nuevoLimite;
        this.tamanoDiscoActual = dialogo.getTamanoDisco(); 

        memoria = new Memoria(nuevoTamano, nuevoLimite);
        cpu = new CPU(nuevoLimite);
        listaDeTrabajos = new ListaDeTrabajos();
        particionador = new ParticionadorFijo(
                nuevoLimite,
                memoria.getEspacioUsuarioDisponible(),
                MAX_PROCESOS_DEFAULT);
        gestor = new GestorProcesos(memoria, cpu, listaDeTrabajos, particionador);
        configurarCallbacks();
        indiceArchivos.clear();

        panelPantalla.limpiar();
        panelPantalla.agregarMensaje(">> Configuracion aplicada:");
        panelPantalla.agregarMensaje(">>   Memoria: " + nuevoTamano + " posiciones");
        panelPantalla.agregarMensaje(">>   Kernel: 0-" + (nuevoLimite - 1)
                + ", Usuario: " + nuevoLimite + "-" + (nuevoTamano - 1));
        panelPantalla.agregarMensaje(">>   Disco: " + tamanoDiscoActual + " posiciones");

        refrescarTodo();
    }

    private void limpiar() {
        inicializarSistema();
        configurarCallbacks();
        indiceArchivos.clear();
        panelPantalla.limpiar();
        panelPantalla.agregarMensaje(">> Sistema reiniciado (memoria: "
                + tamanoMemoriaActual + " posiciones, kernel 0-"
                + (limiteKernelActual - 1) + ")");
        refrescarTodo();
    }

    private void mostrarEstadisticas() {
        List<BCPTerminado> terminados = gestor.getProcesosTerminados();
        if (terminados.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No hay procesos terminados todavia.",
                    "Estadisticas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Estadisticas de procesos terminados:\n\n");
        for (BCPTerminado pt : terminados) {
            sb.append("Proceso ").append(pt.getId()).append(":\n");
            sb.append("  Inicio:   ").append(pt.getTiempoInicio()).append("\n");
            sb.append("  Fin:      ").append(pt.getTiempoFin()).append("\n");
            sb.append("  Duracion: ").append(pt.getDuracionSegundos()).append("s\n");
            sb.append("  Estado:   ").append(pt.getEstado()).append("\n\n");
        }

        JOptionPane.showMessageDialog(this, sb.toString(),
                "Estadisticas", JOptionPane.INFORMATION_MESSAGE);
    }

    /* ==================== REFRESCO ==================== */

    private void refrescarTodo() {
        BCP actual = gestor.getProcesoActual();

        panelProcesos.actualizarLista(
                listaDeTrabajos,
                actual,
                gestor.getProcesosBloqueados(),
                gestor.getProcesosTerminados(),
                gestor.getProcesosEnEspera());
        panelProcesos.actualizarBCP(actual);

        int ir = (actual != null) ? actual.getIr() : -1;
        panelMemoria.actualizar(memoria, ir);
        panelDisco.actualizar(indiceArchivos, tamanoDiscoActual);
    }
}