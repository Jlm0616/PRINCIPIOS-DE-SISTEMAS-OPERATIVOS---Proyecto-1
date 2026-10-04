# Proyecto #1 — Gestor de Procesos — IC-6600

## Autor

- **Nombre:** Julian Lizano Monge
- **Carné:** 2024188887
- **Curso:** Principios de Sistemas Operativos (IC-6600)
- **Profesor:** Ing. Cristian Campos Agüero
- **Centro Académico:** Limón
- **Institución:** Instituto Tecnológico de Costa Rica

---

## Tecnologías utilizadas

- **Java** (JDK 8+)
- **Java Swing** para la interfaz gráfica
- **NetBeans** como IDE
- **Git / GitHub** para control de versiones

---

## Descripción

Simulador de una **minicomputadora** con:

- **1 CPU** con registros AC, AX, BX, CX, DX, IR, PC y banderas OF/EQ
- **Memoria principal configurable** (por defecto 256 posiciones)
- **Disco configurable** (por defecto 512 posiciones) con 3 zonas:
  - Índice de archivos
  - Memoria virtual (swap, por defecto 64)
  - Archivos (.asm)
- **BCP (Bloque de Control de Proceso)** de 30 posiciones en el kernel
- **Planificador FCFS** con despachador y cambio de contexto
- **Hasta 5 procesos** (los que no caben en RAM esperan en la ListaDeTrabajos)
- **Mini ensamblador** con 18 instrucciones (todas las del enunciado)
- **Interrupciones** INT 10H, 09H, 20H y 21H

---

## Video de demostración

