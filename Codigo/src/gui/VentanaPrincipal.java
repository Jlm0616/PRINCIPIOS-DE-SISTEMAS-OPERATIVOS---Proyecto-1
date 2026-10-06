package gui;

import config.ConfiguracionExterna;
import logica.GestorProcesos;
import logica.ListaProcesos;
import logica.ListaDeTrabajos;
import logica.ParticionadorDinamico;
import logica.ResultadoCarga;
import logica.BCPTerminado;
import modelo.BCP;
import modelo.CPU;
import modelo.Memoria;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class VentanaPrincipal extends JFrame {

    private ConfiguracionExterna config;

    // Valores actuales
    private int tamanoMemoriaActual;
    private int maxProcesosActual;
    private int tamanoDiscoActual;
    private int maxArchivosActual;
    private int tamanoSwapActual;
    private int limiteKernelActual;

    private Memoria memoria;
    private CPU cpu;
    private ListaProcesos listaProcesos;
    private ListaDeTrabajos listaDeTrabajos;
    private ParticionadorDinamico particionador;
    private GestorProcesos gestor;
    private Disco disco;

    private PanelProcesos panelProcesos;
    private PanelMemoria panelMemoria;
    private PanelDisco panelDisco;
    private PanelPantalla panelPantalla;

    private JButton btnCargar;
    private JButton btnEjecutar;
    private JButton btnPasoAPaso;
    private JButton btnConfigurar;
    private JButton btnLimpiar;
    private JButton btnEstadisticas;

    private SwingWorker<Integer, Void> workerAutomatico;

    /** Formato de hora para las estadisticas (hora:minuto). */
    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    public VentanaPrincipal() {
        super("Proyecto 1 de SO");

        config = new ConfiguracionExterna();
        config.cargar();
        aplicarConfiguracion(config);

        aplicarLookAndFeel();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1200, 700));
        setLocationRelativeTo(null);

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

        // ==== Mostrar correcciones automaticas ====
        if (config.huboCorrecciones()) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Se encontraron valores invalidos en config.txt\n"
                        + "y se corrigieron automaticamente:\n\n"
                        + config.getMensajesCorrecciones()
                        + "\nEl archivo config.txt fue actualizado.",
                        "Configuracion corregida",
                        JOptionPane.WARNING_MESSAGE);
            });
        }
    }

    private void aplicarConfiguracion(ConfiguracionExterna config) {
        this.tamanoMemoriaActual = config.getMemoria();
        this.limiteKernelActual = config.getKernel();
        this.maxProcesosActual = config.getMaxProcesos();
        this.tamanoDiscoActual = config.getDisco();
        this.maxArchivosActual = config.getMaxArchivos();
        this.tamanoSwapActual = config.getMemoriaVirtual();
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
        try {
            memoria = new Memoria(tamanoMemoriaActual, limiteKernelActual, maxProcesosActual);
            cpu = new CPU(limiteKernelActual);
            disco = new Disco(tamanoDiscoActual, maxArchivosActual, tamanoSwapActual);

            listaProcesos = new ListaProcesos(memoria);
            listaDeTrabajos = new ListaDeTrabajos();

            particionador = new ParticionadorDinamico(
                    memoria,
                    memoria.getLimiteKernelUsuario(),
                    memoria.getEspacioUsuarioDisponible());

            gestor = new GestorProcesos(memoria, cpu,
                                        listaProcesos, listaDeTrabajos,
                                        particionador, disco);
        } catch (IllegalArgumentException e) {
            int kernelMinimoParaUno = Memoria.getTamanoKernelMinimo(1);

            JOptionPane.showMessageDialog(this,
                    "No se puede inicializar el sistema:\n\n"
                    + e.getMessage() + "\n\n"
                    + "El kernel debe ser al menos " + kernelMinimoParaUno
                    + " posiciones para 1 proceso.\n"
                    + "Con memoria=" + tamanoMemoriaActual
                    + " y kernel=" + limiteKernelActual
                    + " no alcanza.\n\n"
                    + "Edita config.txt o usa 'Configurar' para ajustar.",
                    "Error de configuracion",
                    JOptionPane.ERROR_MESSAGE);
            throw e;
        }
    }

    private void inicializarComponentes() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(Paleta.FONDO_GENERAL);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(mainPanel);

        mainPanel.add(crearEncabezado(), BorderLayout.NORTH);

        JPanel centro = new JPanel(new GridLayout(1, 3, 10, 10));
        centro.setOpaque(false);

        panelProcesos = new PanelProcesos();
        panelMemoria = new PanelMemoria();
        panelDisco = new PanelDisco();

        centro.add(panelProcesos);
        centro.add(panelMemoria);
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
                    + " -> " + operacion + " \"" + bcp.getDx() + "\"");
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
        FileDialog fileDialog = new FileDialog(this, "Seleccionar archivos ASM", FileDialog.LOAD);
        fileDialog.setMultipleMode(true);
        fileDialog.setSize(900, 650);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        fileDialog.setLocation((screen.width - 900) / 2, (screen.height - 650) / 2);
        fileDialog.setFile("*.asm");
        fileDialog.setDirectory(System.getProperty("user.home"));
        fileDialog.setVisible(true);

        File[] archivos = fileDialog.getFiles();

        if (archivos == null || archivos.length == 0) return;

        int cargadosExito = 0;
        int enEspera = 0;
        int conError = 0;

        for (File archivo : archivos) {
            int resultado = cargarUnArchivo(archivo);
            switch (resultado) {
                case 1: cargadosExito++; break;
                case 0: conError++; break;
                case -1: enEspera++; break;
            }
        }

        if (archivos.length > 1) {
            panelPantalla.agregarMensaje("");
            panelPantalla.agregarMensaje(">> ===== RESUMEN DE CARGA =====");
            panelPantalla.agregarMensaje(">> Archivos seleccionados: " + archivos.length);
            panelPantalla.agregarMensaje(">> Cargados en RAM:        " + cargadosExito);
            panelPantalla.agregarMensaje(">> En espera (swap):       " + enEspera);
            panelPantalla.agregarMensaje(">> Con error:              " + conError);
            panelPantalla.agregarMensaje(">> ============================");
            panelPantalla.agregarMensaje("");
        }

        refrescarTodo();
    }

    private int cargarUnArchivo(File archivo) {
        if (!archivo.getName().toLowerCase().endsWith(".asm")) {
            JOptionPane.showMessageDialog(this,
                    "Se omite \"" + archivo.getName() + "\": no es un archivo .asm",
                    "Formato incorrecto", JOptionPane.WARNING_MESSAGE);
            return 0;
        }

        ResultadoCarga resultado = gestor.cargarPrograma(archivo);

        switch (resultado.getEstado()) {
            case EXITO:
                panelPantalla.agregarMensaje(">> Cargado: " + archivo.getName()
                        + " (ID=" + resultado.getBcp().getId() + ")");

                try {
                    String contenidoAsm = new String(
                            java.nio.file.Files.readAllBytes(archivo.toPath()));
                    guardarAsmEnDisco(archivo.getName(), contenidoAsm);
                } catch (Exception ex) {
                    panelPantalla.agregarMensaje(">> [ADVERTENCIA] No se pudo guardar el .asm: "
                            + ex.getMessage());
                }
                return 1;

            case ERROR:
                JOptionPane.showMessageDialog(this,
                        "Error al cargar \"" + archivo.getName() + "\":\n"
                        + resultado.getMensajeError(),
                        "Archivo invalido", JOptionPane.ERROR_MESSAGE);
                return 0;

            case EN_ESPERA:
                panelPantalla.agregarMensaje(">> " + archivo.getName()
                        + " en espera (no hay hueco suficiente)");
                return -1;
        }

        return 0;
    }

    private void guardarAsmEnDisco(String nombre, String contenido) {
        // Si ya existe: no duplicar
        if (disco.existe(nombre)) {
            panelPantalla.agregarMensaje(">> " + nombre
                    + " ya está en disco (no se duplica)");
            return;
        }

        String[] lineas = contenido.split("\\R");
        int cantidadLineas = 0;
        for (String linea : lineas) {
            if (!linea.trim().isEmpty()) cantidadLineas++;
        }

        // Validacion 1: indice lleno
        if (disco.getCantidadArchivos() >= disco.getMaxArchivos()) {
            panelPantalla.agregarMensaje(">> [ERROR] No se puede guardar " + nombre
                    + ": el índice de archivos está lleno ("
                    + disco.getCantidadArchivos() + "/" + disco.getMaxArchivos() + ")");
            return;
        }

        // Validacion 2: sin espacio en zona de archivos
        if (disco.getEspacioArchivosLibre() < cantidadLineas) {
            panelPantalla.agregarMensaje(">> [ERROR] No se puede guardar " + nombre
                    + ": sin espacio en la zona de archivos (necesario "
                    + cantidadLineas + " posiciones, libre "
                    + disco.getEspacioArchivosLibre() + ")");
            return;
        }

        int inicio = disco.reservarBloqueArchivo(cantidadLineas);
        if (inicio == -1) {
            panelPantalla.agregarMensaje(">> [ERROR] No se puede guardar " + nombre
                    + ": no hay bloque contiguo de " + cantidadLineas + " posiciones");
            return;
        }

        int i = 0;
        for (String linea : lineas) {
            if (linea.trim().isEmpty()) continue;
            disco.escribir(inicio + i, linea.trim());
            i++;
        }
        int fin = inicio + cantidadLineas - 1;

        boolean registrado = disco.registrarArchivo(nombre, inicio, fin);
        if (!registrado) {
            disco.liberarBloqueArchivo(inicio, cantidadLineas);
            panelPantalla.agregarMensaje(">> [ERROR] No se pudo registrar " + nombre
                    + " en el índice. Bloque liberado.");
            return;
        }

        panelPantalla.agregarMensaje(">> Guardado en disco: " + nombre
                + " (" + cantidadLineas + " posiciones en [" + inicio + ".." + fin + "])");
    }

    private void ejecutarUnPaso() {
        if (!gestor.hayProcesosActivos()) {
            JOptionPane.showMessageDialog(this,
                    "No hay procesos para ejecutar.\nCarga un archivo .asm primero.");
            return;
        }
        if (gestor.ejecutarUnPaso()) {
            refrescarTodo();

            if (!gestor.hayProcesosActivos()) {
                imprimirEstadisticasEnPantalla();
            }
        }
    }

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
                    publish((Void) null);
                });
            }

            @Override
            protected void process(List<Void> chunks) {
                refrescarTodo();
            }

            @Override
            protected void done() {
                try {
                    int pasos = get();
                    panelPantalla.agregarMensaje(">> Ejecucion automatica completada ("
                            + pasos + " pasos)");

                    imprimirEstadisticasEnPantalla();
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
                tamanoDiscoActual,
                maxArchivosActual,
                tamanoSwapActual,
                maxProcesosActual);

        dialogo.setVisible(true);

        if (!dialogo.isConfirmado()) return;

        int nuevoTamano = dialogo.getTamanoMemoria();
        int nuevoKernel = dialogo.getLimiteKernel();
        int nuevoDisco  = dialogo.getTamanoDisco();
        int nuevoMaxArchivos = dialogo.getMaxArchivos();
        int nuevoSwap   = dialogo.getTamanoSwap();

        this.tamanoMemoriaActual = nuevoTamano;
        this.limiteKernelActual = nuevoKernel;
        this.tamanoDiscoActual = nuevoDisco;
        this.maxArchivosActual = nuevoMaxArchivos;
        this.tamanoSwapActual = nuevoSwap;

        config.setMemoria(nuevoTamano);
        config.setKernel(nuevoKernel);
        config.setDisco(nuevoDisco);
        config.setMaxArchivos(nuevoMaxArchivos);
        config.setMemoriaVirtual(nuevoSwap);
        config.guardar();

        inicializarSistema();
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
        panelPantalla.agregarMensaje(">>   BCPs disponibles en kernel: "
                + memoria.getBcpsQueCaben()
                + " (configurados: " + maxProcesosActual + ")");
        panelPantalla.agregarMensaje(">>   Disco: " + tamanoDiscoActual
                + " posiciones (maxArchivos=" + maxArchivosActual
                + ", swap=" + tamanoSwapActual + ")");
        panelPantalla.agregarMensaje(">>     Indice: " + disco.getInicioIndice()
                + "-" + (disco.getInicioSwap() - 1));
        panelPantalla.agregarMensaje(">>     Swap: " + disco.getInicioSwap()
                + "-" + (disco.getInicioArchivos() - 1));
        panelPantalla.agregarMensaje(">>     Archivos: " + disco.getInicioArchivos()
                + "-" + (tamanoDiscoActual - 1));
    }

    private void imprimirEstadisticasEnPantalla() {
        List<BCPTerminado> terminados = gestor.getProcesosTerminados();
        if (terminados.isEmpty()) return;

        panelPantalla.agregarMensaje("");
        panelPantalla.agregarMensaje(">> ========== ESTADISTICAS ==========");
        panelPantalla.agregarMensaje(">> Proceso | Inicio | Fin    | Duracion");
        panelPantalla.agregarMensaje(">> ----------------------------------");

        long duracionTotal = 0;
        for (BCPTerminado pt : terminados) {
            long dur = pt.getDuracionSegundos();
            if (dur > 0) duracionTotal += dur;

            String inicio = formatearHora(pt.getTiempoInicio());
            String fin    = formatearHora(pt.getTiempoFin());

            panelPantalla.agregarMensaje(String.format(
                    ">> ID %-4d | %s  | %s | %d s",
                    pt.getId(), inicio, fin, dur));
        }

        panelPantalla.agregarMensaje(">> ----------------------------------");
        panelPantalla.agregarMensaje(">> Total de procesos: " + terminados.size());
        panelPantalla.agregarMensaje(">> Duracion total:    " + duracionTotal + " s");
        panelPantalla.agregarMensaje(">> ==================================");
        panelPantalla.agregarMensaje("");
    }

    private String formatearHora(LocalDateTime t) {
        if (t == null) return "  -  ";
        return t.format(FORMATO_HORA);
    }

    private void mostrarEstadisticas() {
        List<BCPTerminado> terminados = gestor.getProcesosTerminados();
        if (terminados.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No hay procesos terminados todavia.",
                    "Estadisticas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        VentanaEstadisticas ventana = new VentanaEstadisticas(this, terminados);
        ventana.setVisible(true);
    }

    private void refrescarTodo() {
        BCP actual = gestor.getProcesoActual();

        panelProcesos.actualizarLista(
                gestor.getListaProcesos(),
                actual,
                gestor.getProcesosBloqueadosInput(),
                gestor.getListaDeTrabajos());
        panelProcesos.actualizarBCP(actual);

        int ir = (actual != null) ? actual.getIr() : -1;
        panelMemoria.actualizar(memoria, ir);

        panelDisco.actualizar(disco);
    }
}