# Estrategia de Protección y Seguridad

**Proyecto #1 — Gestor de Procesos — IC-6600**

**Autor:** Julian Lizano Monge
**Carné:** 2024188887
**Curso:** Principios de Sistemas Operativos
**Profesor:** Ing. Cristian Campos Agüero
**Institución:** Instituto Tecnológico de Costa Rica

---

## Introducción

Este documento describe las estrategias de protección y seguridad implementadas en el simulador de la minicomputadora, en cumplimiento con el apartado **"Protección & Seguridad (Cuál fue su estrategia)"** del enunciado del Proyecto #1.

La estrategia se basa en **validación en múltiples capas**:

1. Validación en el ensamblador (antes de cargar).
2. Validación en el gestor de memoria (al asignar).
3. Validación en el ejecutor (al ejecutar).
4. Validación en la configuración (al iniciar).

Ninguna capa por sí sola es suficiente, pero en conjunto garantizan que un programa malicioso o mal escrito no pueda corromper el sistema operativo ni afectar a otros procesos.

---

## 1. Aislamiento Kernel / Usuario

### Distribución de la memoria principal

La memoria principal está dividida en dos zonas claramente separadas:

| Zona | Rango | Contenido |
|---|---|---|
| **Kernel** | `[0, limiteKernelUsuario)` | ListaProcesos (5 pos), BCPs (N × 30 pos), TablaMemoria (N × 3 pos) |
| **Usuario** | `[limiteKernelUsuario, tamano)` | Instrucciones de los procesos |

### Implementación

- `Memoria.esZonaKernel(posicion)` → `posicion < limiteKernelUsuario`.
- El método `Memoria.reservarBloqueUsuario(tamano)` **solo busca desde `limiteKernelUsuario`** hacia arriba. Nunca asigna direcciones en la zona kernel.
- El método `Memoria.liberarBloqueUsuario` **solo libera posiciones de la zona usuario**, sin tocar el kernel.

### Cálculo de BCPs que caben en el kernel

El número de BCPs que caben en el kernel se calcula iterativamente:

```java
for (int n = maxProcesos; n >= 1; n--) {
    int tamanoListaTrabajos = maxArchivos * 4;
    int tamanoBCPs = n * 30;
    int tamanoTablaMem = n * 3;
    int total = tamanoListaTrabajos + tamanoBCPs + tamanoTablaMem;
    if (total <= limiteKernel) return n;
}
return 0;
```

Si no cabe ni 1 BCP, se lanza `IllegalArgumentException` al inicializar.

### Resultado

Un proceso de usuario **no puede escribir ni leer la zona del kernel**, porque todas sus instrucciones se cargan a partir de `base >= limiteKernelUsuario`.

---

## 2. Validación del Contador de Programa (PC)

### Estrategia

El PC de cada proceso debe estar siempre dentro del bloque asignado en la zona de usuario: `[base, base + alcance)`.

### Implementación

- Al cargar un proceso, `GestorProcesos.admitirEnParticion()` asigna `bcp.setPc(base)`. Esto garantiza que el PC apunte a la primera instrucción del proceso.
- El `EjecutorCPU`, antes de leer una instrucción, verifica que la posición del PC corresponda a una instrucción válida del proceso actual:

```java
private boolean esPcValido(int pc) {
    int base = bcp.getBase();
    int alcance = bcp.getAlcance();
    return pc >= base && pc < base + alcance;
}
```

Casos de validación:
1. PC fuera del alcance al iniciar → ERROR FATAL, EXIT.
2. PC fuera del alcance al avanzar (+1) → FIN normal, EXIT.
3. JMP/JE/JNE fuera del alcance → ERROR FATAL, EXIT.
4. PC a posición vacía dentro del alcance → FIN normal, EXIT.

### Resultado

Un programa malicioso **no puede saltar a la zona del kernel ni a la zona de otro proceso**.

---

## 3. Validación de Registros

### Estrategia

