package config;

import modelo.BCP;
import modelo.Memoria;

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
 * IMPORTANTE: el kernel ya NO se configura. Se CALCULA automaticamente
 * segun las 3 zonas fijas (Stallings, seccion 3.3):
 *   - ListaProcesos (5)
 *   - BCPs (maxProcesos × 30)
 *   - TablaMemoria (15)
 */
public class ConfiguracionExterna {

    public static final String ARCHIVO = "config.txt";

    /* ==================== VALORES POR DEFECTO ==================== */

    public static final int MEMORIA_DEFAULT         = 256;
    public static final int DISCO_DEFAULT           = 512;
    public static final int MAX_ARCHIVOS_DEFAULT    = 10;
    public static final int MEMORIA_VIRTUAL_DEFAULT = 64;
    public static final int MAX_PROCESOS_DEFAULT    = 5;

    /* ==================== RESTRICCIONES ==================== */

    public static final int MEMORIA_MAXIMO          = 65536;
    public static final int POSICIONES_POR_INSTRUCCION = 2;

    public static final int DISCO_MINIMO            = 128;
    public static final int DISCO_MAXIMO            = 65536;
    public static final int MAX_ARCHIVOS_MINIMO     = 1;
    public static final int MAX_ARCHIVOS_MAXIMO     = 100;
    public static final int MEMORIA_VIRTUAL_MINIMO  = 16;
    public static final int MEMORIA_VIRTUAL_MAXIMO  = 65536;
    public static final int ESPACIO_ARCHIVOS_MINIMO = 64;

    public static final int MAX_PROCESOS_MINIMO     = 1;
    public static final int MAX_PROCESOS_MAXIMO     = 10;

    /* ==================== VALORES ACTUALES ==================== */

    private int memoria         = MEMORIA_DEFAULT;
    private int disco           = DISCO_DEFAULT;
    private int maxArchivos     = MAX_ARCHIVOS_DEFAULT;
    private int memoriaVirtual  = MEMORIA_VIRTUAL_DEFAULT;
    private int maxProcesos     = MAX_PROCESOS_DEFAULT;

    private boolean huboCorrecciones = false;
    private final StringBuilder mensajesCorrecciones = new StringBuilder();

    /* ==================== CARGA ==================== */

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
            disco          = parsear(props, "disco",          disco);
            maxArchivos    = parsear(props, "max_archivos",   maxArchivos);
            memoriaVirtual = parsear(props, "memoria_virtual", memoriaVirtual);
            maxProcesos    = parsear(props, "max_procesos",   maxProcesos);

            validarYCorregir();

            System.out.println("[CONFIG] Configuracion cargada desde " + ARCHIVO);
            System.out.println("[CONFIG]   Memoria: " + memoria
                    + " (kernel=" + getTamanoKernel() + ")");
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

    private void validarYCorregir() {
        huboCorrecciones = false;
        mensajesCorrecciones.setLength(0);

        // ==== MAX_PROCESOS (primero, porque afecta al tamano del kernel) ====
        if (maxProcesos < MAX_PROCESOS_MINIMO) {
            agregarCorreccion("max_procesos=" + maxProcesos
                    + " invalido (minimo " + MAX_PROCESOS_MINIMO + "). Usando "
                    + MAX_PROCESOS_DEFAULT + ".");
            maxProcesos = MAX_PROCESOS_DEFAULT;
        } else if (maxProcesos > MAX_PROCESOS_MAXIMO) {
            agregarCorreccion("max_procesos=" + maxProcesos
                    + " invalido (maximo " + MAX_PROCESOS_MAXIMO + "). Usando "
                    + MAX_PROCESOS_DEFAULT + ".");
            maxProcesos = MAX_PROCESOS_DEFAULT;
        }

        // ==== MEMORIA (depende del tamano del kernel) ====
        int tamanoBCPs = maxProcesos * BCP.POSICIONES_REQUERIDAS;
        int tamanoKernel = Memoria.TAMANO_LISTA_PROCESOS + tamanoBCPs
                         + Memoria.TAMANO_TABLA_MEMORIA;
        int memoriaMinima = tamanoKernel + POSICIONES_POR_INSTRUCCION;

        if (memoria < memoriaMinima) {
            agregarCorreccion("memoria=" + memoria
                    + " invalido (minimo " + memoriaMinima
                    + " = kernel " + tamanoKernel + " + "
                    + POSICIONES_POR_INSTRUCCION + " de usuario). Usando "
                    + Math.max(memoriaMinima, MEMORIA_DEFAULT) + ".");
            memoria = Math.max(memoriaMinima, MEMORIA_DEFAULT);
        } else if (memoria > MEMORIA_MAXIMO) {
            agregarCorreccion("memoria=" + memoria
                    + " invalido (maximo " + MEMORIA_MAXIMO + "). Usando "
                    + MEMORIA_DEFAULT + ".");
            memoria = MEMORIA_DEFAULT;
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

            espacioOcupado = espacioIndice + memoriaVirtual;
            espacioArchivos = disco - espacioOcupado;
            if (espacioArchivos < ESPACIO_ARCHIVOS_MINIMO) {
                agregarCorreccion("Aun sin espacio. Ajustando max_archivos a "
                        + MAX_ARCHIVOS_DEFAULT + ".");
                maxArchivos = MAX_ARCHIVOS_DEFAULT;
            }
        }

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

    public void guardar() {
        Properties props = new Properties();
        props.setProperty("memoria",         String.valueOf(memoria));
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

    /**
     * @return el tamano del kernel calculado segun las 3 zonas.
     */
    public int getTamanoKernel() {
        int tamanoBCPs = maxProcesos * BCP.POSICIONES_REQUERIDAS;
        return Memoria.TAMANO_LISTA_PROCESOS + tamanoBCPs + Memoria.TAMANO_TABLA_MEMORIA;
    }

    /* ==================== GETTERS ==================== */

    public int getMemoria()        { return memoria; }
    public int getDisco()          { return disco; }
    public int getMaxArchivos()    { return maxArchivos; }
    public int getMemoriaVirtual() { return memoriaVirtual; }
    public int getMaxProcesos()    { return maxProcesos; }

    public boolean huboCorrecciones() { return huboCorrecciones; }
    public String getMensajesCorrecciones() { return mensajesCorrecciones.toString(); }

    /* ==================== SETTERS ==================== */

    public void setMemoria(int v)        { this.memoria = v; }
    public void setDisco(int v)          { this.disco = v; }
    public void setMaxArchivos(int v)    { this.maxArchivos = v; }
    public void setMemoriaVirtual(int v) { this.memoriaVirtual = v; }
    public void setMaxProcesos(int v)    { this.maxProcesos = v; }

    @Override
    public String toString() {
        return "ConfiguracionExterna{"
                + "memoria=" + memoria
                + ", kernel=" + getTamanoKernel()
                + ", disco=" + disco
                + ", maxArchivos=" + maxArchivos
                + ", memoriaVirtual=" + memoriaVirtual
                + ", maxProcesos=" + maxProcesos
                + "}";
    }
}