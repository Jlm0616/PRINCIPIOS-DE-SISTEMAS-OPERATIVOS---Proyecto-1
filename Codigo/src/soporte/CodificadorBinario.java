package soporte;

/**
 * Utilidades para convertir entre números enteros y strings binarios.
 *
 * Se usa para codificar/decodificar datos en la memoria simulada.
 */
public class CodificadorBinario {

    /** Convierte un entero a binario de N bits (sin signo). */
    public static String aBinario(int valor, int bits) {
        String bin = Integer.toBinaryString(valor & ((1 << bits) - 1));
        return String.format("%" + bits + "s", bin).replace(' ', '0');
    }

    /** Convierte binario sin signo a entero. */
    public static int desdeBinario(String bin) {
        return Integer.parseInt(bin, 2);
    }

    /** Convierte binario de N bits (complemento a 2) a entero con signo. */
    public static int desdeBinarioConSigno(String bin) {
        int v = Integer.parseInt(bin, 2);
        int bits = bin.length();
        int limite = 1 << (bits - 1);
        return (v >= limite) ? v - (1 << bits) : v;
    }
}