Solo se aceptan los registros definidos en el enunciado: `AC`, `AX`, `BX`, `CX`, `DX`, `AH`, `AL`. Cualquier otro nombre es rechazado en la fase de ensamblado.

**IMPORTANTE:** `AH` y `AL` NO son campos propios de la CPU: son las dos mitades del registro `AX`.
- `AH` = byte alto de AX: `(AX >> 8) & 0xFF`
- `AL` = byte bajo de AX: `AX & 0xFF`

### Implementación

- `Ensamblador.REGISTROS_VALIDOS` es un `HashSet` con `{"AC", "AX", "BX", "CX", "DX", "AH", "AL"}`.
- `Ensamblador.validarRegistro(arg)` lanza `IllegalArgumentException` si el registro no está en el conjunto.
- El método `Instruccion.esRegistro(indice)` también valida los argumentos antes de que el `EjecutorCPU` los use.

### Resultado

Un `.asm` con `MOV ZZ, 5` es rechazado con un mensaje claro:
Línea 3: registro desconocido 'ZZ'. Válidos: [AC, AX, BX, CX, DX, AH, AL]

---

## 4. Protección de la Pila

### Estrategia

Cada proceso tiene una pila de tamaño **fijo de 5 posiciones**. Se detecta desbordamiento (push en pila llena) y subdesbordamiento (pop en pila vacía).

### Implementación

- `BCP.TAMANO_MAXIMO_PILA = 5`.
- `BCP.apilar(valor)` recorre las 5 posiciones. Si todas están ocupadas, lanza `IllegalStateException("Desbordamiento de pila ...")`.
- `BCP.desapilar()` recorre de arriba hacia abajo. Si todas están vacías, lanza `IllegalStateException("Subdesbordamiento de pila ...")`.

### Resultado

El `EjecutorCPU` captura la excepción, marca el proceso como `EXIT` y registra el error en pantalla, evitando que el proceso corrompa la memoria.

---

## 5. Protección de Archivos Abiertos

### Estrategia

Cada proceso puede tener **máximo 5 archivos abiertos** simultáneamente.

### Implementación

- `BCP.TAMANO_MAXIMO_ARCHIVOS = 5`.
- `BCP.abrirArchivo(nombre)`:
  1. Verifica si el archivo ya está abierto (evita duplicados).
  2. Busca el primer hueco libre.
  3. Si la lista está llena, retorna `false`.
- `BCP.cerrarArchivo(nombre)` quita el archivo de la lista.
- El manejador de INT 21H (AH=3Dh) usa `setAl(1)` si el archivo no se puede abrir.

### Resultado

Un proceso no puede agotar la lista de archivos del sistema ni abrir el mismo archivo dos veces.

---

## 6. Validación de Sintaxis de Archivos `.asm`

### Estrategia

Todo archivo `.asm` es **validado antes de cargarse**. Si una sola línea es inválida, el archivo completo se rechaza y se muestran los errores con número de línea.

### Implementación

- `Ensamblador.esArchivoValido(File)` recorre todas las líneas y acumula errores en `erroresEncontrados`.
- Validaciones aplicadas:
  - Extensión `.asm` obligatoria.
  - Al menos una instrucción.
  - Opcode conocido (18 opcodes válidos).
  - Cantidad de argumentos correcta según el opcode.
  - Registros válidos.
  - Valores numéricos enteros.
  - Códigos de interrupción con formato `XXH` (ej. `20H`, `09H`).
- `Ensamblador.getErroresComoTexto()` devuelve todos los errores juntos.

### Resultado

Un archivo con un error en la línea 5 se rechaza y se muestra:
Línea 5: el desplazamiento 'abc' no es un numero entero valido
-> "JMP abc"

---

## 7. Validación de Configuración Externa

### Estrategia

Todos los parámetros leídos de `config.txt` son validados. Si un valor está fuera de rango, se corrige automáticamente y se avisa al usuario.

### Implementación

