package modelo;

/**
 * Estados posibles de un proceso (modelo de 7 estados de Silberschatz).
 *
 * NOTA: nombres provisionales. El enunciado del Proyecto #1 menciona
 * "nuevo, preparado, ejecución, suspendido, en espera y finalizado (7 estados)",
 * lo cual es ambiguo. Se usa el modelo clásico de Silberschatz hasta
 * confirmar con el profesor.
 *
 * Transiciones típicas:
 *   NEW -> READY -> RUNNING -> EXIT
 *                    RUNNING -> BLOCKED -> READY
 *                    READY   -> READY_SUSPEND -> READY
 *                    BLOCKED -> BLOCKED_SUSPEND -> BLOCKED
 */
public enum EstadoProceso {
    NEW,               // recién creado
    READY,             // preparado, esperando CPU
    RUNNING,           // en ejecución
    BLOCKED,           // en espera (E/S)
    EXIT,              // finalizado
    READY_SUSPEND,     // preparado pero suspendido (swap out)
    BLOCKED_SUSPEND    // bloqueado y suspendido (swap out)
}