package modelo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Bloque de Control de Proceso (BCP).
 *
 * Es una clase Java normal con atributos privados; la Memoria guardará
 * una REFERENCIA a este objeto en una posición específica, simulando
 * su "dirección" dentro del espacio de direcciones.
 *
 * El BCP persiste TODO el estado del proceso, incluyendo:
 *   - Registros: pc, ir, ac, ax, bx, cx, dx
 *   - Banderas: overflow, banderaIgual (copia de la CPU)
 *   - Pila (máx. 5 elementos, con detección de desbordamiento)
 *   - Planificación: prioridad, base, alcance
 *   - Tiempos: inicio, fin
 *   - Recursos: archivos abiertos
 *   - Enlace al siguiente BCP (lista enlazada)
 *   - Dirección simulada donde vive este BCP
 *
 * La sincronización con la CPU se hace con:
 *   - actualizarDesdeCPU(CPU): copia CPU -> BCP (tras cada instrucción)
 *   - actualizarHaciaCPU(CPU): copia BCP -> CPU (al despachar un proceso)
 */
public class BCP {

    /** Tamaño máximo de la pila de un proceso. */
    public static final int TAMANO_MAXIMO_PILA = 5;

    // --- Identificación y estado ---
    private int id;
    private EstadoProceso estado;

    // --- Registros de CPU ---
    private int pc;
    private int ac;
    private int ax;
    private int bx;
    private int cx;
    private int dx;
    private int ir;

    // --- Banderas (copia del estado de la CPU) ---
    private boolean overflow;      // true si la última operación aritmética desbordó
    private boolean banderaIgual;  // resultado de la última comparación (CMP)

    // --- Pila del proceso ---
    private Stack<Integer> pila;

    // --- Planificación ---
    private int prioridad;
    private int base;
    private int alcance;

    // --- Tiempos ---
    private LocalDateTime tiempoInicio;
    private LocalDateTime tiempoFin;

    // --- Recursos ---
    private List<String> archivosAbiertos;

    // --- CPU donde se ejecuta (-1 = ninguna) ---
    private int cpuAsignado;

    // --- Enlace para lista enlazada de BCPs ---
    private BCP siguienteBCP;

    // --- Dirección simulada donde "vive" el BCP ---
    private int direccion;

    /* ==================== CONSTRUCTOR ==================== */

    /**
     * Crea un BCP en estado NEW, con todos los registros y banderas en 0/false,
     * pila vacía, sin archivos abiertos y sin CPU asignada.
     *
     * @param id        identificador del proceso
     * @param prioridad prioridad del proceso
     * @param base      dirección de inicio del proceso en memoria
     * @param alcance   tamaño del proceso en posiciones
     */
    public BCP(int id, int prioridad, int base, int alcance) {
        this.id = id;
        this.prioridad = prioridad;
        this.base = base;
        this.alcance = alcance;

        this.estado = EstadoProceso.NEW;

        this.pc = 0;
        this.ac = 0;
        this.ax = 0;
        this.bx = 0;
        this.cx = 0;
        this.dx = 0;
        this.ir = 0;

        this.overflow = false;
        this.banderaIgual = false;

        this.pila = new Stack<>();

        this.tiempoInicio = null;
        this.tiempoFin = null;

        this.archivosAbiertos = new ArrayList<>();
        this.cpuAsignado = -1;
        this.siguienteBCP = null;
        this.direccion = -1;
    }

    /* ==================== SINCRONIZACIÓN CON CPU ==================== */

    /**
     * Copia el estado actual de la CPU hacia este BCP.
     * Se llama al final de cada instrucción ejecutada, para persistir
     * el estado del proceso en memoria.
     *
     * @param cpu CPU desde la cual copiar
     */
    public void actualizarDesdeCPU(CPU cpu) {
        this.pc = cpu.getPC();
        this.ir = cpu.getIR();
        this.ac = cpu.getAC();
        this.ax = cpu.getAX();
        this.bx = cpu.getBX();
        this.cx = cpu.getCX();
        this.dx = cpu.getDX();
        this.overflow = cpu.getOverflow();
        this.banderaIgual = cpu.getBanderaIgual();
    }

    /**
     * Copia el estado de este BCP hacia la CPU.
     * Se llama antes de reanudar un proceso (despacho).
     *
     * @param cpu CPU hacia la cual copiar
     */
    public void actualizarHaciaCPU(CPU cpu) {
        cpu.setPC(this.pc);
        cpu.setIR(this.ir);
        cpu.setAC(this.ac);
        cpu.setAX(this.ax);
        cpu.setBX(this.bx);
        cpu.setCX(this.cx);
        cpu.setDX(this.dx);
        cpu.setOverflow(this.overflow);
        cpu.setBanderaIgual(this.banderaIgual);
    }

