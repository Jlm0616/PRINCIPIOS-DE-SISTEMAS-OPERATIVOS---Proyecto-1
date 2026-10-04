package modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Memoria principal de la maquina virtual (Proyecto 1).
 *
 * Se divide en DOS zonas:
 *   - Zona KERNEL: posiciones [0, limiteKernelUsuario)
 *   - Zona USUARIO: posiciones [limiteKernelUsuario, tamanoMemoria)
 *
 * La zona KERNEL contiene 3 SUB-ZONAS FIJAS (Stallings, seccion 3.3):
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  ZONA KERNEL                                         │
 *   ├──────────────────────────────────────────────────────┤
 *   │  ListaProcesos (Ready queue)                         │
 *   │    → Direcciones de BCPs READY (5 posiciones fijas)  │
 *   ├──────────────────────────────────────────────────────┤
 *   │  BCPs                                                │
 *   │    → bcpsQueCaben × 30 posiciones                    │
 *   ├──────────────────────────────────────────────────────┤
 *   │  TablaMemoria                                        │
 *   │    → Bloques asignados (15 posiciones fijas)         │
 *   └──────────────────────────────────────────────────────┘
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  ZONA USUARIO                                        │
 *   │    → Instrucciones de los procesos activos           │
 *   └──────────────────────────────────────────────────────┘
 *
 * IMPORTANTE: el numero de BCPs que caben se calcula a partir
 * del tamano del kernel. Si el kernel no alcanza para maxProcesos,
 * se usan los BCPs que quepan y los procesos sobrantes esperan
 * en la ListaDeTrabajos (disco), segun lo indica el enunciado:
 *
 *   "En el caso de que no exista espacio para almacenar un proceso
 *    en memoria principal, este debe esperar hasta que sea liberado."
 *
 * El limiteKernelUsuario es CONFIGURABLE. Debe ser al menos:
 *   TAMANO_LISTA_PROCESOS + BCP.POSICIONES_REQUERIDAS + TAMANO_TABLA_MEMORIA
 * (es decir, al menos 1 BCP).
 */
public class Memoria {

    /* ==================== TAMANOS FIJOS ==================== */

    public static final int TAMANO_LISTA_PROCESOS = 5;
    public static final int TAMANO_TABLA_MEMORIA   = 15;
    public static final int MAX_BCPS               = 5;

    /** Tamano minimo de la memoria (configurable por el usuario). */
    public static final int TAMANO_MINIMO = 128;

    /* ==================== ZONAS (calculadas) ==================== */

    private final int inicioListaProcesos;
    private final int finListaProcesos;

    private final int inicioBCPs;
    private final int finBCPs;

    private final int inicioTablaMemoria;
    private final int finTablaMemoria;

    private final int limiteKernelUsuario;

    /* ==================== DATOS ==================== */

    private final int tamanoMemoria;

    /** Cantidad de BCPs configurada por el usuario (tope, no garantia). */
    private final int maxProcesos;

    /** Cantidad de BCPs que REALMENTE caben en el kernel. */
    private final int bcpsQueCaben;

    private final Object[] arregloMemoria;

    /* ==================== CONSTRUCTOR ==================== */

