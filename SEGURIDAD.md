\# Estrategia de Protección y Seguridad



\*\*Proyecto #1 — Gestor de Procesos — IC-6600\*\*



\*\*Autor:\*\* Julian Lizano Monge

\*\*Carné:\*\* 2024188887

\*\*Curso:\*\* Principios de Sistemas Operativos

\*\*Profesor:\*\* Ing. Cristian Campos Agüero

\*\*Institución:\*\* Instituto Tecnológico de Costa Rica



\---



\## Introducción



Este documento describe las estrategias de protección y seguridad

implementadas en el simulador de la minicomputadora, en cumplimiento

con el apartado \*\*"Protección \& Seguridad (Cuál fue su estrategia)"\*\*

del enunciado del Proyecto #1.



La estrategia se basa en \*\*validación en múltiples capas\*\*:



1\. Validación en el ensamblador (antes de cargar).

2\. Validación en el gestor de memoria (al asignar).

3\. Validación en el ejecutor (al ejecutar).

4\. Validación en la configuración (al iniciar).



Ninguna capa por sí sola es suficiente, pero en conjunto garantizan

que un programa malicioso o mal escrito no pueda corromper el sistema

operativo ni afectar a otros procesos.



\---



\## 1. Aislamiento Kernel / Usuario



\### Estrategia



La memoria principal está dividida en dos zonas claramente separadas:



```

┌──────────────────────────────────────────────────┐

│  ZONA KERNEL  \[0, limiteKernelUsuario)           │

│    ├── ListaProcesos (5 posiciones)              │

│    ├── BCPs (N × 30 posiciones)                  │

│    └── TablaMemoria (15 posiciones)              │

├──────────────────────────────────────────────────┤

│  ZONA USUARIO \[limiteKernelUsuario, tamano)      │

│    └── Instrucciones de los procesos             │

└──────────────────────────────────────────────────┘

```



\### Implementación



\- `Memoria.esZonaKernel(posicion)` → `posicion < limiteKernelUsuario`.

\- El método `Memoria.reservarBloqueUsuario(tamano)` \*\*solo busca desde

&#x20; `limiteKernelUsuario`\*\* hacia arriba. Nunca asigna direcciones en la

&#x20; zona kernel.

\- El método `Memoria.liberarBloqueUsuario` \*\*solo libera posiciones de la

&#x20; zona usuario\*\*, sin tocar el kernel.



\### Resultado



Un proceso de usuario \*\*no puede escribir ni leer la zona del kernel\*\*,

porque todas sus instrucciones se cargan a partir de

`base >= limiteKernelUsuario`.



\---



\## 2. Validación del Contador de Programa (PC)



\### Estrategia



El PC de cada proceso debe estar siempre dentro del bloque asignado en

la zona de usuario: `\[base, base + alcance)`.



\### Implementación



\- Al cargar un proceso, `GestorProcesos.admitirEnParticion()` asigna

&#x20; `bcp.setPc(base)`.

\- El `EjecutorCPU`, antes de leer una instrucción, verifica que la

&#x20; posición del PC corresponda a una instrucción válida del proceso actual.

\- Si el PC sale del bloque (por ejemplo, por un `JMP` mal calculado),

&#x20; el proceso termina con estado `EXIT` y se registra el error en pantalla.



\### Resultado



Un programa malicioso \*\*no puede saltar a la zona del kernel ni a la

zona de otro proceso\*\*.



\---



\## 3. Validación de Registros



\### Estrategia



Solo se aceptan los registros definidos en el enunciado: `AC`, `AX`,

`BX`, `CX`, `DX`. Cualquier otro nombre es rechazado en la fase de

ensamblado.



\### Implementación



\- `Ensamblador.REGISTROS\_VALIDOS` es un `HashSet` con

&#x20; `{"AC", "AX", "BX", "CX", "DX"}`.

\- `Ensamblador.validarRegistro(arg)` lanza `IllegalArgumentException`

&#x20; si el registro no está en el conjunto.

\- El método `Instruccion.esRegistro(indice)` también valida los

&#x20; argumentos antes de que el `EjecutorCPU` los use.



\### Resultado



Un `.asm` con `MOV ZZ, 5` es rechazado con un mensaje claro:



```

Línea 3: registro desconocido 'ZZ'. Válidos: \[AC, AX, BX, CX, DX]

```



\---



\## 4. Protección de la Pila



\### Estrategia



Cada proceso tiene una pila de tamaño \*\*fijo de 5 posiciones\*\*. Se

detecta desbordamiento (push en pila llena) y subdesbordamiento (pop

en pila vacía).



\### Implementación



\- `BCP.TAMANO\_MAXIMO\_PILA = 5`.

\- `BCP.apilar(valor)` recorre las 5 posiciones. Si todas están ocupadas,

&#x20; lanza `IllegalStateException("Desbordamiento de pila ...")`.

\- `BCP.desapilar()` recorre de arriba hacia abajo. Si todas están vacías,

&#x20; lanza `IllegalStateException("Subdesbordamiento de pila ...")`.



\### Resultado



El `EjecutorCPU` captura la excepción, marca el proceso como `EXIT` y

registra el error en pantalla, evitando que el proceso corrompa la

memoria.



\---



\## 5. Protección de Archivos Abiertos



\### Estrategia



Cada proceso puede tener \*\*máximo 5 archivos abiertos\*\* simultáneamente.



\### Implementación



\- `BCP.TAMANO\_MAXIMO\_ARCHIVOS = 5`.

\- `BCP.abrirArchivo(nombre)`:

