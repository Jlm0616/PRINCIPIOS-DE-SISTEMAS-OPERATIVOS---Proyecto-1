package logica;

import modelo.BCP;
import modelo.CPU;
import modelo.EstadoProceso;
import modelo.Instruccion;
import modelo.Memoria;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Fachada de orquestación del ciclo de vida y ejecución de procesos.
 *
 * ... (Javadoc igual al tuyo) ...
 */
public class GestorProcesos {

    /** Máximo de procesos permitidos en el sistema (restricción del enunciado). */
    public static final int MAX_PROCESOS = 5;

    private Memoria memoria;
    private CPU cpu;
    private ListaDeTrabajos listaDeTrabajos;
    private Planificador planificador;
    private Despachador despachador;
    private ParticionadorFijo particionador;

    private int siguienteId;
    private List<BCPTerminado> procesosTerminados;
    private List<BCP> procesosBloqueados;
    private List<ProcesoEnEspera> procesosEnEspera;

    public GestorProcesos(Memoria memoria, CPU cpu, ListaDeTrabajos listaDeTrabajos,
                          ParticionadorFijo particionador) {
        this.memoria = memoria;
        this.cpu = cpu;
        this.listaDeTrabajos = listaDeTrabajos;
        this.particionador = particionador;
        this.planificador = new Planificador(listaDeTrabajos);
        this.despachador = new Despachador(cpu);
        this.siguienteId = 1;
        this.procesosTerminados = new ArrayList<>();
        this.procesosBloqueados = new ArrayList<>();
        this.procesosEnEspera = new ArrayList<>();
    }

    /* ==================== CREACIÓN DE PROCESOS ==================== */

    public ResultadoCarga cargarPrograma(File archivo) {
        if (getTotalProcesos() >= MAX_PROCESOS) {
            return ResultadoCarga.error(
                "Ya hay " + MAX_PROCESOS + " procesos en el sistema (maximo permitido).\n"
                + "Espera a que termine alguno o presiona 'Limpiar'.");
        }

        Ensamblador ensamblador = new Ensamblador();

        if (!ensamblador.esArchivoValido(archivo)) {
            return ResultadoCarga.error(ensamblador.getErroresComoTexto());
        }

        List<Instruccion> instrucciones = ensamblador.leerArchivo(archivo);

        if (instrucciones.size() > particionador.getTamanoParticion()) {
            return ResultadoCarga.error(
                "El programa tiene " + instrucciones.size() + " instrucciones, "
                + "pero cada partición solo admite " + particionador.getTamanoParticion());
        }

        int indice = particionador.asignarParticion();
        if (indice == -1) {
            int direccionBase = memoria.reservarBloque(BCP.POSICIONES_REQUERIDAS);
            if (direccionBase == -1) {
                return ResultadoCarga.error(
                    "No hay espacio en el kernel para registrar más BCPs.");
            }
            BCP bcp = new BCP(memoria, direccionBase, siguienteId++, 1);
            bcp.setEstado(EstadoProceso.READY_SUSPEND);
            bcp.setAlcance(instrucciones.size());
            procesosEnEspera.add(new ProcesoEnEspera(bcp, archivo));
            return ResultadoCarga.enEspera();
        }

        BCP bcp = crearProcesoEnParticion(instrucciones, indice);
        return ResultadoCarga.exito(bcp);
    }

    private BCP crearProcesoEnParticion(List<Instruccion> instrucciones, int indiceParticion) {
        int direccionBase = memoria.reservarBloque(BCP.POSICIONES_REQUERIDAS);
        if (direccionBase == -1) {
            throw new IllegalStateException(
                "No hay espacio en el kernel para registrar más BCPs.");
        }

        BCP bcp = new BCP(memoria, direccionBase, siguienteId++, 1);
        admitirEnParticion(bcp, instrucciones, indiceParticion);
        return bcp;
    }

    /* ==================== EJECUCIÓN PASO A PASO ==================== */

    public boolean ejecutarUnPaso() {
        if (!despachador.procesoActualOcupaCPU()) {
            if (despachador.getEjecutorActual() != null) {
                BCP anterior = despachador.getEjecutorActual().getBcp();
                if (anterior.getEstado() == EstadoProceso.EXIT) {
                    procesoTerminado(anterior);
                }
                despachador.limpiarEjecutor();
            }

            BCP siguiente = planificador.seleccionarSiguiente();
            if (siguiente == null) {
                return false;
            }

            listaDeTrabajos.sacarPrimero();
            despachador.despachar(siguiente, memoria);
        }

        boolean sigueVivo = despachador.ejecutarUnPaso();
        BCP actual = despachador.getEjecutorActual().getBcp();

        if (!sigueVivo) {
            procesoTerminado(actual);
            despachador.limpiarEjecutor();
        } else if (actual.getEstado() == EstadoProceso.BLOCKED) {
            despachador.guardarContexto(actual);
            actual.setCpuAsignado(-1);
            despachador.limpiarEjecutor();
            procesosBloqueados.add(actual);
        }

        return true;
    }

