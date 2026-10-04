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
 *   │    → Direcciones de BCPs READY (5 posiciones)        │
 *   ├──────────────────────────────────────────────────────┤
 *   │  BCPs                                                │
 *   │    → maxProcesos × 30 posiciones                     │
 *   ├──────────────────────────────────────────────────────┤
 *   │  TablaMemoria                                        │
 *   │    → Bloques asignados (15 posiciones = 5 bloques)   │
 *   └──────────────────────────────────────────────────────┘
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  ZONA USUARIO                                        │
 *   │    → Instrucciones de los procesos activos           │
 *   └──────────────────────────────────────────────────────┘
 *
 * El limiteKernelUsuario se CALCULA automaticamente segun las 3 zonas.
 */
public class Memoria {

    /* ==================== TAMANOS FIJOS ==================== */

    public static final int TAMANO_LISTA_PROCESOS = 5;
    public static final int TAMANO_TABLA_MEMORIA   = 15;
    public static final int MAX_BCPS               = 5;

    /** Tamano minimo: 3 zonas + al menos 2 posiciones de usuario. */
    public static final int TAMANO_MINIMO =
            TAMANO_LISTA_PROCESOS + (MAX_BCPS * BCP.POSICIONES_REQUERIDAS)
            + TAMANO_TABLA_MEMORIA + 2;

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
    private final int maxProcesos;
    private final Object[] arregloMemoria;

    /* ==================== CONSTRUCTOR ==================== */

    public Memoria(int tamanoMemoria, int maxProcesos) {
        if (maxProcesos < 1) {
            throw new IllegalArgumentException("maxProcesos debe ser al menos 1");
        }

        int tamanoBCPs = maxProcesos * BCP.POSICIONES_REQUERIDAS;
        int tamanoKernel = TAMANO_LISTA_PROCESOS + tamanoBCPs + TAMANO_TABLA_MEMORIA;

        if (tamanoMemoria < tamanoKernel + 2) {
            throw new IllegalArgumentException(
                "Memoria muy pequena: kernel necesita " + tamanoKernel
                + " posiciones + al menos 2 de usuario. Total minimo: "
                + (tamanoKernel + 2));
        }

        this.tamanoMemoria = tamanoMemoria;
        this.maxProcesos = maxProcesos;
        this.arregloMemoria = new Object[tamanoMemoria];

        // Calcular limites de cada zona
        this.inicioListaProcesos = 0;
        this.finListaProcesos = inicioListaProcesos + TAMANO_LISTA_PROCESOS - 1;

        this.inicioBCPs = finListaProcesos + 1;
        this.finBCPs = inicioBCPs + tamanoBCPs - 1;

        this.inicioTablaMemoria = finBCPs + 1;
        this.finTablaMemoria = inicioTablaMemoria + TAMANO_TABLA_MEMORIA - 1;

        this.limiteKernelUsuario = finTablaMemoria + 1;
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
    public int getMaxProcesos() { return maxProcesos; }

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

    @Override
    public String toString() {
        return "Memoria[" + tamanoMemoria + " pos, kernel=0-" + (limiteKernelUsuario - 1)
                + ", usuario=" + limiteKernelUsuario + "-" + (tamanoMemoria - 1) + "]";
    }
}