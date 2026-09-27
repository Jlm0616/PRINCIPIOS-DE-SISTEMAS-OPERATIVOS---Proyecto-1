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
 * Es una fachada que agrupa los paneles de configuracion en pestañas:
 *   - Memoria principal (PanelConfigMemoria)
 *   - Memoria virtual / swap (PanelConfigMemoriaVirtual)
 *   - Disco (PanelConfigDisco)
 *
 * No crea objetos Memoria, MemoriaVirtual ni Disco: solo captura y
 * valida los valores. Quien la usa (VentanaPrincipal) es responsable
 * de aplicarlos.
 */
public class VentanaConfiguracion extends JDialog {

    private PanelConfigMemoria panelMemoria;
    private PanelConfigMemoriaVirtual panelMemoriaVirtual;   // ← CAMBIO
    private PanelConfigDisco panelDisco;
    private JTabbedPane tabs;

    private boolean confirmado;

    private int tamanoMemoria;
    private int limiteKernel;
    private int tamanoMemoriaVirtual;   // ← CAMBIO
    private int tamanoDisco;

    /**
     * Crea la ventana modal.
     *
     * @param propietario               ventana padre
     * @param tamanoMemoriaActual       valor inicial de memoria
     * @param limiteKernelActual        valor inicial del limite kernel
     * @param tamanoMemoriaVirtualActual valor inicial de la memoria virtual
     * @param tamanoDiscoActual         valor inicial del disco
     */
    public VentanaConfiguracion(Frame propietario,
                                 int tamanoMemoriaActual,
                                 int limiteKernelActual,
                                 int tamanoMemoriaVirtualActual,   // ← CAMBIO
                                 int tamanoDiscoActual) {
        super(propietario, "Configuracion", true);
        this.confirmado = false;
        this.tamanoMemoria = tamanoMemoriaActual;
        this.limiteKernel = limiteKernelActual;
        this.tamanoMemoriaVirtual = tamanoMemoriaVirtualActual;   // ← CAMBIO
        this.tamanoDisco = tamanoDiscoActual;

        construirInterfaz(tamanoMemoriaActual, limiteKernelActual,
                          tamanoMemoriaVirtualActual, tamanoDiscoActual);   // ← CAMBIO
        pack();
        setResizable(false);
        setLocationRelativeTo(propietario);
    }

    private void construirInterfaz(int tamanoMemoriaActual,
                                    int limiteKernelActual,
                                    int tamanoMemoriaVirtualActual,   // ← CAMBIO
                                    int tamanoDiscoActual) {
        panelMemoria = new PanelConfigMemoria(tamanoMemoriaActual, limiteKernelActual);
        panelMemoriaVirtual = new PanelConfigMemoriaVirtual(tamanoMemoriaVirtualActual);   // ← CAMBIO
        panelDisco = new PanelConfigDisco(tamanoDiscoActual);

        tabs = new JTabbedPane();
        tabs.addTab("Memoria", panelMemoria);
        tabs.addTab("Memoria Virtual", panelMemoriaVirtual);   // ← CAMBIO
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
        // Validar memoria virtual   // ← CAMBIO
        if (!panelMemoriaVirtual.validar()) {
            tabs.setSelectedComponent(panelMemoriaVirtual);
            return;
        }
        // Validar disco
        if (!panelDisco.validar()) {
            tabs.setSelectedComponent(panelDisco);
            return;
        }

        // Guardar valores
        tamanoMemoria = panelMemoria.getTamanoMemoria();
        limiteKernel = panelMemoria.getLimiteKernel();
        tamanoMemoriaVirtual = panelMemoriaVirtual.getTamanoMemoriaVirtual();   // ← CAMBIO
        tamanoDisco = panelDisco.getTamanoDisco();
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

    public int getTamanoMemoriaVirtual() {   // ← CAMBIO
        return tamanoMemoriaVirtual;
    }

    public int getTamanoDisco() {
        return tamanoDisco;
    }
}