    /**
     * Crea una memoria con el tamano y limite kernel indicados.
     *
     * Los BCPs que caben en el kernel se calculan automaticamente segun
     * el limiteKernelUsuario. Si caben menos que maxProcesos, los procesos
     * sobrantes iran a la ListaDeTrabajos (disco) y esperaran a que se
     * libere espacio en RAM. NO se lanza excepcion por este motivo.
     *
     * @param tamanoMemoria       cantidad total de posiciones (>= TAMANO_MINIMO)
     * @param limiteKernelUsuario primera posicion de la zona usuario
     * @param maxProcesos         cantidad maxima deseada de procesos (tope 5)
     * @throws IllegalArgumentException solo si el tamano es invalido o
     *         el kernel no alcanza ni para 1 BCP
     */
    public Memoria(int tamanoMemoria, int limiteKernelUsuario, int maxProcesos) {
        if (tamanoMemoria < TAMANO_MINIMO) {
            throw new IllegalArgumentException(
                "Tamano de memoria menor a " + TAMANO_MINIMO);
        }
        if (maxProcesos < 1) {
            throw new IllegalArgumentException("maxProcesos debe ser al menos 1");
        }
        if (limiteKernelUsuario > tamanoMemoria) {
            throw new IllegalArgumentException(
                "El limite kernel/usuario no puede superar el tamano total");
        }

        // Cuantos BCPs caben en el kernel configurado
        int bcpsPosibles = calcularBCPsQueCaben(limiteKernelUsuario);

        if (bcpsPosibles < 1) {
            throw new IllegalArgumentException(
                "El kernel no alcanza ni para 1 BCP.\n"
                + "  Kernel configurado: " + limiteKernelUsuario + " posiciones.\n"
                + "  Minimo requerido: " + getTamanoKernelMinimo(1) + " posiciones "
                + "(ListaProcesos=" + TAMANO_LISTA_PROCESOS
                + " + 1 BCP=" + BCP.POSICIONES_REQUERIDAS
                + " + TablaMemoria=" + TAMANO_TABLA_MEMORIA + ").");
        }

        this.tamanoMemoria = tamanoMemoria;
        this.limiteKernelUsuario = limiteKernelUsuario;
        this.maxProcesos = maxProcesos;
        this.bcpsQueCaben = Math.min(bcpsPosibles, maxProcesos);
        this.arregloMemoria = new Object[tamanoMemoria];

        // Dimensionar BCPs con bcpsQueCaben (no con maxProcesos)
        int tamanoBCPs = bcpsQueCaben * BCP.POSICIONES_REQUERIDAS;

        // Calcular limites de cada zona
        this.inicioListaProcesos = 0;
        this.finListaProcesos = inicioListaProcesos + TAMANO_LISTA_PROCESOS - 1;

        this.inicioBCPs = finListaProcesos + 1;
        this.finBCPs = inicioBCPs + tamanoBCPs - 1;

        this.inicioTablaMemoria = finBCPs + 1;
        this.finTablaMemoria = inicioTablaMemoria + TAMANO_TABLA_MEMORIA - 1;

        // Verificar que la TablaMemoria no se salga del kernel
        if (finTablaMemoria >= limiteKernelUsuario) {
            throw new IllegalArgumentException(
                "Error interno: la TablaMemoria (hasta posicion " + finTablaMemoria
                + ") no cabe en el kernel (limite " + limiteKernelUsuario + ").");
        }
    }

    /* ==================== CALCULO DE BCPs QUE CABEN ==================== */

    /**
     * Calcula cuantos BCPs caben en un kernel de tamano dado.
     *
     * @param tamanoKernel tamano del kernel configurado
     * @return cantidad de BCPs que caben (0 si no cabe ni 1)
     */
    public static int calcularBCPsQueCaben(int tamanoKernel) {
        int espacioParaBCPs = tamanoKernel - TAMANO_LISTA_PROCESOS - TAMANO_TABLA_MEMORIA;
        if (espacioParaBCPs < BCP.POSICIONES_REQUERIDAS) return 0;
        return Math.min(espacioParaBCPs / BCP.POSICIONES_REQUERIDAS, MAX_BCPS);
    }

    /**
     * @return el tamano minimo del kernel para UNA cantidad dada de BCPs.
     */
    public static int getTamanoKernelMinimo(int maxProcesos) {
        return TAMANO_LISTA_PROCESOS
             + (maxProcesos * BCP.POSICIONES_REQUERIDAS)
             + TAMANO_TABLA_MEMORIA;
    }

    /* ==================== ACCESO GENERICO ==================== */

    public void escribir(int posicion, Object valor) {
        arregloMemoria[posicion] = valor;
    }

    public Object leer(int posicion) {
        return arregloMemoria[posicion];
    }

    public Instruccion leerInstruccion(int posicion) {
        Object valor = arregloMemoria[posicion];
        if (valor == null) return null;
        if (!(valor instanceof Instruccion)) {
            throw new ClassCastException(
                "La posicion " + posicion + " no contiene una Instruccion, sino "
                + valor.getClass().getSimpleName());
        }
        return (Instruccion) valor;
    }

    public BCP leerBCP(int posicion) {
        Object valor = arregloMemoria[posicion];
        if (valor == null) return null;
        if (!(valor instanceof BCP)) {
            throw new ClassCastException(
                "La posicion " + posicion + " no contiene un BCP, sino "
                + valor.getClass().getSimpleName());
        }
        return (BCP) valor;
    }

    /* ==================== ZONA 1: LISTA DE PROCESOS ==================== */

    public int getInicioListaProcesos() { return inicioListaProcesos; }
    public int getFinListaProcesos()    { return finListaProcesos; }
    public int getTamanoListaProcesos() { return TAMANO_LISTA_PROCESOS; }

    public void escribirListaProcesos(int i, Object valor) {
        if (i < 0 || i >= TAMANO_LISTA_PROCESOS) {
            throw new IndexOutOfBoundsException("Indice fuera de ListaProcesos: " + i);
        }
        arregloMemoria[inicioListaProcesos + i] = valor;
    }

