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

### Estrategia

La memoria principal está dividida en dos zonas claramente separadas:
┌──────────────────────────────────────────────────┐
│ ZONA KERNEL [0, limiteKernelUsuario) │
│ ├── ListaProcesos (5 posiciones) │
│ ├── BCPs (N × 30 posiciones) │
│ └── TablaMemoria (15 posiciones) │
├──────────────────────────────────────────────────┤
│ ZONA USUARIO [limiteKernelUsuario, tamano) │
│ └── Instrucciones de los procesos │
└──────────────────────────────────────────────────┘

text

### Implementación

- `Memoria.esZonaKernel(posicion)` → `posicion < limiteKernelUsuario`.
- El método `Memoria.reservarBloqueUsuario(tamano)` **solo busca desde `limiteKernelUsuario`** hacia arriba. Nunca asigna direcciones en la zona kernel.
- El método `Memoria.liberarBloqueUsuario` **solo libera posiciones de la zona usuario**, sin tocar el kernel.

### Resultado

Un proceso de usuario **no puede escribir ni leer la zona del kernel**, porque todas sus instrucciones se cargan a partir de `base >= limiteKernelUsuario`.

---

## 2. Validación del Contador de Programa (PC)

### Estrategia

El PC de cada proceso debe estar siempre dentro del bloque asignado en la zona de usuario: `[base, base + alcance)`.

### Implementación

- Al cargar un proceso, `GestorProcesos.admitirEnParticion()` asigna `bcp.setPc(base)`.
- El `EjecutorCPU`, antes de leer una instrucción, verifica que la posición del PC corresponda a una instrucción válida del proceso actual.
- Si el PC sale del bloque (por ejemplo, por un `JMP` mal calculado), el proceso termina con estado `EXIT` y se registra el error en pantalla.

### Resultado

Un programa malicioso **no puede saltar a la zona del kernel ni a la zona de otro proceso**.

---

## 3. Validación de Registros

### Estrategia

Solo se aceptan los registros definidos en el enunciado: `AC`, `AX`, `BX`, `CX`, `DX`. Cualquier otro nombre es rechazado en la fase de ensamblado.

### Implementación

- `Ensamblador.REGISTROS_VALIDOS` es un `HashSet` con `{"AC", "AX", "BX", "CX", "DX"}`.
- `Ensamblador.validarRegistro(arg)` lanza `IllegalArgumentException` si el registro no está en el conjunto.
- El método `Instruccion.esRegistro(indice)` también valida los argumentos antes de que el `EjecutorCPU` los use.

### Resultado

Un `.asm` con `MOV ZZ, 5` es rechazado con un mensaje claro:
Línea 3: registro desconocido 'ZZ'. Válidos: [AC, AX, BX, CX, DX]

text

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

text

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
  - `max_procesos` entre `MAX_PROCESOS_MINIMO` y `MAX_PROCESOS_MAXIMO`.
  - Espacio del disco: índice + swap + archivos >= mínimo.
- Si hay correcciones, se guardan en `mensajesCorrecciones` y se muestra un diálogo al usuario.

### Resultado

Un `config.txt` con `memoria=-5` se corrige a `memoria=256` (default) y se avisa al usuario:
Se encontraron valores invalidos en config.txt
y se corrigieron automaticamente:

memoria=-5 invalido (minimo 128). Usando 256.

text

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

## 10. Resumen de la Estrategia

| Amenaza | Protección |
|---|---|
| Proceso escribe en kernel | Aislamiento kernel/usuario |
| Proceso salta fuera de su bloque | Validación de PC |
| Instrucción con registro inválido | Validación en ensamblador |
| Desbordamiento de pila | Pila de tamaño fijo 5 |
| Agotamiento de archivos abiertos | Máximo 5 por proceso |
| Archivo `.asm` malicioso | Validación de sintaxis |
| Configuración inválida | Corrección automática |
| Ciclo infinito | Límite de ciclos |
| Acceso a memoria inválida | Validación de tipos |

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