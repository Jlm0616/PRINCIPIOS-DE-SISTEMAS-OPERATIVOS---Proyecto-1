package modelo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Bloque de Control de Proceso (BCP).
 *
 * El BCP NO guarda datos en si mismo. Es una VISTA sobre las
 * POSICIONES_REQUERIDAS posiciones consecutivas de la zona kernel
 * de la Memoria, donde vive cada uno de sus campos.
 *
 * Offsets (posiciones relativas a la direccion base del BCP):
 *   0  = ID
 *   1  = Estado
 *   2  = Prioridad
 *   3  = PC
 *   4  = IR
 *   5  = AC
 *   6  = AX
 *   7  = BX
 *   8  = CX
 *   9  = DX
 *   10 = Overflow
 *   11 = BanderaIgual
 *   12 = PesoPendiente
 *   13 = Pila
 *   14 = Base
 *   15 = Alcance
 *   16 = TiempoInicio
 *   17 = TiempoFin
 *   18 = CPU Asignado
 *   19 = ArchivosAbiertos
 *   20 = Siguiente BCP (direccion de memoria)
 *   21 = Direccion (donde vive este BCP)
 */
public class BCP {

    public static final int TAMANO_MAXIMO_PILA = 5;
    public static final int POSICIONES_REQUERIDAS = 22;

    private static final int OFF_ID             = 0;
    private static final int OFF_ESTADO         = 1;
    private static final int OFF_PRIORIDAD      = 2;
    private static final int OFF_PC             = 3;
    private static final int OFF_IR             = 4;
    private static final int OFF_AC             = 5;
    private static final int OFF_AX             = 6;
    private static final int OFF_BX             = 7;
    private static final int OFF_CX             = 8;
    private static final int OFF_DX             = 9;
    private static final int OFF_OVERFLOW       = 10;
    private static final int OFF_BANDERA_IGUAL  = 11;
    private static final int OFF_PESO_PENDIENTE = 12;
    private static final int OFF_PILA           = 13;
    private static final int OFF_BASE           = 14;
    private static final int OFF_ALCANCE        = 15;
    private static final int OFF_TIEMPO_INICIO  = 16;
    private static final int OFF_TIEMPO_FIN     = 17;
    private static final int OFF_CPU_ASIGNADO   = 18;
    private static final int OFF_ARCHIVOS       = 19;
    private static final int OFF_SIGUIENTE_BCP  = 20;
    private static final int OFF_DIRECCION      = 21;

    private final Memoria memoria;
    private final int direccionBase;

    /**
     * Crea un BCP que ocupa POSICIONES_REQUERIDAS posiciones consecutivas
     * del kernel a partir de direccionBase.
     */
    public BCP(Memoria memoria, int direccionBase, int id, int prioridad) {
        this.memoria = memoria;
        this.direccionBase = direccionBase;

        setId(id);
        setEstado(EstadoProceso.NEW);
        setPrioridad(prioridad);
        setPc(0);
        setIr(0);
        setAc(0);
        setAx(0);
        setBx(0);
        setCx(0);
        setDx(0);
        setOverflow(false);
        setBanderaIgual(false);
        setPesoPendiente(0);
        setPila(new Stack<>());
        setBase(0);
        setAlcance(0);
        setTiempoInicio(null);
        setTiempoFin(null);
        setCpuAsignado(-1);
        setArchivosAbiertos(new ArrayList<>());
        setSiguienteBCP(-1);
        setDireccion(direccionBase);
    }

    /* ============ ACCESO A MEMORIA ============ */

    private Object leer(int offset) {
        return memoria.leer(direccionBase + offset);
    }

    private void escribir(int offset, Object valor) {
        memoria.escribir(direccionBase + offset, valor);
    }

    /* ============ SINCRONIZACIÓN CON CPU ============ */

    public void actualizarDesdeCPU(CPU cpu) {
        setPc(cpu.getPC());
        setIr(cpu.getIR());
        setAc(cpu.getAC());
        setAx(cpu.getAX());
        setBx(cpu.getBX());
        setCx(cpu.getCX());
        setDx(cpu.getDX());
        setOverflow(cpu.getOverflow());
        setBanderaIgual(cpu.getBanderaIgual());
    }

    public void actualizarHaciaCPU(CPU cpu) {
        cpu.setPC(getPc());
        cpu.setIR(getIr());
        cpu.setAC(getAc());
        cpu.setAX(getAx());
        cpu.setBX(getBx());
        cpu.setCX(getCx());
        cpu.setDX(getDx());
        cpu.setOverflow(getOverflow());
        cpu.setBanderaIgual(getBanderaIgual());
    }

    /* ============ PILA ============ */

    public void apilar(int valor) {
        Stack<Integer> pila = getPila();
        if (pila.size() >= TAMANO_MAXIMO_PILA) {
            throw new IllegalStateException(
                "Desbordamiento de pila en el proceso " + getId()
                + " (máximo " + TAMANO_MAXIMO_PILA + " elementos)");
        }
        pila.push(valor);
    }

    public int desapilar() {
        Stack<Integer> pila = getPila();
        if (pila.isEmpty()) {
            throw new IllegalStateException(
                "Subdesbordamiento de pila en el proceso " + getId() + " (pila vacía)");
        }
        return pila.pop();
    }