    public Object leerListaProcesos(int i) {
        if (i < 0 || i >= TAMANO_LISTA_PROCESOS) {
            throw new IndexOutOfBoundsException("Indice fuera de ListaProcesos: " + i);
        }
        return arregloMemoria[inicioListaProcesos + i];
    }

    /* ==================== ZONA 2: BCPs ==================== */

    public int getInicioBCPs() { return inicioBCPs; }
    public int getFinBCPs()    { return finBCPs; }

    /** @return cantidad de BCPs configurada por el usuario (tope). */
    public int getMaxProcesos() { return maxProcesos; }

    /** @return cantidad de BCPs que REALMENTE caben en el kernel. */
    public int getBcpsQueCaben() { return bcpsQueCaben; }

    public int reservarBloqueBCP() {
        for (int inicio = inicioBCPs;
             inicio + BCP.POSICIONES_REQUERIDAS - 1 <= finBCPs;
             inicio += BCP.POSICIONES_REQUERIDAS) {
            boolean libre = true;
            for (int i = 0; i < BCP.POSICIONES_REQUERIDAS; i++) {
                if (arregloMemoria[inicio + i] != null) {
                    libre = false;
                    break;
                }
            }
            if (libre) return inicio;
        }
        return -1;
    }

    public void liberarBloqueBCP(int direccionBase) {
        for (int i = 0; i < BCP.POSICIONES_REQUERIDAS; i++) {
            arregloMemoria[direccionBase + i] = null;
        }
    }

    /* ==================== ZONA 3: TABLA DE MEMORIA ==================== */

    public int getInicioTablaMemoria() { return inicioTablaMemoria; }
    public int getFinTablaMemoria()    { return finTablaMemoria; }

    public void escribirTablaMemoria(int i, Object valor) {
        if (i < 0 || i >= TAMANO_TABLA_MEMORIA) {
            throw new IndexOutOfBoundsException("Indice fuera de TablaMemoria: " + i);
        }
        arregloMemoria[inicioTablaMemoria + i] = valor;
    }

    public Object leerTablaMemoria(int i) {
        if (i < 0 || i >= TAMANO_TABLA_MEMORIA) {
            throw new IndexOutOfBoundsException("Indice fuera de TablaMemoria: " + i);
        }
        return arregloMemoria[inicioTablaMemoria + i];
    }

    /* ==================== ZONA USUARIO ==================== */

    public int reservarBloqueUsuario(int tamano) {
        if (tamano <= 0) {
            throw new IllegalArgumentException("El tamano del bloque debe ser > 0");
        }
        for (int inicio = limiteKernelUsuario; inicio <= tamanoMemoria - tamano; inicio++) {
            boolean libre = true;
            for (int i = 0; i < tamano; i++) {
                if (arregloMemoria[inicio + i] != null) {
                    libre = false;
                    break;
                }
            }
            if (libre) return inicio;
        }
        return -1;
    }

    public void liberarBloqueUsuario(int direccionBase, int tamano) {
        for (int i = 0; i < tamano; i++) {
            arregloMemoria[direccionBase + i] = null;
        }
    }

    /* ==================== CONSULTAS ==================== */

    public boolean esZonaKernel(int posicion) {
        return posicion < limiteKernelUsuario;
    }

    public boolean cabeProgramaDeUsuario(int cantidadPosiciones) {
        return cantidadPosiciones <= getEspacioUsuarioDisponible();
    }

    public int getTamanoMemoria() { return tamanoMemoria; }
    public int getLimiteKernelUsuario() { return limiteKernelUsuario; }
    public Object[] getArregloMemoria() { return arregloMemoria; }

    public int getEspacioUsuarioDisponible() {
        return tamanoMemoria - limiteKernelUsuario;
    }

    public int getTamanoKernel() {
        return limiteKernelUsuario;
    }

    /** @return cuantos procesos mas pueden entrar a RAM ahora mismo. */
    public int getBCPsLibres() {
        int libres = 0;
        for (int inicio = inicioBCPs;
             inicio + BCP.POSICIONES_REQUERIDAS - 1 <= finBCPs;
             inicio += BCP.POSICIONES_REQUERIDAS) {
            boolean vacio = true;
            for (int i = 0; i < BCP.POSICIONES_REQUERIDAS; i++) {
                if (arregloMemoria[inicio + i] != null) {
                    vacio = false;
                    break;
                }
            }
            if (vacio) libres++;
        }
        return libres;
    }

    @Override
    public String toString() {
        return "Memoria[" + tamanoMemoria + " pos, kernel=0-" + (limiteKernelUsuario - 1)
                + ", usuario=" + limiteKernelUsuario + "-" + (tamanoMemoria - 1)
                + ", BCPs=" + bcpsQueCaben + "/" + maxProcesos + "]";
    }
}