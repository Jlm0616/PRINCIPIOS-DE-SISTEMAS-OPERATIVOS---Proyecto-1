package gui;

import logica.GestorProcesos;
import logica.ListaDeTrabajos;
import logica.ParticionadorFijo;
import logica.ResultadoCarga;
import logica.BCPTerminado;
import modelo.BCP;
import modelo.CPU;
import modelo.Memoria;
import modelo.MemoriaVirtual;
import modelo.Disco;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.FileDialog;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VentanaPrincipal extends JFrame {

    private static final int TAMANO_MEMORIA_DEFAULT = 256;
    private static final int LIMITE_KERNEL_DEFAULT = (int) Math.ceil(TAMANO_MEMORIA_DEFAULT * 0.20);
    private static final int MAX_PROCESOS_DEFAULT = 5;
    private static final int TAMANO_DISCO_DEFAULT = 512;
    private static final int TAMANO_MEMORIA_VIRTUAL_DEFAULT = 64;

    private int tamanoMemoriaActual = TAMANO_MEMORIA_DEFAULT;
    private int limiteKernelActual = LIMITE_KERNEL_DEFAULT;
    private int tamanoDiscoActual = TAMANO_DISCO_DEFAULT;
    private int tamanoMemoriaVirtualActual = TAMANO_MEMORIA_VIRTUAL_DEFAULT;

    private Memoria memoria;
    private MemoriaVirtual memoriaVirtual;
    private CPU cpu;
    private ListaDeTrabajos listaDeTrabajos;
    private ParticionadorFijo particionador;
    private GestorProcesos gestor;
    private Disco disco;

    private PanelProcesos panelProcesos;
    private PanelMemoria panelMemoria;
    private PanelMemoriaVirtual panelMemoriaVirtual;
    private PanelDisco panelDisco;
    private PanelPantalla panelPantalla;

    private JButton btnCargar;
    private JButton btnEjecutar;
    private JButton btnPasoAPaso;
    private JButton btnConfigurar;
    private JButton btnLimpiar;
    private JButton btnEstadisticas;

    /** Worker del modo automático (corre en hilo aparte). */
    private SwingWorker<Integer, Void> workerAutomatico;

    public VentanaPrincipal() {
        super("Proyecto 1 de SO");
        aplicarLookAndFeel();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1200, 700));
        setLocationRelativeTo(null);

        // Cancelar el worker si se cierra la ventana
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (workerAutomatico != null && !workerAutomatico.isDone()) {
                    workerAutomatico.cancel(true);
                }
            }
        });

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
        memoriaVirtual = new MemoriaVirtual(tamanoMemoriaVirtualActual);
        cpu = new CPU(limiteKernelActual);
        disco = new Disco(tamanoDiscoActual);
        listaDeTrabajos = new ListaDeTrabajos();
        particionador = new ParticionadorFijo(
                limiteKernelActual,
                memoria.getEspacioUsuarioDisponible(),
                MAX_PROCESOS_DEFAULT);
        gestor = new GestorProcesos(memoria, memoriaVirtual, cpu,
                                    listaDeTrabajos, particionador, disco);
    }

    private void inicializarComponentes() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(Paleta.FONDO_GENERAL);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(mainPanel);

        mainPanel.add(crearEncabezado(), BorderLayout.NORTH);

        JPanel centro = new JPanel(new GridLayout(1, 4, 10, 10));
        centro.setOpaque(false);

        panelProcesos = new PanelProcesos();
        panelMemoria = new PanelMemoria();
        panelMemoriaVirtual = new PanelMemoriaVirtual();
        panelDisco = new PanelDisco();

        centro.add(panelProcesos);
        centro.add(panelMemoria);
        centro.add(panelMemoriaVirtual);
        centro.add(panelDisco);
        mainPanel.add(centro, BorderLayout.CENTER);

        panelPantalla = new PanelPantalla();
        mainPanel.add(panelPantalla, BorderLayout.SOUTH);

        configurarCallbacks();
    }

    private void configurarCallbacks() {
        gestor.setSalidaPantalla(mensaje -> panelPantalla.agregarMensaje(mensaje));

        gestor.setSolicitudTeclado(bcp -> {
            panelPantalla.habilitarEntrada(true);
            panelPantalla.agregarMensaje(">> Proceso " + bcp.getId()
                    + " esperando input de teclado (0-255)...");
        });

        gestor.setSolicitudArchivo(bcp -> {
            int ah = bcp.getAh();
            String operacion;
            switch (ah) {
                case 0x3C: operacion = "crear"; break;
                case 0x3D: operacion = "abrir"; break;
                case 0x4D: operacion = "leer"; break;
                case 0x40: operacion = "escribir"; break;
                case 0x41: operacion = "eliminar"; break;
                default:   operacion = "desconocida"; break;
            }
            panelPantalla.agregarMensaje(">> [DISCO] Proceso " + bcp.getId()
                    + " -> " + operacion + " archivo_" + bcp.getDx());
        });

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

        JPanel panelTitulo = new JPanel(new BorderLayout());
        panelTitulo.setBackground(Paleta.FONDO_TITULO);
        panelTitulo.setBorder(new EmptyBorder(12, 20, 12, 20));

        JLabel lblTitulo = new JLabel("Proyecto 1 de SO");
        lblTitulo.setFont(Paleta.FUENTE_TITULO);
        lblTitulo.setForeground(Paleta.TEXTO_CLARO);
        panelTitulo.add(lblTitulo, BorderLayout.WEST);

        encabezado.add(panelTitulo, BorderLayout.NORTH);

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

        if (archivoSeleccionado == null || directorio == null) return;

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

                try {
                    String contenidoAsm = new String(
                            java.nio.file.Files.readAllBytes(archivo.toPath()));
                    disco.guardar(archivo.getName(), contenidoAsm);
                } catch (Exception ex) {
                    panelPantalla.agregarMensaje(">> [ADVERTENCIA] No se pudo guardar el .asm: "
                            + ex.getMessage());
                }
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
        if (gestor.ejecutarUnPaso()) refrescarTodo();
    }

    /**
     * Ejecuta todos los procesos en modo automático.
     *
     * Corre en un SwingWorker (hilo aparte), respetando 1 segundo real
     * por paso. Si hay procesos esperando input, el worker se pausa y
     * el tiempo sigue contando hasta que el usuario ingrese un valor.
     */
    private void ejecutarAutomatico() {
        if (!gestor.hayProcesosActivos()) {
            JOptionPane.showMessageDialog(this,
                    "No hay procesos para ejecutar.\nCarga un archivo .asm primero.");
            return;
        }

        deshabilitarBotones();

        workerAutomatico = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return gestor.ejecutarAutomatico(() -> {
                    // Notificar a la GUI desde el hilo del worker
                    publish((Void) null);
                });
            }

            @Override
            protected void process(List<Void> chunks) {
                // Se ejecuta en el hilo de Swing
                refrescarTodo();
            }

            @Override
            protected void done() {
                try {
                    int pasos = get();
                    panelPantalla.agregarMensaje(">> Ejecucion automatica completada ("
                            + pasos + " pasos)");
                } catch (java.util.concurrent.CancellationException ex) {
                    panelPantalla.agregarMensaje(">> Ejecucion automatica cancelada.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(VentanaPrincipal.this,
                            "Error durante la ejecucion:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    habilitarBotones();
                    refrescarTodo();
                }
            }
        };
        workerAutomatico.execute();
    }

    private void deshabilitarBotones() {
        btnCargar.setEnabled(false);
        btnEjecutar.setEnabled(false);
        btnPasoAPaso.setEnabled(false);
        btnLimpiar.setEnabled(false);
        btnConfigurar.setEnabled(false);
    }

    private void habilitarBotones() {
        btnCargar.setEnabled(true);
        btnEjecutar.setEnabled(true);
        btnPasoAPaso.setEnabled(true);
        btnLimpiar.setEnabled(true);
        btnConfigurar.setEnabled(true);
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
                tamanoMemoriaVirtualActual,
                tamanoDiscoActual);

        dialogo.setVisible(true);

        if (!dialogo.isConfirmado()) return;

        int nuevoTamano = dialogo.getTamanoMemoria();
        int nuevoLimite = dialogo.getLimiteKernel();
        int nuevoMemVirtual = dialogo.getTamanoMemoriaVirtual();
        int nuevoDisco  = dialogo.getTamanoDisco();

        this.tamanoMemoriaActual = nuevoTamano;
        this.limiteKernelActual = nuevoLimite;
        this.tamanoMemoriaVirtualActual = nuevoMemVirtual;
        this.tamanoDiscoActual = nuevoDisco;

        memoria = new Memoria(nuevoTamano, nuevoLimite);
        memoriaVirtual = new MemoriaVirtual(nuevoMemVirtual);
        cpu = new CPU(nuevoLimite);
        disco = new Disco(nuevoDisco);
        listaDeTrabajos = new ListaDeTrabajos();
        particionador = new ParticionadorFijo(
                nuevoLimite,
                memoria.getEspacioUsuarioDisponible(),
                MAX_PROCESOS_DEFAULT);
        gestor = new GestorProcesos(memoria, memoriaVirtual, cpu,
                                    listaDeTrabajos, particionador, disco);

        configurarCallbacks();

        panelPantalla.limpiar();
        mostrarMensajeConfiguracion();

        refrescarTodo();
    }

    private void limpiar() {
        inicializarSistema();
        configurarCallbacks();
        panelPantalla.limpiar();
        mostrarMensajeConfiguracion();
        refrescarTodo();
    }

    private void mostrarMensajeConfiguracion() {
        panelPantalla.agregarMensaje(">> Configuracion aplicada:");
        panelPantalla.agregarMensaje(">>   Memoria: " + tamanoMemoriaActual + " posiciones");
        panelPantalla.agregarMensaje(">>   Kernel: 0-" + (limiteKernelActual - 1)
                + ", Usuario: " + limiteKernelActual + "-" + (tamanoMemoriaActual - 1));
        panelPantalla.agregarMensaje(">>   Memoria virtual: " + tamanoMemoriaVirtualActual
                + " posiciones");
        panelPantalla.agregarMensaje(">>   Disco: " + tamanoDiscoActual + " posiciones");
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

        panelMemoriaVirtual.actualizar(memoriaVirtual);

        List<String[]> entradas = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : disco.getIndice().entrySet()) {
            entradas.add(new String[]{ e.getKey(), "dir " + e.getValue() });
        }
        panelDisco.actualizar(entradas, tamanoDiscoActual);
    }
}