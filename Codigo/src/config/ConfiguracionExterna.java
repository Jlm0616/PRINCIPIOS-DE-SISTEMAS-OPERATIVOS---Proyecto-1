package config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuracion externa del sistema.
 *
 * Lee y escribe los parametros del sistema desde un archivo de texto
 * (config.txt) con formato "clave=valor".
 *
 * Esto cumple con el requisito del enunciado:
 *   "Esta configuracion tanto para memoria principal como la secundaria
 *    puede ser un archivo de texto, json, xml. No debe de quedar en codigo."
 *
 * Si el archivo no existe al arrancar, se crea con los valores por defecto.
 * Si el archivo existe pero tiene valores invalidos, se corrigen
 * automaticamente usando los valores por defecto y se regraba el archivo.
 */
public class ConfiguracionExterna {

    /** Nombre del archivo de configuracion. */
    public static final String ARCHIVO = "config.txt";

    /* ==================== VALORES POR DEFECTO ==================== */

    public static final int MEMORIA_DEFAULT         = 256;
    public static final int KERNEL_DEFAULT          = 52;
    public static final int DISCO_DEFAULT           = 512;
    public static final int MAX_ARCHIVOS_DEFAULT    = 10;
    public static final int MEMORIA_VIRTUAL_DEFAULT = 64;
    public static final int MAX_PROCESOS_DEFAULT    = 5;

    /* ==================== RESTRICCIONES ==================== */

    public static final int MEMORIA_MINIMO          = 128;
    public static final int MEMORIA_MAXIMO          = 65536;
    public static final double PORCENTAJE_KERNEL    = 0.20;
    public static final int POSICIONES_POR_INSTRUCCION = 2;

    public static final int DISCO_MINIMO            = 128;
    public static final int DISCO_MAXIMO            = 65536;
    public static final int MAX_ARCHIVOS_MINIMO     = 1;
    public static final int MAX_ARCHIVOS_MAXIMO     = 100;
    public static final int MEMORIA_VIRTUAL_MINIMO  = 16;
    public static final int MEMORIA_VIRTUAL_MAXIMO  = 65536;
    public static final int ESPACIO_ARCHIVOS_MINIMO = 64;

    public static final int MAX_PROCESOS_MINIMO     = 1;

    /* ==================== VALORES ACTUALES ==================== */

    private int memoria         = MEMORIA_DEFAULT;
    private int kernel          = KERNEL_DEFAULT;
    private int disco           = DISCO_DEFAULT;
    private int maxArchivos     = MAX_ARCHIVOS_DEFAULT;
    private int memoriaVirtual  = MEMORIA_VIRTUAL_DEFAULT;
    private int maxProcesos     = MAX_PROCESOS_DEFAULT;

    /** Indica si hubo correcciones durante la ultima carga. */
    private boolean huboCorrecciones = false;

    /** Mensajes de correcciones (para mostrar en la GUI). */
    private final StringBuilder mensajesCorrecciones = new StringBuilder();

    /* ==================== CARGA ==================== */

    /**
     * Carga la configuracion desde el archivo.
     *
     * Si el archivo no existe, se crea con los valores por defecto.
     * Si el archivo existe pero tiene errores, se usan los defaults
     * para los campos invalidos.
     */
    public void cargar() {
        File archivo = new File(ARCHIVO);

        if (!archivo.exists()) {
            System.out.println("[CONFIG] " + ARCHIVO
                    + " no existe. Creando con valores por defecto...");
            guardar();
            return;
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(archivo)) {
            props.load(in);

            memoria        = parsear(props, "memoria",        memoria);
            kernel         = parsear(props, "kernel",         kernel);
            disco          = parsear(props, "disco",          disco);
            maxArchivos    = parsear(props, "max_archivos",   maxArchivos);
            memoriaVirtual = parsear(props, "memoria_virtual", memoriaVirtual);
            maxProcesos    = parsear(props, "max_procesos",   maxProcesos);

            // Validar y corregir
            validarYCorregir();

            System.out.println("[CONFIG] Configuracion cargada desde " + ARCHIVO);
            System.out.println("[CONFIG]   Memoria: " + memoria
                    + ", Kernel: " + kernel);
            System.out.println("[CONFIG]   Disco: " + disco
                    + ", MaxArchivos: " + maxArchivos
                    + ", Swap: " + memoriaVirtual);
            System.out.println("[CONFIG]   MaxProcesos: " + maxProcesos);

        } catch (IOException e) {
            System.err.println("[CONFIG] Error leyendo " + ARCHIVO
                    + ": " + e.getMessage());
            System.err.println("[CONFIG] Usando valores por defecto.");
        }
    }

    /* ==================== VALIDACION ==================== */