- `ConfiguracionExterna.validarYCorregir()` valida:
  - `memoria` entre `MEMORIA_MINIMO` y `MEMORIA_MAXIMO`.
  - `kernel` al menos el 30% de `memoria` y menor que `memoria`.
  - `disco` entre `DISCO_MINIMO` y `DISCO_MAXIMO`.
  - `max_archivos` entre `MAX_ARCHIVOS_MINIMO` y `MAX_ARCHIVOS_MAXIMO`.
  - `memoria_virtual` entre `MEMORIA_VIRTUAL_MINIMO` y `MEMORIA_VIRTUAL_MAXIMO`.
  - `max_procesos = 5` (FIJO, no configurable).
  - Validación cruzada: kernel debe alcanzar para 1 BCP + ListaDeTrabajos.
  - Validación cruzada: espacio del disco (índice ASM + índice PROCESO + swap + archivos).
- Si hay correcciones, se guardan en `mensajesCorrecciones` y se muestra un diálogo al usuario.

### max_procesos fijo

Según el enunciado:
> "Para este diseño dispondremos de un CPU, y podrá ejecutar hasta 5 procesos."

El valor no se lee del `config.txt` y no se puede cambiar desde la GUI.

### Resultado

Un `config.txt` con `memoria=-5` se corrige a `memoria=256` (default) y se avisa al usuario:
Se encontraron valores invalidos en config.txt
y se corrigieron automaticamente:

memoria=-5 invalido (minimo 128). Usando 256.

---

## 8. Protección contra Ciclos Infinitos

### Estrategia

El `EjecutorCPU` tiene un límite máximo de ciclos en modo automático para evitar que un programa con un ciclo infinito bloquee el sistema.

### Implementación

- `EjecutorCPU.MAX_CICLOS_AUTOMATICO = 10000`.
- `ejecutarHastaTerminar()` termina el proceso con `EXIT` si supera el límite.
- `GestorProcesos.ejecutarAutomatico()` tiene un límite de 100000 pasos.

### Resultado

Un programa con un `JMP` infinito termina automáticamente y el sistema sigue funcionando.

---

## 9. Protección contra Acceso a Memoria Inválida

### Estrategia

El `Memoria.leer()` y `Memoria.escribir()` no validan índices por defecto (para eficiencia), pero las clases que los usan validan antes.

### Implementación

- `Memoria.leerInstruccion(posicion)` lanza `ClassCastException` si la posición no contiene una `Instruccion`.
- `Memoria.leerBCP(posicion)` lanza `ClassCastException` si la posición no contiene un `BCP`.
- `Disco.leer(posicion)` valida que la posición esté dentro del disco.

### Resultado

Un acceso incorrecto lanza una excepción controlada, no corrompe la memoria.

---

## 10. Validación de Saltos

### Estrategia

Los saltos (JMP/JE/JNE) se validan ANTES de aplicarse.

### Implementación

```java
case "JMP": {
    int nuevoPc = pc + 1 + instr.getArgumentoComoEntero(0);
    if (!esPcValido(nuevoPc)) {
        terminarConError("JMP fuera del alcance (PC=" + nuevoPc
                + ", base=" + bcp.getBase()
                + ", alcance=" + bcp.getAlcance() + ")");
        return true;
    }
    cpu.setPC(nuevoPc);
    return true;
}
```

### Resultado

Un salto a la zona kernel o a otro proceso se rechaza y el proceso termina con EXIT.

---

## 11. Validación de INT 21H

### Estrategia

INT 21H maneja archivos. Se validan todas las operaciones.

### Implementación

```
INT 21H: manejo de archivos.
  AH = operación:
    3Ch -> crear
    3Dh -> abrir
    4Dh -> leer
    40h -> escribir
    41h -> eliminar
  DX = cadena de texto con el nombre del archivo (String).
       Ejemplo: MOV DX, "datos.txt"
  AL = resultado (0 éxito, 1 error, o contenido leído).

Validaciones:
  1. DX no puede estar vacío. Si DX está vacío (""), se retorna AL=1 (error)
     y se muestra el mensaje en pantalla.
  2. Crear archivo que ya existe → AL=1.
  3. Abrir archivo que no existe → AL=1.
  4. Más de 5 archivos abiertos → AL=1.
  5. Leer archivo que no existe → AL=1.
  6. Eliminar archivo que no existe → AL=1.

IMPORTANTE: todos los archivos creados por procesos van al
INDICE PROCESO del disco, NO al indice ASM. Esto evita conflictos
entre los .asm cargados por el usuario y los archivos creados
por los procesos en ejecución.
```

