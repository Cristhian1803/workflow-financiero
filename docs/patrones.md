# Patrones de diseño aplicados

Este documento reúne los patrones que están implementados en el código, no los
que podrían aplicarse. Cada uno indica el problema que resuelve, las clases que
participan, la alternativa que se descartó y la prueba que lo demuestra.

Los patrones de integración (intención durable, inbox, receptor idempotente,
reclamación con `SKIP LOCKED`) se describen en
[`docs/arquitectura.md`](arquitectura.md), porque son mecanismos de comunicación
y no patrones de diseño clásicos.

## 1. Strategy — políticas de aprobación

### Problema

Los roles de aprobación dependen del monto y del nivel de riesgo. La regla debe
poder crecer sin reescribir el cálculo del plan ni duplicar condiciones.

### Solución y participantes

- `PoliticaAprobacion` es el contrato: recibe un `ContextoAprobacion` y devuelve
  un conjunto de códigos de rol.
- `PoliticaAprobacionPorMonto` aporta los tramos por monto.
- `PoliticaAprobacionPorRiesgo` aporta `RIESGOS` cuando el nivel es `ALTO`.
- `CalculadoraPlanAprobacion` inyecta todas las implementaciones, une sus
  resultados, elimina duplicados y valida que los roles existan y sean
  aprobadores activos.

La calculadora no conoce las reglas concretas: solo itera la lista de políticas.
Agregar una política nueva es agregar un bean, no editar la calculadora.

### Por qué se eligió

- Cada regla se prueba por separado.
- El plan se compone por unión, que es exactamente la semántica del negocio: si
  dos reglas piden el mismo rol, se pide una vez.
- El catálogo de roles se valida en un solo lugar.

### Alternativas descartadas

- **Una cadena de `if`/`else` en la calculadora.** Se descartó porque mezcla
  reglas, dificulta las pruebas por tramo y obliga a editar el mismo método cada
  vez que cambia una política.
- **Chain of Responsibility.** Se descartó porque la cadena corta en el primer
  eslabón que responde y aquí hace falta acumular todos los roles.

### Dónde está

- [`PoliticaAprobacion.java`](../src/main/java/com/workflow/financiero/dominio/regla/aprobacion/PoliticaAprobacion.java)
- [`PoliticaAprobacionPorMonto.java`](../src/main/java/com/workflow/financiero/dominio/regla/aprobacion/PoliticaAprobacionPorMonto.java)
- [`PoliticaAprobacionPorRiesgo.java`](../src/main/java/com/workflow/financiero/dominio/regla/aprobacion/PoliticaAprobacionPorRiesgo.java)
- [`CalculadoraPlanAprobacion.java`](../src/main/java/com/workflow/financiero/aplicacion/servicio/CalculadoraPlanAprobacion.java)

### Prueba

[`PruebaPoliticasAprobacionTest.java`](../src/test/java/com/workflow/financiero/PruebaPoliticasAprobacionTest.java)
cubre los bordes de monto, las combinaciones de riesgo y la composición del plan.

## 2. Composite — validación de solicitudes

### Problema

Una solicitud se valida por varios criterios independientes (monto, moneda,
descripción, justificación, tipo de operación y perfil del solicitante). El
cliente necesita recibir **todos** los errores juntos, no solo el primero.

### Solución y participantes

- `ValidadorSolicitud` es el componente común.
- `ValidadorSolicitudCompuesto` es el compuesto: recorre una lista de validadores
  y acumula sus `ErrorCampo`.
- `ValidadorMonto`, `ValidadorMoneda`, `ValidadorDescripcion`,
  `ValidadorJustificacion`, `ValidadorTipoOperacion` y
  `ValidadorPerfilSolicitante` son las hojas.

El compuesto trata a las hojas y a otros compuestos de la misma forma, así que
se puede anidar sin cambiar el contrato.

### Por qué se eligió

- El usuario recibe el conjunto completo de errores en una sola respuesta.
- Cada validador es pequeño y se prueba de forma aislada.
- El orden de los validadores es explícito y estable.

### Alternativas descartadas

- **Chain of Responsibility.** Se descartó porque una cadena típica corta en el
  primer error o exige continuar manualmente; el compuesto acumula por diseño.
- **Un único validador con muchos `if`.** Se descartó porque concentra reglas
  distintas y hace difícil saber qué se rompió cuando falla una.

### Dónde está