    public int ejecutarAutomatico() {
        int pasos = 0;
        while (hayProcesosActivos()) {
            ejecutarUnPaso();
            pasos++;
            if (pasos > 100000) {
                throw new IllegalStateException(
                    "Demasiados pasos: posible ciclo infinito entre procesos.");
            }
        }
        return pasos;
    }

    /* ==================== TERMINACIÓN ==================== */

    /**
     * Se llama cuando un proceso termina.
     *
     * Pasos:
     *   1. Marcar estado final en el BCP (EXIT).
     *   2. Crear un snapshot (BCPTerminado) ANTES de liberar el BCP.
     *   3. Agregar el snapshot a procesosTerminados.
     *   4. Liberar la particion de usuario.
     *   5. Liberar el bloque del BCP en el kernel.
     *   6. Intentar admitir el siguiente proceso en espera.
     */
    public void procesoTerminado(BCP bcp) {
        // 1. Marcar estado final
        bcp.setEstado(EstadoProceso.EXIT);
        bcp.marcarFin();
        bcp.setCpuAsignado(-1);

        // 2. Crear snapshot ANTES de liberar el BCP
        BCPTerminado snapshot = new BCPTerminado(
            bcp.getId(),
            bcp.getEstado(),
            bcp.getTiempoInicio(),
            bcp.getTiempoFin(),
            bcp.getBase(),
            bcp.getAlcance(),
            bcp.getPrioridad()
        );

        // 3. Agregar snapshot a la lista de terminados
        this.procesosTerminados.add(snapshot);

        // 4. Copiar base y alcance a variables locales (ANTES de liberar el BCP)
        int base = bcp.getBase();
        int alcance = bcp.getAlcance();

        // 5. Liberar la particion de usuario
        int indice = particionador.indiceDesdePosicion(base);
        particionador.liberarParticion(indice);

        // 6. Limpiar las posiciones de la zona usuario
        for (int i = 0; i < alcance; i++) {
            memoria.escribir(base + i, null);
        }

        // 7. Liberar el bloque del BCP en el kernel
        memoria.liberarBloque(bcp.getDireccionBase(), BCP.POSICIONES_REQUERIDAS);

        // 8. Intentar admitir el siguiente proceso en espera
        if (!procesosEnEspera.isEmpty()) {
            ProcesoEnEspera pendiente = procesosEnEspera.remove(0);
            Ensamblador ens = new Ensamblador();
            List<Instruccion> instr = ens.leerArchivo(pendiente.getArchivo());
            int indiceNuevo = particionador.asignarParticion();
            if (indiceNuevo != -1) {
                admitirEnParticion(pendiente.getBcp(), instr, indiceNuevo);
            } else {
                procesosEnEspera.add(0, pendiente);
            }
        }
    }

    private void admitirEnParticion(BCP bcp, List<Instruccion> instrucciones, int indiceParticion) {
        int base = particionador.getBaseParticion(indiceParticion);
        bcp.setBase(base);
        bcp.setAlcance(instrucciones.size());
        bcp.setPc(base);
        bcp.setEstado(EstadoProceso.READY);

        int pos = base;
        for (Instruccion instr : instrucciones) {
            memoria.escribir(pos++, instr);
        }

        listaDeTrabajos.agregar(bcp);
    }

    /* ==================== CONSULTAS PARA LA GUI ==================== */

    public int getTotalProcesos() {
        return listaDeTrabajos.getCantidad()
             + procesosBloqueados.size()
             + procesosEnEspera.size()
             + (despachador.getEjecutorActual() != null ? 1 : 0);
    }

    public boolean hayProcesosActivos() {
        return !listaDeTrabajos.estaVacia()
            || despachador.procesoActualOcupaCPU()
            || !procesosBloqueados.isEmpty();
    }

    public BCP getProcesoActual() {
        if (despachador.getEjecutorActual() != null) {
            return despachador.getEjecutorActual().getBcp();
        }
        if (!listaDeTrabajos.estaVacia()) {
            return listaDeTrabajos.verPrimero();
        }
        return null;
    }

    public ListaDeTrabajos getListaDeTrabajos() {
        return listaDeTrabajos;
    }

    public int getCantidadEnEspera() {
        return procesosEnEspera.size();
    }

    public List<BCPTerminado> getProcesosTerminados() {
        return procesosTerminados;
    }

    public List<BCP> getProcesosBloqueados() {
        return procesosBloqueados;
    }

    public List<ProcesoEnEspera> getProcesosEnEspera() {
        return procesosEnEspera;
    }
}