&#x20; 1. Verifica si el archivo ya está abierto (evita duplicados).

&#x20; 2. Busca el primer hueco libre.

&#x20; 3. Si la lista está llena, retorna `false`.

\- `BCP.cerrarArchivo(nombre)` quita el archivo de la lista.

\- El manejador de INT 21H (AH=3Dh) usa `setAl(1)` si el archivo no se

&#x20; puede abrir.



\### Resultado



Un proceso no puede agotar la lista de archivos del sistema ni abrir

el mismo archivo dos veces.



\---



\## 6. Validación de Sintaxis de Archivos `.asm`



\### Estrategia



Todo archivo `.asm` es \*\*validado antes de cargarse\*\*. Si una sola

línea es inválida, el archivo completo se rechaza y se muestran los

errores con número de línea.



\### Implementación



\- `Ensamblador.esArchivoValido(File)` recorre todas las líneas y acumula

&#x20; errores en `erroresEncontrados`.

\- Validaciones aplicadas:

&#x20; - Extensión `.asm` obligatoria.

&#x20; - Al menos una instrucción.

&#x20; - Opcode conocido (18 opcodes válidos).

&#x20; - Cantidad de argumentos correcta según el opcode.

&#x20; - Registros válidos.

&#x20; - Valores numéricos enteros.

&#x20; - Códigos de interrupción con formato `XXH` (ej. `20H`, `09H`).

\- `Ensamblador.getErroresComoTexto()` devuelve todos los errores juntos.



\### Resultado



Un archivo con un error en la línea 5 se rechaza y se muestra:



```

Línea 5: el desplazamiento 'abc' no es un numero entero valido

\-> "JMP abc"

```



\---



\## 7. Validación de Configuración Externa



\### Estrategia



Todos los parámetros leídos de `config.txt` son validados. Si un valor

está fuera de rango, se corrige automáticamente y se avisa al usuario.



\### Implementación



\- `ConfiguracionExterna.validarYCorregir()` valida:

&#x20; - `memoria` entre `MEMORIA\_MINIMO` y `MEMORIA\_MAXIMO`.

&#x20; - `kernel` al menos el 30% de `memoria` y menor que `memoria`.

&#x20; - `disco` entre `DISCO\_MINIMO` y `DISCO\_MAXIMO`.

&#x20; - `max\_archivos` entre `MAX\_ARCHIVOS\_MINIMO` y `MAX\_ARCHIVOS\_MAXIMO`.

&#x20; - `memoria\_virtual` entre `MEMORIA\_VIRTUAL\_MINIMO` y

&#x20;   `MEMORIA\_VIRTUAL\_MAXIMO`.

&#x20; - `max\_procesos` entre `MAX\_PROCESOS\_MINIMO` y `MAX\_PROCESOS\_MAXIMO`.

&#x20; - Espacio del disco: índice + swap + archivos >= mínimo.

\- Si hay correcciones, se guardan en `mensajesCorrecciones` y se muestra

&#x20; un diálogo al usuario.



\### Resultado



Un `config.txt` con `memoria=-5` se corrige a `memoria=256` (default) y

se avisa al usuario:



```

Se encontraron valores invalidos en config.txt

y se corrigieron automaticamente:

&#x20; - memoria=-5 invalido (minimo 128). Usando 256.

```



\---



\## 8. Protección contra Ciclos Infinitos



\### Estrategia



El `EjecutorCPU` tiene un límite máximo de ciclos en modo automático

para evitar que un programa con un ciclo infinito bloquee el sistema.



\### Implementación



\- `EjecutorCPU.MAX\_CICLOS\_AUTOMATICO = 10000`.

\- `ejecutarHastaTerminar()` termina el proceso con `EXIT` si supera el

&#x20; límite.

\- `GestorProcesos.ejecutarAutomatico()` tiene un límite de 100000 pasos.



\### Resultado



Un programa con un `JMP` infinito termina automáticamente y el sistema

sigue funcionando.



\---



\## 9. Protección contra Acceso a Memoria Inválida



\### Estrategia



El `Memoria.leer()` y `Memoria.escribir()` no validan índices por

defecto (para eficiencia), pero las clases que los usan validan antes.



\### Implementación



\- `Memoria.leerInstruccion(posicion)` lanza `ClassCastException` si la

&#x20; posición no contiene una `Instruccion`.

\- `Memoria.leerBCP(posicion)` lanza `ClassCastException` si la posición

&#x20; no contiene un `BCP`.

\- `Disco.leer(posicion)` valida que la posición esté dentro del disco.



\### Resultado



Un acceso incorrecto lanza una excepción controlada, no corrompe la

memoria.



\---



\## 10. Resumen de la Estrategia



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



\---



\## Conclusión



La estrategia de seguridad del simulador se basa en \*\*validación en

múltiples capas\*\*:



1\. \*\*Validación en el ensamblador\*\* (antes de cargar).

2\. \*\*Validación en el gestor de memoria\*\* (al asignar).

3\. \*\*Validación en el ejecutor\*\* (al ejecutar).

4\. \*\*Validación en la configuración\*\* (al iniciar).



Ninguna capa por sí sola es suficiente, pero en conjunto garantizan que

un programa malicioso o mal escrito no pueda corromper el sistema

operativo ni afectar a otros procesos.



\---



\## Referencias



\- Stallings, W. \*Operating Systems: Internals and Design Principles\*.

\- Silberschatz, A., Galvin, P. \*Operating System Concepts\*.

\- Enunciado del Proyecto #1 — IC-6600 Principios de Sistemas Operativos.

```



\---