- [`ValidadorSolicitud.java`](../src/main/java/com/workflow/financiero/dominio/regla/validacion/ValidadorSolicitud.java)
- [`ValidadorSolicitudCompuesto.java`](../src/main/java/com/workflow/financiero/dominio/regla/validacion/ValidadorSolicitudCompuesto.java)
- [`ServicioValidacionSolicitud.java`](../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioValidacionSolicitud.java)

### Prueba

[`PruebaValidacionesTest.java`](../src/test/java/com/workflow/financiero/PruebaValidacionesTest.java)
verifica errores individuales, acumulación y normalización de códigos.

## 3. State — ciclo de vida de la revisión

### Problema

Una revisión pasa por doce estados y solo algunas transiciones son válidas. Una
transición ilegal debe fallar antes de persistirse, y el estado terminal no puede
volver a moverse.

### Solución y participantes

- `EstadoRevision` es una enumeración con `puedeTransicionarA` y `esTerminal`. La
  tabla de transiciones vive en el propio enum.
- `RevisionSolicitud.cambiarEstado` aplica la transición y rechaza la inválida.
- `ServicioTransicionRevision` bloquea solicitud y revisión, cambia el estado y
  registra el evento en la misma transacción.

No hay una clase por estado: el comportamiento de cada estado es una fila de la
tabla de transiciones. Es una máquina de estados por enumeración, no el patrón
State de GoF con polimorfismo.

### Por qué se eligió

- Las transiciones válidas se leen en un solo lugar.
- La prueba unitaria no necesita Spring ni base de datos.
- El servicio no duplica la tabla: delega en el enum.

### Alternativas descartadas

- **State de GoF con una clase por estado.** Se descartó porque doce clases con
  un método cada una no aportan comportamiento propio; la diferencia entre
  estados es la tabla de transiciones.
- **Estados como cadenas libres.** Se descartó porque permitiría valores
  inválidos en la base y trasladaría el error a la consulta.

### Dónde está

- [`EstadoRevision.java`](../src/main/java/com/workflow/financiero/dominio/estado/EstadoRevision.java)
- [`RevisionSolicitud.java`](../src/main/java/com/workflow/financiero/dominio/modelo/RevisionSolicitud.java)
- [`ServicioTransicionRevision.java`](../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioTransicionRevision.java)

### Prueba

[`PruebaEstadosTest.java`](../src/test/java/com/workflow/financiero/PruebaEstadosTest.java)
recorre transiciones válidas e inválidas y comprueba los estados terminales.

## 4. Adapter y puertos — motor, proveedor y riesgo

### Problema

La capa de aplicación necesita iniciar procesos, completar tareas, reservar y
confirmar operaciones y solicitar una evaluación de riesgo. No debe depender de
la API de Flowable, de `RestClient` ni del simulador concreto.

### Solución y participantes

- `PuertoProceso` define las operaciones del motor; `AdaptadorProcesoFlowable`
  las implementa con Flowable.
- `PuertoProveedorFinanciero` define reservar, confirmar, consultar y liberar;
  `AdaptadorProveedorFinancieroHttp` lo implementa con HTTP.
- `PuertoProveedorRiesgo` define el envío de la evaluación;
  `ProveedorRiesgoSimulado` lo implementa.
- `PuertoCancelacionProceso` separa el cierre del proceso de la compensación.

Los dobles de prueba implementan los mismos puertos, así que la aplicación se
prueba sin levantar un servidor.

### Por qué se eligió

- Cambiar el proveedor o el motor no obliga a tocar los casos de uso.
- Las pruebas pueden usar un adaptador directo y, a la vez, probar el contrato
  HTTP con WireMock.
- La frontera transaccional queda explícita: el puerto no sabe de transacciones.

### Alternativas descartadas

- **Usar Flowable y `RestClient` directamente en los servicios.** Se descartó
  porque acopla la aplicación a dos tecnologías y hace las pruebas más pesadas.
- **Una capa genérica de integraciones.** Se descartó porque no hay suficientes
  integraciones para justificarla.

### Dónde está

- [`PuertoProceso.java`](../src/main/java/com/workflow/financiero/aplicacion/puerto/PuertoProceso.java)
- [`AdaptadorProcesoFlowable.java`](../src/main/java/com/workflow/financiero/infraestructura/bpmn/AdaptadorProcesoFlowable.java)
- [`PuertoProveedorFinanciero.java`](../src/main/java/com/workflow/financiero/aplicacion/puerto/PuertoProveedorFinanciero.java)
- [`AdaptadorProveedorFinancieroHttp.java`](../src/main/java/com/workflow/financiero/infraestructura/externo/AdaptadorProveedorFinancieroHttp.java)
- [`PuertoProveedorRiesgo.java`](../src/main/java/com/workflow/financiero/aplicacion/puerto/PuertoProveedorRiesgo.java)
- [`ProveedorRiesgoSimulado.java`](../src/main/java/com/workflow/financiero/infraestructura/externo/ProveedorRiesgoSimulado.java)

