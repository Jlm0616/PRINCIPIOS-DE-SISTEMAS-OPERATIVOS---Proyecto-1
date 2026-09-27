package logica;

import modelo.BCP;
import java.io.File;

/**
 * Representa un proceso que no pudo ser admitido en memoria principal
 * porque no había partición libre disponible.
 *
 * Encapsula:
 *   - El BCP del proceso (con estado READY_SUSPEND).
 *   - El archivo .asm original (para poder releer las instrucciones
 *     cuando se libere una partición).
 *
 * Sigue el modelo de Stallings (sección 3.2): un proceso que no cabe
 * en memoria queda en estado READY_SUSPEND hasta que se libere espacio.
 *
 * Una vez admitido, el proceso pasa a READY y se elimina de la lista
 * de procesos en espera.
 */
public class ProcesoEnEspera {

    private final BCP bcp;
    private final File archivo;

    /**
     * Crea un proceso en espera con su BCP y el archivo original.
     *
     * @param bcp     BCP del proceso (debe tener estado READY_SUSPEND)
     * @param archivo archivo .asm original
     * @throws IllegalArgumentException si bcp o archivo son null
     */
    public ProcesoEnEspera(BCP bcp, File archivo) {
        if (bcp == null) {
            throw new IllegalArgumentException("El BCP no puede ser nulo");
        }
        if (archivo == null) {
            throw new IllegalArgumentException("El archivo no puede ser nulo");
        }
        this.bcp = bcp;
        this.archivo = archivo;
    }

    public BCP getBcp() {
        return bcp;
    }

    public File getArchivo() {
        return archivo;
    }

    @Override
    public String toString() {
        return "ProcesoEnEspera{id=" + bcp.getId()
                + ", archivo=" + archivo.getName() + "}";
    }
}