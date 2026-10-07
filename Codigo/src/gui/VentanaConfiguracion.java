package gui;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;

/**
 * Ventana modal de configuracion del sistema.
 *
 * Agrupa los paneles de configuracion en 2 pestañas:
 *   - Memoria principal (PanelConfigMemoria)
 *   - Disco (PanelConfigDisco: tamaño, maxArchivos, swap)
 *
 * No crea objetos Memoria ni Disco: solo captura y valida los valores.
 * Quien la usa (VentanaPrincipal) es responsable de aplicarlos.
 *
 * IMPORTANTE: maxArchivos se pasa a PanelConfigMemoria porque el kernel
 * ahora reserva espacio para la ListaDeTrabajos (maxArchivos × 4).
 */
public class VentanaConfiguracion extends JDialog {

    private PanelConfigMemoria panelMemoria;
    private PanelConfigDisco panelDisco;
    private JTabbedPane tabs;

    private boolean confirmado;

    private int tamanoMemoria;
    private int limiteKernel;
    private int tamanoDisco;
    private int maxArchivos;
    private int tamanoSwap;

    /**
     * Crea la ventana modal.
     *
     * @param propietario          ventana padre
     * @param tamanoMemoriaActual  valor inicial de memoria
     * @param limiteKernelActual   valor inicial del limite kernel
     * @param tamanoDiscoActual    valor inicial del disco
     * @param maxArchivosActual    valor inicial de maxArchivos
     * @param tamanoSwapActual     valor inicial de la memoria virtual
     * @param maxProcesosActual    cantidad de procesos configurada
     */
    public VentanaConfiguracion(Frame propietario,
                                 int tamanoMemoriaActual,
                                 int limiteKernelActual,
                                 int tamanoDiscoActual,
                                 int maxArchivosActual,
                                 int tamanoSwapActual,
                                 int maxProcesosActual) {
        super(propietario, "Configuracion", true);
        this.confirmado = false;
        this.tamanoMemoria = tamanoMemoriaActual;
        this.limiteKernel = limiteKernelActual;
        this.tamanoDisco = tamanoDiscoActual;
        this.maxArchivos = maxArchivosActual;
        this.tamanoSwap = tamanoSwapActual;

        construirInterfaz(tamanoMemoriaActual, limiteKernelActual,
                          tamanoDiscoActual, maxArchivosActual,
                          tamanoSwapActual, maxProcesosActual);
        pack();
        setResizable(false);
        setLocationRelativeTo(propietario);
    }

    private void construirInterfaz(int tamanoMemoriaActual,
                                    int limiteKernelActual,
                                    int tamanoDiscoActual,
                                    int maxArchivosActual,
                                    int tamanoSwapActual,
                                    int maxProcesosActual) {
        // PanelConfigMemoria ahora recibe tambien maxArchivos
        panelMemoria = new PanelConfigMemoria(
                tamanoMemoriaActual, limiteKernelActual,
                maxProcesosActual, maxArchivosActual);

        panelDisco = new PanelConfigDisco(
                tamanoDiscoActual, maxArchivosActual, tamanoSwapActual);

        tabs = new JTabbedPane();
        tabs.addTab("Memoria", panelMemoria);
        tabs.addTab("Disco", panelDisco);

        JButton btnAceptar = new JButton("Aceptar");
        JButton btnCancelar = new JButton("Cancelar");
        btnAceptar.addActionListener(e -> validarYConfirmar());
        btnCancelar.addActionListener(e -> {
            confirmado = false;
            dispose();
        });

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        panelBotones.setBorder(new EmptyBorder(0, 20, 15, 20));
        panelBotones.add(btnCancelar);
        panelBotones.add(btnAceptar);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(panelBotones, BorderLayout.SOUTH);
    }

    private void validarYConfirmar() {
        // Validar memoria (30% + minimo para 1 BCP + ListaDeTrabajos)
        if (!panelMemoria.validar()) {
            tabs.setSelectedComponent(panelMemoria);
            return;
        }
        // Validar disco (maxArchivos y swap)
        if (!panelDisco.validar()) {
            tabs.setSelectedComponent(panelDisco);
            return;
        }

        // === VALIDACION CRUZADA: kernel vs maxArchivos ===
        int limiteKernel = panelMemoria.getLimiteKernel();
        int maxArchivos = panelDisco.getMaxArchivos();
        int kernelMinimo = modelo.Memoria.getTamanoKernelMinimo(1, maxArchivos);

        if (limiteKernel < kernelMinimo) {
            // Calcular maxArchivos maximo que cabe con este kernel
            int maxArchivosPosibles = modelo.Memoria.calcularMaxArchivosQueCaben(limiteKernel);

            JOptionPane.showMessageDialog(this,
                    "Configuracion invalida:\n\n"
                    + "  Pestana 'Memoria': limite del kernel = " + limiteKernel + "\n"
                    + "  Pestana 'Disco':   max_archivos      = " + maxArchivos + "\n\n"
                    + "El kernel (" + limiteKernel + ") no alcanza para "
                    + maxArchivos + " archivos + 1 proceso.\n"
                    + "Minimo requerido: " + kernelMinimo + " posiciones.\n\n"
                    + "Opciones:\n"
                    + "  1. Aumenta el limite del kernel (pestana 'Memoria')\n"
                    + "  2. Reduce max_archivos a " + maxArchivosPosibles
                    + " o menos (pestana 'Disco')\n\n"
                    + "Se mantiene la configuracion anterior.",
                    "Datos invalidos",
                    JOptionPane.ERROR_MESSAGE);

            // Llevar al usuario a la pestana Disco (donde esta maxArchivos)
            tabs.setSelectedComponent(panelDisco);
            return;
        }

        // Guardar valores
        tamanoMemoria = panelMemoria.getTamanoMemoria();
        this.limiteKernel = limiteKernel;
        tamanoDisco = panelDisco.getTamanoDisco();
        this.maxArchivos = maxArchivos;
        tamanoSwap = panelDisco.getTamanoSwap();
        confirmado = true;
        dispose();
    }

    public boolean isConfirmado() {
        return confirmado;
    }

    public int getTamanoMemoria() {
        return tamanoMemoria;
    }

    public int getLimiteKernel() {
        return limiteKernel;
    }

    public int getTamanoDisco() {
        return tamanoDisco;
    }

    public int getMaxArchivos() {
        return maxArchivos;
    }

    public int getTamanoSwap() {
        return tamanoSwap;
    }
}