### Resultado

Un proceso no puede realizar operaciones inválidas sobre archivos ni agotar recursos del sistema.

---

## 12. Validación de ListaDeTrabajos

### Estrategia

La ListaDeTrabajos vive en RAM (zona kernel) y apunta a los índices del disco.

### Implementación

Cada entrada ocupa 4 posiciones: `[nombre, inicio, fin, zona]`

Validaciones:
1. Si un proceso no cabe en RAM, va a swap (zona VIRTUAL).
2. Se registra en el índice ASM del disco con zona VIRTUAL.
3. Se agrega a la ListaDeTrabajos en RAM.
4. Cuando un proceso termina, se reactiva el siguiente de la ListaDeTrabajos (si hay espacio en RAM).

### Resultado

Los procesos se gestionan de forma ordenada y no se pierden trabajos pendientes.

---

## 13. Validación de TablaMemoria

### Estrategia

La TablaMemoria es proporcional a `bcpsQueCaben`.

### Implementación

- TablaMemoria: `bcpsQueCaben × 3` posiciones (id, inicio, tamaño).
- Si solo caben 2 BCPs, la tabla ocupa 6 posiciones.

### Resultado

La tabla se adapta dinámicamente al espacio disponible en el kernel.

---

## 14. Validación de Particionador Dinámico

### Estrategia

ParticionadorDinamico (first-fit) asigna bloques en la zona usuario.

### Implementación

1. Solo asigna bloques en la zona usuario (`[limiteKernelUsuario, fin)`).
2. Fusiona bloques libres contiguos al liberar.
3. Si no hay espacio contiguo, devuelve -1.

### Resultado

No se puede asignar una partición que invada el kernel.

---

## 15. Resumen de la Estrategia

| Amenaza | Protección |
|---|---|
| Proceso escribe en kernel | Aislamiento kernel/usuario |
| Proceso salta fuera de su bloque | Validación de PC y saltos |
| Instrucción con registro inválido | Validación en ensamblador (AC, AX, BX, CX, DX, AH, AL) |
| Desbordamiento de pila | Pila de tamaño fijo 5 |
| Agotamiento de archivos abiertos | Máximo 5 por proceso |
| Archivo `.asm` malicioso | Validación de sintaxis |
| Configuración inválida | Corrección automática |
| Ciclo infinito | Límite de ciclos |
| Acceso a memoria inválida | Validación de tipos |
| Operación INT 21H inválida | Validación de AH, DX, archivos |
| Proceso no cabe en RAM | ListaDeTrabajos + swap |
| Kernel mal dimensionado | Cálculo iterativo de bcpsQueCaben |
| max_procesos incorrecto | Fijo en 5 |
| TablaMemoria mal dimensionada | Proporcional a bcpsQueCaben |
| Partición invade kernel | First-fit desde limiteKernelUsuario |

---

## Conclusión

La estrategia de seguridad del simulador se basa en **validación en múltiples capas**:

1. **Validación en el ensamblador** (antes de cargar).
2. **Validación en el gestor de memoria** (al asignar).
3. **Validación en el ejecutor** (al ejecutar).
4. **Validación en la configuración** (al iniciar).

Ninguna capa por sí sola es suficiente, pero en conjunto garantizan que un programa malicioso o mal escrito no pueda corromper el sistema operativo ni afectar a otros procesos.

---

## Referencias

- Stallings, W. *Operating Systems: Internals and Design Principles*.
- Silberschatz, A., Galvin, P. *Operating System Concepts*.
- Enunciado del Proyecto #1 — IC-6600 Principios de Sistemas Operativos.