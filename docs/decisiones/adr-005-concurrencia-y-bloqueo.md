# ADR-005 — Bloqueo pesimista con orden fijo y versión optimista

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

Hay carreras reales: dos aprobadores deciden la misma tarea, una cancelación
compite con una aprobación, dos reenvíos crean revisiones a la vez y el trabajador
de ejecución compite con la compensación. El sistema tiene que producir un solo
efecto y un solo evento por transición, sin depender de esperas ni de reintentos
ciegos.

## Decisión

Se combinan tres mecanismos:

1. `@Version` en las entidades mutables para detectar escrituras concurrentes.
2. Bloqueo pesimista (`PESSIMISTIC_WRITE`) en las transiciones críticas.
3. Un orden de bloqueo fijo: **solicitud → revisión → tarea o intención → motor**.

Las restricciones únicas de PostgreSQL y las actualizaciones condicionales por
estado son la última defensa. El motor se avanza dentro de la misma transacción,
después de bloquear el negocio.

## Alternativas descartadas

- **Solo bloqueo optimista.** Se descartó porque las carreras de aprobación y
  cancelación producirían conflictos y `409` espurios que el usuario tendría que
  reintentar.
- **Aislamiento `SERIALIZABLE`.** Se descartó porque aumenta los abortos y obliga
  a una política de reintentos en toda la aplicación.
- **Bloqueos en memoria (`synchronized`, `ReentrantLock`).** Se descartaron
  porque no protegen si hay más de un nodo y no cubren la transacción de base de
  datos.

## Consecuencias

- Menos conflictos visibles para el usuario.
- Todo el código debe respetar el mismo orden de bloqueo; invertirlo puede
  producir un interbloqueo.
- Las pruebas de concurrencia usan transacciones independientes y
  `CountDownLatch`, no esperas arbitrarias.
- El bloqueo pesimista se limita a transacciones cortas; las llamadas HTTP
  quedan fuera.

## Dónde se ve

- [`ServicioTransicionRevision.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioTransicionRevision.java)
- [`ServicioDecisionAprobacion.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioDecisionAprobacion.java)
- [`ServicioIntencionEjecucion.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioIntencionEjecucion.java)
- [`SolicitudFinancieraRepositorio.java`](../../src/main/java/com/workflow/financiero/infraestructura/persistencia/SolicitudFinancieraRepositorio.java)
- [`PruebaConcurrenciaAprobacionTest.java`](../../src/test/java/com/workflow/financiero/PruebaConcurrenciaAprobacionTest.java)
- [`PruebaConcurrenciaCancelacionTest.java`](../../src/test/java/com/workflow/financiero/PruebaConcurrenciaCancelacionTest.java)
