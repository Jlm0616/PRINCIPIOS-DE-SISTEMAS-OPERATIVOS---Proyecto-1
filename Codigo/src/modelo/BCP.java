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
 *
 * AH y AL no son campos propios: son las dos mitades de AX (como en
 * la arquitectura x86). Se acceden mediante getAh/setAh/getAl/setAl.
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

    /* ============ EXTRACCIÓN / RESTAURACIÓN (para swap) ============ */

    /**
     * Extrae los 22 valores del BCP como un arreglo de Object.
     *
     * Se usa al suspender un proceso: los valores se guardan en el
     * ProcesoEnEspera (dentro del swap), y el bloque del BCP se libera
     * del kernel. Esto respeta el modelo de Stallings (sección 3.3):
     * el BCP es parte del process image y se mueve COMPLETO a swap.
     *
     * El orden del arreglo corresponde a los offsets:
     *   [0]=ID, [1]=Estado, [2]=Prioridad, ..., [21]=Dirección.
     *
     * @return Object[] con los 22 valores del BCP
     */
    public Object[] extraerValores() {
        Object[] valores = new Object[POSICIONES_REQUERIDAS];
        for (int i = 0; i < POSICIONES_REQUERIDAS; i++) {
            valores[i] = leer(i);
        }
        return valores;
    }

    /**
     * Restaura los 22 valores del BCP desde un arreglo de Object.
     *
     * Se usa al reactivar un proceso suspendido: se reserva un bloque
     * nuevo en kernel y se escriben en él los valores guardados en el
     * swap.
     *
     * @param valores Object[] con exactamente 22 valores
     * @throws IllegalArgumentException si el arreglo es null o no tiene
     *         POSICIONES_REQUERIDAS elementos
     */
    public void restaurarValores(Object[] valores) {
        if (valores == null || valores.length != POSICIONES_REQUERIDAS) {
            throw new IllegalArgumentException(
                "El arreglo debe tener exactamente " + POSICIONES_REQUERIDAS + " valores");
        }
        for (int i = 0; i < POSICIONES_REQUERIDAS; i++) {
            escribir(i, valores[i]);
        }
    }

    /**
     * Crea un arreglo con los valores iniciales de un BCP que se
     * suspende directamente (sin haber estado nunca en kernel).
     *
     * Se usa cuando un proceso recién cargado no cabe en memoria
     * principal y se manda directamente al swap en estado READY_SUSPEND.
     *
     * @param id        identificador del proceso
     * @param prioridad prioridad del proceso
     * @param alcance   cantidad de instrucciones del proceso
     * @return Object[] con 22 valores iniciales
     */
    public static Object[] valoresIniciales(int id, int prioridad, int alcance) {
        Object[] valores = new Object[POSICIONES_REQUERIDAS];
        valores[OFF_ID]             = id;
        valores[OFF_ESTADO]         = EstadoProceso.READY_SUSPEND;
        valores[OFF_PRIORIDAD]      = prioridad;
        valores[OFF_PC]             = 0;
        valores[OFF_IR]             = 0;
        valores[OFF_AC]             = 0;
        valores[OFF_AX]             = 0;
        valores[OFF_BX]             = 0;
        valores[OFF_CX]             = 0;
        valores[OFF_DX]             = 0;
        valores[OFF_OVERFLOW]       = false;
        valores[OFF_BANDERA_IGUAL]  = false;
        valores[OFF_PESO_PENDIENTE] = 0;
        valores[OFF_PILA]           = new Stack<Integer>();
        valores[OFF_BASE]           = 0;
        valores[OFF_ALCANCE]        = alcance;
        valores[OFF_TIEMPO_INICIO]  = null;
        valores[OFF_TIEMPO_FIN]     = null;
        valores[OFF_CPU_ASIGNADO]   = -1;
        valores[OFF_ARCHIVOS]       = new ArrayList<String>();
        valores[OFF_SIGUIENTE_BCP]  = -1;
        valores[OFF_DIRECCION]      = -1;
        return valores;
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

    /* ============ AH / AL (partes de AX) ============ */

    /**
     * Obtiene AH: byte alto de AX (bits 15-8).
     * Equivalente al registro AH de x86.
     *
     * @return valor de AH (0-255)
     */
    public int getAh() {
        return (getAx() >> 8) & 0xFF;
    }

    /**
     * Establece AH: modifica solo los 8 bits altos de AX.
     * AL queda intacto.
     *
     * @param ah nuevo valor de AH (0-255)
     */
    public void setAh(int ah) {
        int al = getAl();
        setAx(((ah & 0xFF) << 8) | al);
    }

    /**
     * Obtiene AL: byte bajo de AX (bits 7-0).
     * Equivalente al registro AL de x86.
     *
     * @return valor de AL (0-255)
     */
    public int getAl() {
        return getAx() & 0xFF;
    }

    /**
     * Establece AL: modifica solo los 8 bits bajos de AX.
     * AH queda intacto.
     *
     * @param al nuevo valor de AL (0-255)
     */
    public void setAl(int al) {
        int ah = getAh();
        setAx((ah << 8) | (al & 0xFF));
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