### Prueba

[`PruebaAdaptadorProveedorFinancieroTest.java`](../src/test/java/com/workflow/financiero/PruebaAdaptadorProveedorFinancieroTest.java)
prueba el contrato HTTP; [`PruebaProcesoPrincipalTest.java`](../src/test/java/com/workflow/financiero/PruebaProcesoPrincipalTest.java)
comprueba el proceso real; [`PruebaContratoProveedorTest.java`](../src/test/java/com/workflow/financiero/PruebaContratoProveedorTest.java)
cubre la idempotencia del simulador.

## 5. Saga orquestada — cancelación y compensación

### Problema

Cancelar después de reservar exige un efecto externo (liberar) que puede fallar.
No hay transacción distribuida y el proceso BPMN no puede conservar la
compensación cuando su alcance termina.

### Solución y participantes

- `ServicioCancelacionSolicitud` es el orquestador: prepara, ejecuta una pasada de
  compensación y devuelve el estado seguro.
- `ServicioIntencionCancelacion` persiste la intención y el estado de la revisión.
- `ServicioCompensacion` consulta la confirmación y la reserva, y libera fuera de
  transacción.
- `IntentoCompensacion` es el registro durable de cada paso.
- `TrabajadorCompensacion` reintenta y crea un incidente cuando se agota.

Cada paso es idempotente y consultable; si el sistema se reinicia, el trabajador
retoma desde el estado persistido.

### Por qué se eligió

- No se declara `CANCELADA` sin liberar.
- La compensación sobrevive a un reinicio y a un cierre del proceso.
- La carrera contra la confirmación se resuelve con la idempotencia del
  proveedor.

### Alternativas descartadas

- **Compensar dentro del subproceso BPMN.** Se descartó porque el subproceso
  interrumpe y termina; no queda estado para reintentar.
- **2PC con el proveedor.** Se descartó porque el proveedor es HTTP.
- **Liberación de mejor esfuerzo.** Se descartó porque un fallo perdería la
  reserva sin rastro.

### Dónde está

- [`ServicioCancelacionSolicitud.java`](../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioCancelacionSolicitud.java)
- [`ServicioCompensacion.java`](../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioCompensacion.java)
- [`IntentoCompensacion.java`](../src/main/java/com/workflow/financiero/dominio/modelo/IntentoCompensacion.java)
- [`TrabajadorCompensacion.java`](../src/main/java/com/workflow/financiero/infraestructura/ejecucion/TrabajadorCompensacion.java)

### Prueba

[`PruebaCancelacionCompensacionTest.java`](../src/test/java/com/workflow/financiero/PruebaCancelacionCompensacionTest.java)
cubre liberación, confirmación en tránsito, respuesta perdida y reintento;
[`PruebaConcurrenciaCancelacionTest.java`](../src/test/java/com/workflow/financiero/PruebaConcurrenciaCancelacionTest.java)
cubre la carrera con la aprobación y el despacho.

## 6. Patrones que no se usan

La ausencia también es una decisión. Estos patrones se evaluaron y se descartaron:

| Patrón | Por qué no se usa |
|---|---|
| Observer / Domain Events | `evento_solicitud` es auditoría persistida, no un bus interno. No hay suscriptores ni publicación en memoria. |
| Factory Method / Abstract Factory | No hay creación polimórfica por tipo de operación. Las entidades y los comandos se construyen de forma directa. |
| Chain of Responsibility | La validación necesita acumular todos los errores; se eligió Composite. |
| State de GoF con clases | El estado se modela con una enumeración y una tabla de transiciones; no hay una clase por estado. |
| Repository escrito a mano | Se usa Spring Data JPA. No hay una interfaz de repositorio de dominio separada de la persistencia. |
| Outbox genérico | La intención durable y el inbox cubren los dos casos concretos; no se agregó una bandeja de salida general. |

## 7. Cómo se mantiene este documento

Un patrón se agrega solo si existe una clase concreta que lo implementa y una
prueba que lo demuestra. Si una clase cambia de nombre o desaparece, se actualiza
la referencia en el mismo cambio.
