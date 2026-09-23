package logica;

import modelo.Instruccion;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * Ensamblador de la máquina virtual.
 *
 * Se encarga de dos tareas:
 *   1. Validar un archivo .asm (sintaxis, opcodes y registros conocidos).
 *   2. Leerlo y convertirlo en una lista de objetos {@link Instruccion}.
 *
 * Formato de línea aceptado (opcode + registro + valor opcional):
 *   OPCODE REGISTRO[,] [VALOR]
 *
 * Ejemplos válidos:
 *   MOV AX, 5
 *   LOAD BX
 *   ADD CX, 10
 *
 * Si la validación falla, el mensaje del primer error queda disponible
 * en {@link #getPrimerErrorEncontrado()}.
 */
public class Ensamblador {

    /** Conjunto de opcodes que el ensamblador reconoce como válidos. */
    private static final Set<String> OPCODES_VALIDOS = new HashSet<>(
            Arrays.asList("LOAD", "STORE", "MOV", "SUB", "ADD"));

    /** Conjunto de registros que el ensamblador reconoce como válidos. */
    private static final Set<String> REGISTROS_VALIDOS = new HashSet<>(
            Arrays.asList("AX", "BX", "CX", "DX"));

    /** Rango máximo del valor (signo-magnitud de 8 bits). */
    private static final int VALOR_MAXIMO = 127;
    private static final int VALOR_MINIMO = -127;

    private String primerErrorEncontrado;   // mensaje del primer error detectado
    private String motivoErrorLinea;        // motivo específico del error de la última línea

    /**
     * Verifica que el archivo exista, tenga extensión .asm, y que cada
     * línea de contenido tenga formato válido (opcode y registro conocidos,
     * y valor numérico cuando corresponde).
     *
     * Reglas:
     *   - Se ignoran las líneas vacías.
     *   - El archivo debe contener al menos una instrucción.
     *   - Si una sola línea falla, el archivo completo se considera inválido.
     *
     * El mensaje del primer error encontrado queda guardado internamente
     * y puede recuperarse con {@link #getPrimerErrorEncontrado()}.
     *
     * @param archivo archivo a validar
     * @return true si el archivo es válido; false en caso contrario
     */
    public boolean esArchivoValido(File archivo) {
        primerErrorEncontrado = null;

        if (archivo == null || !archivo.exists() || !archivo.canRead()) {
            primerErrorEncontrado = "El archivo no existe o no se puede leer.";
            return false;
        }

        if (!archivo.getName().toLowerCase().endsWith(".asm")) {
            primerErrorEncontrado = "El archivo debe tener extension .asm";
            return false;
        }

        int numeroLinea = 0;
        boolean tieneAlMenosUnaInstruccion = false;

        try (Scanner lector = new Scanner(archivo)) {
            while (lector.hasNextLine()) {
                numeroLinea++;
                String linea = lector.nextLine().trim();

                if (linea.isEmpty()) {
                    continue;
                }

                if (!esLineaValida(linea)) {
                    primerErrorEncontrado = "Linea " + numeroLinea + ": " + motivoErrorLinea
                            + "\n-> \"" + linea + "\"";
                    return false;
                }

                tieneAlMenosUnaInstruccion = true;
            }
        } catch (Exception e) {
            primerErrorEncontrado = "No se pudo leer el archivo: " + e.getMessage();
            return false;
        }

        if (!tieneAlMenosUnaInstruccion) {
            primerErrorEncontrado = "El archivo no contiene ninguna instruccion.";
            return false;
        }

        return true;
    }

    /**
     * Valida una sola línea de código ensamblador.
     *
     * Formato aceptado:
     *   - 2 partes: OPCODE REGISTRO
     *   - 3 partes: OPCODE REGISTRO VALOR
     *
     * El opcode y el registro se normalizan a mayúsculas.
     * La coma tras el registro es opcional.
     *
     * @param linea línea a validar (sin espacios al inicio/fin)
     * @return true si la línea es válida
     */
    private boolean esLineaValida(String linea) {
        motivoErrorLinea = null;

        String[] partes = linea.split(" ");

        if (partes.length != 2 && partes.length != 3) {
            motivoErrorLinea = "se esperaban 2 o 3 partes (OPCODE REGISTRO [VALOR]), se encontraron " + partes.length;
            return false;
        }

        String opcode = partes[0].toUpperCase();
        if (!OPCODES_VALIDOS.contains(opcode)) {
            motivoErrorLinea = "opcode desconocido '" + partes[0] + "'. Válidos: " + OPCODES_VALIDOS;
            return false;
        }

        String registro = partes[1].replace(",", "").toUpperCase();
        if (!REGISTROS_VALIDOS.contains(registro)) {
            motivoErrorLinea = "registro desconocido '" + partes[1] + "'. Válidos: " + REGISTROS_VALIDOS;
            return false;
        }

        if (partes.length == 3) {
            int valor;
            try {
                valor = Integer.parseInt(partes[2]);
            } catch (NumberFormatException e) {
                motivoErrorLinea = "el valor '" + partes[2] + "' no es un numero entero";
                return false;
            }
            if (valor < VALOR_MINIMO || valor > VALOR_MAXIMO) {
                motivoErrorLinea = "el valor " + valor + " esta fuera de rango. "
                    + "Rango permitido: " + VALOR_MINIMO + " a " + VALOR_MAXIMO
                    + " (signo-magnitud de 8 bits)";
                return false;
            }
        }

        return true;
    }

    /** @return el mensaje del primer error detectado en la última validación. */
    public String getPrimerErrorEncontrado() {
        return primerErrorEncontrado;
    }

    /**
     * Lee un archivo .asm y devuelve sus instrucciones como objetos.
     *
     * IMPORTANTE: se asume que el archivo ya fue validado con
     * {@link #esArchivoValido(File)}. Esta función NO valida sintaxis:
     * si una línea está mal formada, puede lanzar excepción o generar
     * instrucciones incorrectas.
     *
     *
     * @param archivoEnsamblador archivo .asm previamente validado
     * @return lista de instrucciones en orden de aparición
     */
    public List<Instruccion> leerArchivo(File archivoEnsamblador) {
        List<Instruccion> instrucciones = new ArrayList<>();

        try {
            Scanner lector = new Scanner(archivoEnsamblador);
            while (lector.hasNextLine()) {
                String linea = lector.nextLine();
                String[] lineasCodigo = linea.split(" ");
                String opcode = lineasCodigo[0];
                String registro = lineasCodigo[1].replace(",", "");
                int valor = 0;

                if (lineasCodigo.length > 2) {
                    valor = Integer.parseInt(lineasCodigo[2]);
                }

                Instruccion instruccionEnsamblador = new Instruccion(opcode, registro, valor);
                instrucciones.add(instruccionEnsamblador);
            }
        } catch (Exception e) {
            System.out.println("Error leyendo el archivo: " + e.getMessage());
        }

        return instrucciones;
    }
}