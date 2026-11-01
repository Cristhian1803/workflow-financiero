# Limitaciones conocidas

Este documento reúne lo que el sistema **no** hace o hace con una restricción
deliberada. Cada punto indica el motivo y el siguiente paso razonable. No es una
lista de defectos: es el alcance real de la entrega.

## Integraciones

### Los proveedores financiero y de riesgo son simulados

El sistema no mueve dinero ni consulta un buró real. El proveedor financiero
persiste reservas, confirmaciones y liberaciones en `operacion_simulada`, y el de
riesgo responde con un evento firmado.

- **Motivo:** la prueba no debe operar con fondos reales ni depender de un
  tercero.
- **Siguiente paso:** implementar los adaptadores reales sobre los mismos puertos
  y agregar credenciales por ambiente.

### No hay `Idempotency-Key` en la API

La identidad financiera durable es `intento_ejecucion`, con clave y huella
estables. La API de creación no acepta una cabecera de idempotencia por usuario.

- **Motivo:** el requisito central es que el efecto financiero no se duplique; la
  creación de una solicitud repetida no genera un efecto externo.
- **Siguiente paso:** agregar un filtro por usuario y operación si la API se
  expone a terceros.

### Los trabajadores usan polling y no se probaron varios nodos

`TrabajadorEjecucion`, `TrabajadorCompensacion` y `TrabajadorRiesgoExterno` se
ejecutan por intervalo. La reclamación usa `FOR UPDATE SKIP LOCKED`, pero la
prueba se hizo con un solo proceso.

- **Motivo:** el despliegue previsto es una sola instancia.
- **Siguiente paso:** medir con dos instancias y verificar que el intervalo y la
  reclamación vencida sigan siendo suficientes.

## Seguridad

### JWT sin refresh ni revocación inmediata

El token se emite con expiración configurable y no hay endpoint de refresh ni
lista de revocación.

- **Motivo:** el alcance no incluye un proveedor de identidad ni sesiones.
- **Siguiente paso:** agregar refresh token o una lista de revocación por
  identificador de token.

### Los roles de aprobación están fijos en el BPMN

Las tareas humanas declaran `candidateGroups` con `SUPERVISOR`, `GERENTE`,
`RIESGOS`, `ANALISTA_RIESGOS` y `JEFE_RIESGOS`. Las políticas de aprobación sí
son extensibles, pero los grupos del proceso no se leen de una tabla.

- **Motivo:** el XML debe ser explícito y el catálogo de roles es estable.
- **Siguiente paso:** pasar los grupos como variables de proceso o desde el plan
  de aprobación.

### Las notificaciones solo se persisten

`GET /api/v1/notificaciones` devuelve las notificaciones del rol. No hay envío
por correo ni estado de “leída”.

- **Motivo:** el requisito pide dejar constancia, no un canal externo.
- **Siguiente paso:** agregar un estado de lectura y un adaptador de correo.

## Modelo

### Una sola moneda y montos enteros

`ValidadorMoneda` acepta únicamente `PYG`. Los montos usan `NUMERIC(19,0)` y
`BigDecimal` sin decimales.

- **Motivo:** es la regla de negocio vigente.
- **Siguiente paso:** catálogo multimoneda, tipo de cambio y una escala por
  moneda.

### La evaluación externa y la revisión manual no cambian el plan por encima de 200 millones

`PoliticaAprobacionPorMonto` ya incluye `RIESGOS` para montos superiores a
200.000.000, y `PoliticaAprobacionPorRiesgo` solo agrega ese mismo rol cuando el
nivel es `ALTO`. Por eso, en ese tramo, un riesgo alto o una revisión manual no
suman aprobaciones nuevas.

- **Motivo:** la política vigente es la del proyecto.
- **Siguiente paso:** si se agregan roles por riesgo, revisar la consolidación y
  el recálculo del plan.

### La reasignación elige el primer superior activo por usuario

`ServicioReasignacionTarea` toma el primer usuario activo del rol superior,
ordenado por nombre de usuario.

- **Motivo:** es una regla simple y determinista.
- **Siguiente paso:** jerarquía explícita, carga de trabajo o round-robin.

## Proceso

### Las instancias BPMN en vuelo no se migran

Un cambio en el XML crea una versión nueva. Las instancias existentes siguen en
la versión con la que se iniciaron.

- **Motivo:** es el comportamiento de Flowable y evita migraciones implícitas.
- **Siguiente paso:** definir una migración explícita con prueba propia.

### El endpoint del spike se conserva

`ControladorSolicitudPrueba` sigue en el código como demostración no productiva y
está oculto de OpenAPI. Solo existe en los perfiles `desarrollo` y `pruebas`.

- **Motivo:** se conserva para mostrar el circuito mínimo del motor.
- **Siguiente paso:** retirarlo en una limpieza posterior.

## Operación

### La observabilidad se limita a salud y correlación

Actuator expone `health` e `info`. Los logs incluyen el identificador de
correlación, pero no hay métricas de negocio ni trazas distribuidas.

- **Motivo:** el alcance pide salud básica.
- **Siguiente paso:** métricas de ejecución, compensación e incidentes, y trazas
  si se agrega un colector.

### Los scripts están orientados a Windows

Los comandos documentados usan PowerShell y `mvnw.cmd`.

- **Motivo:** es el entorno de desarrollo y demostración.
- **Siguiente paso:** agregar equivalentes para otros sistemas.

## Verificación

### La separación de capas no está verificada por ArchUnit

ArchUnit está declarado en `pom.xml`, pero no hay reglas activas. La separación
entre `dominio`, `aplicacion`, `api` e `infraestructura` se sostiene por
convención y revisión.

- **Motivo:** no se agregaron reglas para no ampliar el alcance de esta entrega.
- **Siguiente paso:** reglas que prohíban a `dominio` depender de otras capas y a
  `api` depender de la persistencia.

### Las pruebas manuales no forman parte de la suite

Las verificaciones con base local se ejecutan a mano. Los recorridos están en
[`docs/escenarios.md`](escenarios.md) y la evidencia de las pruebas manuales en
[`docs/pruebas.md`](pruebas.md). Las limitaciones propias de la suite (Docker,
Testcontainers, adaptadores de prueba) están en ese mismo documento.

- **Motivo:** requieren una base local y tiempos controlados.
- **Siguiente paso:** convertir los escenarios principales en pruebas
  automatizadas de extremo a extremo.
