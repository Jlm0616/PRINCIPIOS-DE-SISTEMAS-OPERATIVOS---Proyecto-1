package gui;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JButton;
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
     */
    public VentanaConfiguracion(Frame propietario,
                                 int tamanoMemoriaActual,
                                 int limiteKernelActual,
                                 int tamanoDiscoActual,
                                 int maxArchivosActual,
                                 int tamanoSwapActual) {
        super(propietario, "Configuracion", true);
        this.confirmado = false;
        this.tamanoMemoria = tamanoMemoriaActual;
        this.limiteKernel = limiteKernelActual;
        this.tamanoDisco = tamanoDiscoActual;
        this.maxArchivos = maxArchivosActual;
        this.tamanoSwap = tamanoSwapActual;

        construirInterfaz(tamanoMemoriaActual, limiteKernelActual,
                          tamanoDiscoActual, maxArchivosActual, tamanoSwapActual);
        pack();
        setResizable(false);
        setLocationRelativeTo(propietario);
    }

    private void construirInterfaz(int tamanoMemoriaActual,
                                    int limiteKernelActual,
                                    int tamanoDiscoActual,
                                    int maxArchivosActual,
                                    int tamanoSwapActual) {
        panelMemoria = new PanelConfigMemoria(tamanoMemoriaActual, limiteKernelActual);
        panelDisco = new PanelConfigDisco(tamanoDiscoActual, maxArchivosActual, tamanoSwapActual);

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
        // Validar memoria
        if (!panelMemoria.validar()) {
            tabs.setSelectedComponent(panelMemoria);
            return;
        }
        // Validar disco (incluye maxArchivos y swap)
        if (!panelDisco.validar()) {
            tabs.setSelectedComponent(panelDisco);
            return;
        }

        // Guardar valores
        tamanoMemoria = panelMemoria.getTamanoMemoria();
        limiteKernel = panelMemoria.getLimiteKernel();
        tamanoDisco = panelDisco.getTamanoDisco();
        maxArchivos = panelDisco.getMaxArchivos();
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