    /**
     * Valida los valores cargados y corrige los invalidos usando
     * los valores por defecto. Si hubo correcciones, regraba el archivo.
     */
    private void validarYCorregir() {
        huboCorrecciones = false;
        mensajesCorrecciones.setLength(0);

        // ==== MEMORIA ====
        if (memoria < MEMORIA_MINIMO) {
            agregarCorreccion("memoria=" + memoria
                    + " invalido (minimo " + MEMORIA_MINIMO + "). Usando "
                    + MEMORIA_DEFAULT + ".");
            memoria = MEMORIA_DEFAULT;
        } else if (memoria > MEMORIA_MAXIMO) {
            agregarCorreccion("memoria=" + memoria
                    + " invalido (maximo " + MEMORIA_MAXIMO + "). Usando "
                    + MEMORIA_DEFAULT + ".");
            memoria = MEMORIA_DEFAULT;
        }

        // ==== KERNEL ====
        int minimoKernel = (int) Math.ceil(memoria * PORCENTAJE_KERNEL);
        if (kernel >= memoria) {
            agregarCorreccion("kernel=" + kernel
                    + " invalido (debe ser < memoria=" + memoria + "). Usando "
                    + minimoKernel + ".");
            kernel = minimoKernel;
        } else if (kernel < minimoKernel) {
            agregarCorreccion("kernel=" + kernel
                    + " invalido (minimo " + minimoKernel
                    + " = 20% de " + memoria + "). Usando " + minimoKernel + ".");
            kernel = minimoKernel;
        } else if (memoria - kernel < POSICIONES_POR_INSTRUCCION) {
            agregarCorreccion("memoria-kernel=" + (memoria - kernel)
                    + " invalido (minimo " + POSICIONES_POR_INSTRUCCION
                    + "). Ajustando kernel a " + (memoria - POSICIONES_POR_INSTRUCCION) + ".");
            kernel = memoria - POSICIONES_POR_INSTRUCCION;
        }

        // ==== DISCO ====
        if (disco < DISCO_MINIMO) {
            agregarCorreccion("disco=" + disco
                    + " invalido (minimo " + DISCO_MINIMO + "). Usando "
                    + DISCO_DEFAULT + ".");
            disco = DISCO_DEFAULT;
        } else if (disco > DISCO_MAXIMO) {
            agregarCorreccion("disco=" + disco
                    + " invalido (maximo " + DISCO_MAXIMO + "). Usando "
                    + DISCO_DEFAULT + ".");
            disco = DISCO_DEFAULT;
        }

        // ==== MAX_ARCHIVOS ====
        if (maxArchivos < MAX_ARCHIVOS_MINIMO) {
            agregarCorreccion("max_archivos=" + maxArchivos
                    + " invalido (minimo " + MAX_ARCHIVOS_MINIMO + "). Usando "
                    + MAX_ARCHIVOS_DEFAULT + ".");
            maxArchivos = MAX_ARCHIVOS_DEFAULT;
        } else if (maxArchivos > MAX_ARCHIVOS_MAXIMO) {
            agregarCorreccion("max_archivos=" + maxArchivos
                    + " invalido (maximo " + MAX_ARCHIVOS_MAXIMO + "). Usando "
                    + MAX_ARCHIVOS_DEFAULT + ".");
            maxArchivos = MAX_ARCHIVOS_DEFAULT;
        }

        // ==== MEMORIA_VIRTUAL ====
        if (memoriaVirtual < MEMORIA_VIRTUAL_MINIMO) {
            agregarCorreccion("memoria_virtual=" + memoriaVirtual
                    + " invalido (minimo " + MEMORIA_VIRTUAL_MINIMO + "). Usando "
                    + MEMORIA_VIRTUAL_DEFAULT + ".");
            memoriaVirtual = MEMORIA_VIRTUAL_DEFAULT;
        } else if (memoriaVirtual > MEMORIA_VIRTUAL_MAXIMO) {
            agregarCorreccion("memoria_virtual=" + memoriaVirtual
                    + " invalido (maximo " + MEMORIA_VIRTUAL_MAXIMO + "). Usando "
                    + MEMORIA_VIRTUAL_DEFAULT + ".");
            memoriaVirtual = MEMORIA_VIRTUAL_DEFAULT;
        }

        // ==== MAX_PROCESOS ====
        if (maxProcesos < MAX_PROCESOS_MINIMO) {
            agregarCorreccion("max_procesos=" + maxProcesos
                    + " invalido (minimo " + MAX_PROCESOS_MINIMO + "). Usando "
                    + MAX_PROCESOS_DEFAULT + ".");
            maxProcesos = MAX_PROCESOS_DEFAULT;
        }

        // ==== VALIDACION CRUZADA: espacio del disco ====
        int espacioIndice = maxArchivos * 3;
        int espacioOcupado = espacioIndice + memoriaVirtual;
        int espacioArchivos = disco - espacioOcupado;

        if (espacioArchivos < ESPACIO_ARCHIVOS_MINIMO) {
            agregarCorreccion("El disco no tiene espacio suficiente: "
                    + "indice (" + maxArchivos + "x3=" + espacioIndice + ")"
                    + " + swap (" + memoriaVirtual + ")"
                    + " = " + espacioOcupado
                    + ", deja " + espacioArchivos
                    + " para archivos (minimo " + ESPACIO_ARCHIVOS_MINIMO + ")."
                    + " Ajustando memoria_virtual a " + MEMORIA_VIRTUAL_DEFAULT + ".");
            memoriaVirtual = MEMORIA_VIRTUAL_DEFAULT;

            // Re-verificar
            espacioOcupado = espacioIndice + memoriaVirtual;
            espacioArchivos = disco - espacioOcupado;
            if (espacioArchivos < ESPACIO_ARCHIVOS_MINIMO) {
                agregarCorreccion("Aun sin espacio. Ajustando max_archivos a "
                        + MAX_ARCHIVOS_DEFAULT + ".");
                maxArchivos = MAX_ARCHIVOS_DEFAULT;
            }
        }

        // Si hubo correcciones, guardar el archivo
        if (huboCorrecciones) {
            System.err.println("[CONFIG] Se corrigieron valores invalidos:");
            System.err.println(mensajesCorrecciones.toString());
            System.err.println("[CONFIG] Guardando " + ARCHIVO + "...");
            guardar();
        }
    }

