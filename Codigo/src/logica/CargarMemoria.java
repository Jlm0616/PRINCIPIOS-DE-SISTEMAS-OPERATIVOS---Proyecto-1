package logica;

import modelo.Memoria;
import modelo.Instruccion;
import java.util.List;

/**
 * Se encarga de cargar un programa (lista de instrucciones) en la memoria
 * de la máquina virtual.
 *
 * Cada instrucción se guarda en UNA posición de memoria, codificada como
 * un string binario de 16 bits:
 *
 *   [ opcode (4) | registro (4) | signo (1) | magnitud (7) ]
 *
 * Ejemplos:
 *   MOV AX, 5  -> "0011000100000101"
 *   ADD BX     -> "0101001000000000"
 *
 * La carga comienza en el límite kernel/usuario, es decir, siempre se escribe
 * en la zona de usuario y nunca sobre la zona kernel.
 */
public class CargarMemoria {

    private Traductor traductor;   // codifica la instrucción a binario de 16 bits

    public CargarMemoria() {
        traductor = new Traductor();
    }

    /**
     * Escribe las instrucciones en la memoria a partir del límite kernel/usuario.
     *
     * Cada instrucción ocupa UNA posición con su binario de 16 bits:
     *   posicionActual     -> "0011000100000101"   (MOV AX, 5)
     *   posicionActual + 1 -> "0011001000000011"   (MOV BX, 3)
     *
     * @param listaInstrucciones instrucciones a cargar, en orden de ejecución
     * @param memoria            memoria destino (debe tener espacio suficiente)
     */
    public void cargar(List<Instruccion> listaInstrucciones, Memoria memoria) {
        int posicionActual = memoria.getLimiteKernelUsuario();

        for (Instruccion instruccionActual : listaInstrucciones) {
            String binario = traductor.instruccionCompleta(instruccionActual);
            memoria.escribir(posicionActual, binario);
            posicionActual++;   // 1 posición por instrucción
        }
    }
}