[Ver video de demostración en YouTube](https://youtu.be/TU_VIDEO_AQUI)

> **Nota:** reemplaza el enlace con el del video del Proyecto 1.

---

## Objetivos alcanzados

- [x] Carga y validación de archivos `.asm` (uno o varios a la vez)
- [x] BCP completo (30 posiciones) en memoria kernel
- [x] 7 estados de proceso (NEW, READY, RUNNING, BLOCKED, EXIT, READY_SUSPEND, BLOCKED_SUSPEND)
- [x] Pila de 5 posiciones con detección de desbordamiento
- [x] Lista de archivos abiertos (5 por proceso)
- [x] Enlace al siguiente BCP
- [x] Base, Alcance, Prioridad
- [x] CPU con registros AC, AX, BX, CX, DX, IR, PC y banderas OF/EQ
- [x] Ejecución de las 18 instrucciones del enunciado con sus pesos
- [x] Planificador FCFS
- [x] Despachador y cambio de contexto
- [x] Memoria principal configurable (default 256)
- [x] Disco configurable (default 512) con índice + swap (default 64) + archivos
- [x] Memoria virtual (swap) para procesos suspendidos
- [x] Particionamiento dinámico first-fit en zona de usuario
- [x] Interrupciones: INT 10H (pantalla), INT 09H (teclado), INT 20H (fin), INT 21H (archivos)
- [x] Manejo de archivos INT 21H: crear, abrir, leer, escribir, eliminar
- [x] GUI con paneles de memoria, disco, BCP, registros, pantalla y consola
- [x] Modo paso a paso (1 segundo por clic)
- [x] Modo automático con SwingWorker
- [x] Estadísticas de procesos terminados
- [x] Configuración externa en `config.txt` (no en código)
- [x] Protección y seguridad

## Objetivos no alcanzados

- Ninguno. Todas las funcionalidades del enunciado fueron implementadas.

---

## ¿Cómo se usa?

### 1. Configuración inicial

La aplicación lee los parámetros de `config.txt` en la raíz del proyecto:

```properties
memoria=256
kernel=77
disco=512
max_archivos=10
memoria_virtual=64
max_procesos=5
```

**Defaults del profesor:**
- Memoria principal: **256**
- Disco: **512**
- Memoria virtual: **64**
- Máximo de procesos: **5**
- Kernel: **30% de la memoria** (77 con default 256)

Si no existe el archivo, se crea automáticamente con estos valores.

### 2. Cargar programas

Presionar **Cargar archivos** y seleccionar uno o varios `.asm`.

Si el archivo no cabe en RAM, el proceso pasa a la **ListaDeTrabajos (disco)** y espera.

### 3. Ejecutar

- **Paso a paso:** cada clic en "Paso a paso" = 1 segundo de CPU.
- **Ejecutar:** modo automático hasta que todos los procesos terminen.
- **Limpiar:** reinicia el sistema con la configuración actual.
- **Configurar:** abre el diálogo para cambiar memoria, kernel, disco, swap.
- **Estadísticas:** muestra el resumen de procesos terminados.

---

## Paneles de la GUI

| Panel | Información mostrada |
|---|---|
| **Procesos** | Lista de todos los procesos (RAM + disco) con su estado, y BCP actual |
| **Memoria** | Contenido de la memoria principal (kernel + usuario) |
| **Disco** | 3 pestañas: índice de archivos, swap y archivos |
| **Pantalla** | Salida de INT 10H |
| **Consola** | Entrada de INT 09H (valores 0-255) |
| **Recursos** | Uso de memoria y disco en barras de progreso |

---

## Formato de archivos `.asm`

Una instrucción por línea. Ejemplo:

```asm
MOV AX, 10
MOV BX, 20
MOV CX, 30
LOAD AX
ADD BX
SUB CX
CMP AX, BX
JNE +1
JMP +4
STORE CX
CMP AX, CX
JE +2
MOV DX, 99
INT 10H
INT 20H
```

### Reglas

- Una instrucción por línea.
- Sin comentarios.
- Mayúsculas o minúsculas.
- Registros válidos: `AC`, `AX`, `BX`, `CX`, `DX`.

---

## Instrucciones soportadas

| Instrucción | Descripción | Peso |
|---|---|---|
| `LOAD AX` | Carga el valor al AC | 2 |
| `STORE BX` | Almacena el valor del AC a un registro destino | 2 |
| `MOV BX, AX` | Movimiento entre registros | 1 |
| `MOV BX, 5` | Movimiento de un valor a un registro | 1 |
| `ADD BX` | Suma al AC el valor del BX | 3 |
| `SUB BX` | Resta al AC el valor del BX | 3 |
| `INC` / `INC AX` | Incrementa en 1 el AC o un registro | 1 |
| `DEC` / `DEC AX` | Decrementa en 1 el AC o un registro | 1 |
| `SWAP AX, BX` | Intercambia valores entre registros | 1 |
| `INT 20H` | Finaliza el programa | 2 |
| `INT 10H` | Imprime en pantalla el valor del DX | 2 |
| `INT 09H` | Entrada de teclado (0-255), se guarda en DX | 3 |
| `INT 21H` | Manejo de archivos (AH: 3Ch/3Dh/4Dh/40h/41h) | 5 |
| `JMP [+/-Desplazamiento]` | Salta a la instrucción | 2 |
| `CMP Reg1, Reg2` | Compara dos registros | 2 |
| `JE` / `JNE [+/-Desplazamiento]` | Salta si es igual / no igual | 2 |
| `PARAM v1, v2, .. vN` | Parámetros de entrada (máx 3) a la pila | 3 |
| `PUSH AX` | Guarda en la pila el valor del registro | 1 |
| `POP AX` | Saca de la pila a un registro | 1 |

---

## Estructura del proyecto

```
src/
├── App/
│   └── Main.java
├── config/
│   └── ConfiguracionExterna.java
├── gui/
│   ├── Paleta.java
│   ├── PanelConfigDisco.java
│   ├── PanelConfigMemoria.java
│   ├── PanelDisco.java
│   ├── PanelMemoria.java
│   ├── PanelPantalla.java
│   ├── PanelProcesos.java
│   ├── PanelRecursos.java
│   ├── VentanaConfiguracion.java
│   ├── VentanaEstadisticas.java
│   └── VentanaPrincipal.java
├── logica/
│   ├── BCPTerminado.java
│   ├── Despachador.java
│   ├── EjecutorCPU.java
│   ├── Ensamblador.java
│   ├── EstrategiaPlanificacion.java (interfaz)
│   ├── GestorProcesos.java
│   ├── Interrupciones.java
│   ├── ListaDeTrabajos.java
│   ├── ListaProcesos.java
│   ├── ParticionadorDinamico.java
│   ├── Planificador.java
│   ├── ProcesoEnEspera.java
│   ├── ResultadoCarga.java
│   └── planificacion/
│       └── PlanificadorFCFS.java
└── modelo/
    ├── BCP.java
    ├── CPU.java
    ├── Disco.java
    ├── EstadoProceso.java
    ├── Instruccion.java
    └── Memoria.java
```

---

## Seguridad implementada

Ver [SEGURIDAD.md](SEGURIDAD.md) para el detalle completo. Resumen:

1. **Aislamiento kernel/usuario:** la memoria está dividida en dos zonas. El proceso no puede escribir en zona kernel.
2. **Validación de PC:** el PC debe estar dentro del bloque del proceso.
3. **Validación de registros:** el ensamblador rechaza registros desconocidos.
4. **Protección de pila:** tamaño máximo 5, con detección de desbordamiento.
5. **Protección de archivos abiertos:** máximo 5 por proceso.
6. **Validación de sintaxis:** todos los `.asm` se validan antes de cargar.
7. **Validación de configuración:** valores fuera de rango se corrigen automáticamente.

---

## Diagrama de paquetes

Ver [diagrama_paquetes.pdf](diagrama_paquetes.pdf) para el diagrama UML de paquetes y la explicación del diseño del SO.

---

## Notas sobre configuración

- **Con memoria=256 y kernel=77 (30%), caben 1 BCP en RAM.** Los procesos sobrantes esperan en la ListaDeTrabajos (disco), según indica el enunciado.
- **Para 2 BCPs**, poner `kernel=100` en `config.txt`.
- **Para 3 BCPs**, poner `kernel=110`.
- **Para 5 BCPs**, poner `kernel=170` (66% de 256) o aumentar la memoria a 600.
- Los valores se **persisten** entre sesiones (se guardan en `config.txt`).
- El usuario puede cambiarlos desde el botón **Configurar** o editando `config.txt` directamente.

---

## Repositorio

[GitHub — PRINCIPIOS-DE-SISTEMAS-OPERATIVOS---Proyecto-1](https://github.com/Jlm0616/PRINCIPIOS-DE-SISTEMAS-OPERATIVOS---Proyecto-1)
```

---