    private void agregarCorreccion(String mensaje) {
        huboCorrecciones = true;
        mensajesCorrecciones.append("  - ").append(mensaje).append("\n");
    }

    /* ==================== GUARDADO ==================== */

    /**
     * Guarda la configuracion actual en el archivo.
     */
    public void guardar() {
        Properties props = new Properties();
        props.setProperty("memoria",         String.valueOf(memoria));
        props.setProperty("kernel",          String.valueOf(kernel));
        props.setProperty("disco",           String.valueOf(disco));
        props.setProperty("max_archivos",    String.valueOf(maxArchivos));
        props.setProperty("memoria_virtual", String.valueOf(memoriaVirtual));
        props.setProperty("max_procesos",    String.valueOf(maxProcesos));

        try (FileOutputStream out = new FileOutputStream(ARCHIVO)) {
            props.store(out, "Configuracion del Sistema - Proyecto 1 SO");
            System.out.println("[CONFIG] Configuracion guardada en " + ARCHIVO);
        } catch (IOException e) {
            System.err.println("[CONFIG] Error guardando " + ARCHIVO
                    + ": " + e.getMessage());
        }
    }

    /* ==================== HELPERS ==================== */

    private int parsear(Properties props, String clave, int defaultValor) {
        String valor = props.getProperty(clave);
        if (valor == null) return defaultValor;
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            System.err.println("[CONFIG] Valor invalido para '" + clave
                    + "': " + valor + ". Usando default " + defaultValor);
            return defaultValor;
        }
    }

    /* ==================== GETTERS ==================== */

    public int getMemoria()        { return memoria; }
    public int getKernel()         { return kernel; }
    public int getDisco()          { return disco; }
    public int getMaxArchivos()    { return maxArchivos; }
    public int getMemoriaVirtual() { return memoriaVirtual; }
    public int getMaxProcesos()    { return maxProcesos; }

    /**
     * @return true si hubo correcciones durante la ultima carga.
     */
    public boolean huboCorrecciones() {
        return huboCorrecciones;
    }

    /**
     * @return los mensajes de correcciones de la ultima carga.
     */
    public String getMensajesCorrecciones() {
        return mensajesCorrecciones.toString();
    }

    /* ==================== SETTERS ==================== */

    public void setMemoria(int v)        { this.memoria = v; }
    public void setKernel(int v)         { this.kernel = v; }
    public void setDisco(int v)          { this.disco = v; }
    public void setMaxArchivos(int v)    { this.maxArchivos = v; }
    public void setMemoriaVirtual(int v) { this.memoriaVirtual = v; }
    public void setMaxProcesos(int v)    { this.maxProcesos = v; }

    @Override
    public String toString() {
        return "ConfiguracionExterna{"
                + "memoria=" + memoria
                + ", kernel=" + kernel
                + ", disco=" + disco
                + ", maxArchivos=" + maxArchivos
                + ", memoriaVirtual=" + memoriaVirtual
                + ", maxProcesos=" + maxProcesos
                + "}";
    }
}