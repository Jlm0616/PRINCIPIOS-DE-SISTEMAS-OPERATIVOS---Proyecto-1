# Mini PC - Simulador de CPU

## Autor

**Nombre:** Julian Lizano Monge  
**Carné:** 2024188887  
**Curso:** Principios de Sistemas Operativos  
**Institución:** Instituto Tecnológico de Costa Rica

---

## Tecnologías utilizadas

- Java
- Java Swing para la interfaz gráfica
- Git para el control de versiones

---

Simulador de una CPU con memoria configurable, registros y BCP (Bloque de Control de Proceso) almacenado en la RAM, desarrollado para la Tarea 1 del curso Principios de Sistemas Operativos.

El programa permite ejecutar instrucciones de un lenguaje ensamblador simple, validar archivos `.asm` y ejecutar los programas paso a paso o de forma automática.

---

## Video de demostración

[Ver video de demostración en YouTube](https://youtu.be/gQ2UIJeZxPM)

---

## ¿Qué hace el programa?

El simulador Mini PC permite:

- Cargar un archivo `.asm` con instrucciones en lenguaje ensamblador.
- Validar que el archivo tenga el formato correcto.
- Verificar que los opcodes, registros y valores sean válidos.
- Verificar que el programa pueda cargarse dentro de la memoria disponible.
- Cargar el programa en memoria, una instrucción por posición.
- Ejecutar las instrucciones paso a paso.
- Ejecutar todas las instrucciones automáticamente.
- Visualizar el estado de la CPU durante la ejecución.
- Visualizar el BCP almacenado en la RAM.
- Actualizar el BCP después de cada instrucción ejecutada.
- Traducir el contenido de la memoria entre binario y texto legible.
- Configurar el tamaño de la memoria.
- Configurar el límite entre la memoria del kernel y la memoria del usuario.
- Consultar estadísticas sobre el estado de la ejecución.

---

## ¿Cómo se usa?

### Botones principales

| Botón | Función |
|---|---|
| **Cargar archivo** | Abre un diálogo para seleccionar un archivo `.asm`. El programa valida el archivo y, si es válido, carga sus instrucciones en memoria. |
| **Modo: Automático / Paso a paso** | Permite cambiar entre los dos modos de ejecución. En modo automático el botón de ejecución permite ejecutar el programa completo. En paso a paso permite ejecutar una instrucción a la vez. |
| **Ejecutar / Siguiente** | En modo automático ejecuta el programa completo. En modo paso a paso ejecuta únicamente la siguiente instrucción. |
| **Limpiar** | Reinicia el simulador, eliminando el programa cargado y reiniciando la memoria, la CPU y el BCP. |
| **Estadísticas** | Muestra información sobre el programa, como el total de instrucciones, instrucciones ejecutadas, instrucciones restantes y estado del BCP. |
| **Ajustar memoria** | Permite modificar el tamaño de la memoria y el límite entre kernel y usuario. Esta opción solamente está disponible cuando no hay un programa cargado. |
| **Traducir / Ver binario** | Alterna la columna de valores de la memoria entre su representación binaria y una representación de texto legible. |

---

## Paneles de la interfaz

| Panel | Información mostrada |
|---|---|
| **Programa cargado** | Muestra las instrucciones del programa cargado y su representación binaria de 16 bits. |
| **Memoria** | Muestra el contenido de las posiciones de memoria, incluyendo el BCP y las instrucciones del programa. |
| **CPU / BCP** | Muestra los registros de la CPU y la información del proceso almacenada en el BCP. |
| **Barra inferior** | Muestra el progreso de la ejecución y mensajes relacionados con el estado del simulador. |

---

## Flujo típico de uso

1. Presionar **Cargar archivo**.
2. Seleccionar un archivo `.asm`.
3. Esperar a que el programa valide y cargue las instrucciones.
4. Seleccionar el modo de ejecución:
   - **Automático** para ejecutar el programa completo.
   - **Paso a paso** para ejecutar una instrucción a la vez.
5. Presionar **Ejecutar** o **Siguiente**, dependiendo del modo seleccionado.
6. Observar los cambios en los registros de la CPU y en el BCP.
7. Presionar **Traducir** para visualizar el contenido de la memoria en un formato más legible.
8. Presionar **Limpiar** para reiniciar el simulador y cargar otro programa.

---

## Formato del archivo .asm

Los programas deben estar escritos utilizando una instrucción por línea.

### Sintaxis

```text
OPCODE REGISTRO
OPCODE REGISTRO, VALOR
```

### Reglas

- Se debe utilizar una instrucción por línea.
- No se permiten comentarios.
- Las instrucciones pueden escribirse en mayúsculas o minúsculas.
- La coma después del registro es opcional cuando corresponde.
- Los registros disponibles son `AX`, `BX`, `CX` y `DX`.

---

## Opcodes soportados

| Opcode | Descripción |
|---|---|
| `LOAD` | Carga el valor de un registro en el acumulador: `AC = registro`. |
| `STORE` | Guarda el valor del acumulador en un registro: `registro = AC`. |
| `MOV` | Asigna un valor a un registro: `registro = valor`. |
| `SUB` | Resta el valor de un registro al acumulador: `AC = AC - registro`. |
| `ADD` | Suma el valor de un registro al acumulador: `AC = AC + registro`. |

---

## Registros soportados

El simulador utiliza los siguientes registros:

- `AX`
- `BX`
- `CX`
- `DX`

También se utiliza el registro:

- `AC`: Acumulador.

Además, la CPU cuenta con:

- `PC`: Contador de programa.
- `IR`: Registro de instrucción.

---

## Bandera de overflow (OF)

El simulador cuenta con una bandera de overflow (`OF`) para indicar cuando una operación `ADD` o `SUB` genera un resultado que supera el rango permitido para un valor de 16 bits con signo.

Los registros utilizan valores entre `-32768` y `32767`. Cuando el resultado supera este rango:

- El resultado se ajusta a 16 bits.
- La bandera `OF` cambia a `1`.
- La bandera permanece activa hasta reiniciar el simulador.

La bandera puede consultarse en:

- **Panel CPU / BCP:** muestra `OF: 0` o `OF: 1`. Cuando hay overflow, se muestra con un fondo rojo suave.
- **Memoria:** se almacena en la posición `8` del BCP.
- **Modo traducido:** se muestra como `OF = 1 (overflow)` o `OF = 0`.