    /* ==================== PILA ==================== */

    /**
     * Apila un valor verificando que no se desborde la pila.
     *
     * @param valor valor a apilar
     * @throws IllegalStateException si la pila ya está llena
     */
    public void apilar(int valor) {
        if (pila.size() >= TAMANO_MAXIMO_PILA) {
            throw new IllegalStateException(
                "Desbordamiento de pila en el proceso " + id +
                " (máximo " + TAMANO_MAXIMO_PILA + " elementos)");
        }
        pila.push(valor);
    }

    /**
     * Desapila el tope de la pila.
     *
     * @return el valor desapilado
     * @throws IllegalStateException si la pila está vacía
     */
    public int desapilar() {
        if (pila.isEmpty()) {
            throw new IllegalStateException(
                "Subdesbordamiento de pila en el proceso " + id + " (pila vacía)");
        }
        return pila.pop();
    }

    /* ==================== TIEMPOS ==================== */

    /** Marca el inicio de ejecución (idempotente). */
    public void marcarInicio() {
        if (this.tiempoInicio == null) {
            this.tiempoInicio = LocalDateTime.now();
        }
    }

    /** Marca el fin de ejecución. */
    public void marcarFin() {
        this.tiempoFin = LocalDateTime.now();
    }

    /**
     * @return duración en segundos, o -1 si aún no terminó.
     */
    public long getDuracionSegundos() {
        if (tiempoInicio == null || tiempoFin == null) {
            return -1;
        }
        return Duration.between(tiempoInicio, tiempoFin).getSeconds();
    }

    /* ==================== GETTERS ==================== */

    public int getId() {
        return id;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public int getPc() {
        return pc;
    }

    public int getAc() {
        return ac;
    }

    public int getAx() {
        return ax;
    }

    public int getBx() {
        return bx;
    }

    public int getCx() {
        return cx;
    }

    public int getDx() {
        return dx;
    }

    public int getIr() {
        return ir;
    }

    public boolean getOverflow() {
        return overflow;
    }

    public boolean getBanderaIgual() {
        return banderaIgual;
    }

    public Stack<Integer> getPila() {
        return pila;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public int getBase() {
        return base;
    }

    public int getAlcance() {
        return alcance;
    }

    public LocalDateTime getTiempoInicio() {
        return tiempoInicio;
    }

    public LocalDateTime getTiempoFin() {
        return tiempoFin;
    }

    public List<String> getArchivosAbiertos() {
        return archivosAbiertos;
    }

    public int getCpuAsignado() {
        return cpuAsignado;
    }

    public BCP getSiguienteBCP() {
        return siguienteBCP;
    }

    public int getDireccion() {
        return direccion;
    }

    /* ==================== SETTERS ==================== */

    public void setId(int id) {
        this.id = id;
    }

    public void setEstado(EstadoProceso estado) {
        this.estado = estado;
    }

    public void setPc(int pc) {
        this.pc = pc;
    }

    public void setAc(int ac) {
        this.ac = ac;
    }

    public void setAx(int ax) {
        this.ax = ax;
    }

    public void setBx(int bx) {
        this.bx = bx;
    }

    public void setCx(int cx) {
        this.cx = cx;
    }

    public void setDx(int dx) {
        this.dx = dx;
    }

    public void setIr(int ir) {
        this.ir = ir;
    }

    public void setOverflow(boolean overflow) {
        this.overflow = overflow;
    }

    public void setBanderaIgual(boolean banderaIgual) {
        this.banderaIgual = banderaIgual;
    }

    public void setPila(Stack<Integer> pila) {
        this.pila = pila;
    }

    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad;
    }

    public void setBase(int base) {
        this.base = base;
    }

    public void setAlcance(int alcance) {
        this.alcance = alcance;
    }

    public void setTiempoInicio(LocalDateTime tiempoInicio) {
        this.tiempoInicio = tiempoInicio;
    }

    public void setTiempoFin(LocalDateTime tiempoFin) {
        this.tiempoFin = tiempoFin;
    }

    public void setArchivosAbiertos(List<String> archivosAbiertos) {
        this.archivosAbiertos = archivosAbiertos;
    }

    public void setCpuAsignado(int cpuAsignado) {
        this.cpuAsignado = cpuAsignado;
    }

    public void setSiguienteBCP(BCP siguienteBCP) {
        this.siguienteBCP = siguienteBCP;
    }

    public void setDireccion(int direccion) {
        this.direccion = direccion;
    }
}