    /* ============ TIEMPOS ============ */

    public void marcarInicio() {
        if (getTiempoInicio() == null) {
            setTiempoInicio(LocalDateTime.now());
        }
    }

    public void marcarFin() {
        setTiempoFin(LocalDateTime.now());
    }

    public long getDuracionSegundos() {
        LocalDateTime inicio = getTiempoInicio();
        LocalDateTime fin = getTiempoFin();
        if (inicio == null || fin == null) return -1;
        return Duration.between(inicio, fin).getSeconds();
    }

    /* ============ GETTERS ============ */

    public Memoria getMemoria() { return memoria; }
    public int getDireccionBase() { return direccionBase; }

    public int getId() { return (Integer) leer(OFF_ID); }
    public EstadoProceso getEstado() { return (EstadoProceso) leer(OFF_ESTADO); }
    public int getPrioridad() { return (Integer) leer(OFF_PRIORIDAD); }
    public int getPc() { return (Integer) leer(OFF_PC); }
    public int getIr() { return (Integer) leer(OFF_IR); }
    public int getAc() { return (Integer) leer(OFF_AC); }
    public int getAx() { return (Integer) leer(OFF_AX); }
    public int getBx() { return (Integer) leer(OFF_BX); }
    public int getCx() { return (Integer) leer(OFF_CX); }
    public int getDx() { return (Integer) leer(OFF_DX); }

    public boolean getOverflow() {
        Boolean v = (Boolean) leer(OFF_OVERFLOW);
        return v != null && v;
    }

    public boolean getBanderaIgual() {
        Boolean v = (Boolean) leer(OFF_BANDERA_IGUAL);
        return v != null && v;
    }

    public int getPesoPendiente() { return (Integer) leer(OFF_PESO_PENDIENTE); }

    @SuppressWarnings("unchecked")
    public Stack<Integer> getPila() { return (Stack<Integer>) leer(OFF_PILA); }

    public int getBase() { return (Integer) leer(OFF_BASE); }
    public int getAlcance() { return (Integer) leer(OFF_ALCANCE); }
    public LocalDateTime getTiempoInicio() { return (LocalDateTime) leer(OFF_TIEMPO_INICIO); }
    public LocalDateTime getTiempoFin() { return (LocalDateTime) leer(OFF_TIEMPO_FIN); }
    public int getCpuAsignado() { return (Integer) leer(OFF_CPU_ASIGNADO); }

    @SuppressWarnings("unchecked")
    public List<String> getArchivosAbiertos() { return (List<String>) leer(OFF_ARCHIVOS); }

    public int getSiguienteBCP() { return (Integer) leer(OFF_SIGUIENTE_BCP); }
    public int getDireccion() { return (Integer) leer(OFF_DIRECCION); }

    /* ============ SETTERS ============ */

    public void setId(int id) { escribir(OFF_ID, id); }
    public void setEstado(EstadoProceso estado) { escribir(OFF_ESTADO, estado); }
    public void setPrioridad(int prioridad) { escribir(OFF_PRIORIDAD, prioridad); }
    public void setPc(int pc) { escribir(OFF_PC, pc); }
    public void setIr(int ir) { escribir(OFF_IR, ir); }
    public void setAc(int ac) { escribir(OFF_AC, ac); }
    public void setAx(int ax) { escribir(OFF_AX, ax); }
    public void setBx(int bx) { escribir(OFF_BX, bx); }
    public void setCx(int cx) { escribir(OFF_CX, cx); }
    public void setDx(int dx) { escribir(OFF_DX, dx); }
    public void setOverflow(boolean overflow) { escribir(OFF_OVERFLOW, overflow); }
    public void setBanderaIgual(boolean banderaIgual) { escribir(OFF_BANDERA_IGUAL, banderaIgual); }
    public void setPesoPendiente(int pesoPendiente) { escribir(OFF_PESO_PENDIENTE, pesoPendiente); }
    public void setPila(Stack<Integer> pila) { escribir(OFF_PILA, pila); }
    public void setBase(int base) { escribir(OFF_BASE, base); }
    public void setAlcance(int alcance) { escribir(OFF_ALCANCE, alcance); }
    public void setTiempoInicio(LocalDateTime tiempoInicio) { escribir(OFF_TIEMPO_INICIO, tiempoInicio); }
    public void setTiempoFin(LocalDateTime tiempoFin) { escribir(OFF_TIEMPO_FIN, tiempoFin); }
    public void setCpuAsignado(int cpuAsignado) { escribir(OFF_CPU_ASIGNADO, cpuAsignado); }
    public void setArchivosAbiertos(List<String> archivosAbiertos) { escribir(OFF_ARCHIVOS, archivosAbiertos); }
    public void setSiguienteBCP(int siguienteBCP) { escribir(OFF_SIGUIENTE_BCP, siguienteBCP); }
    public void setDireccion(int direccion) { escribir(OFF_DIRECCION, direccion); }

    /* ============ toString ============ */

    @Override
    public String toString() {
        return "BCP[id=" + getId() + ", estado=" + getEstado()
                + ", base=" + getBase() + ", alcance=" + getAlcance() + "]";
    }
}