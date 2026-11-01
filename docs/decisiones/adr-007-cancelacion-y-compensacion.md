# ADR-007 — Cancelación orquestada con compensación durable

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

Cancelar una solicitud puede requerir liberar una reserva ya creada en el
proveedor financiero. Esa liberación es un efecto externo que puede fallar, tardar
o quedar con resultado desconocido. El cierre del alcance BPMN no puede destruir
la compensación: si el proceso termina primero, la aplicación se quedaría sin el
estado necesario para liberar.

## Decisión

La cancelación se orquesta en la aplicación, no dentro del subproceso BPMN:

1. `ServicioIntencionCancelacion` prepara el estado y persiste la intención de
   compensación en una transacción corta.
2. `ServicioCompensacion` consulta primero la confirmación y después la reserva,
   y libera fuera de transacción.
3. `IntentoCompensacion` guarda el estado durable (`PENDIENTE`, `LIBERANDO`,
   `LIBERADA`, `CANCELADA`, `RECHAZADA`, `FALLIDA`, `REVISION_MANUAL`).
4. `TrabajadorCompensacion` reintenta y, si se agota, crea un incidente.
5. El mensaje `CANCELACION_SOLICITADA` se envía recién cuando la liberación está
   confirmada. Si la confirmación de ejecución gana la carrera, la cancelación se
   rechaza y la revisión pasa a `EJECUTADA`.

Nunca se declara `CANCELADA` antes de liberar una reserva confirmada. Si la
confirmación está en tránsito o su resultado es desconocido, no se libera.

## Alternativas descartadas

- **Compensar dentro del subproceso de cancelación del BPMN.** Se descartó porque
  el subproceso interrumpe el alcance y termina; la compensación quedaría sin
  estado durable para reintentar.
- **Transacción distribuida (2PC) con el proveedor.** Se descartó porque el
  proveedor es HTTP y no participa de XA.
- **Liberación de mejor esfuerzo, sin estado durable.** Se descartó porque un
  fallo perdería la reserva y no habría forma segura de reintentar.

## Consecuencias

- La revisión puede quedar en `COMPENSANDO` mientras se resuelve la liberación.
- El proceso sigue activo hasta que la compensación termina.
- Un fallo persistente genera un incidente con reintento manual autorizado.
- La compensación y la ejecución compiten por el mismo estado; la idempotencia
  del proveedor decide el resultado.

## Dónde se ve

- [`ServicioCancelacionSolicitud.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioCancelacionSolicitud.java)
- [`ServicioCompensacion.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioCompensacion.java)
- [`IntentoCompensacion.java`](../../src/main/java/com/workflow/financiero/dominio/modelo/IntentoCompensacion.java)
- [`TrabajadorCompensacion.java`](../../src/main/java/com/workflow/financiero/infraestructura/ejecucion/TrabajadorCompensacion.java)
- [`V10__intento_compensacion.sql`](../../src/main/resources/db/migration/V10__intento_compensacion.sql)
- [`PruebaCancelacionCompensacionTest.java`](../../src/test/java/com/workflow/financiero/PruebaCancelacionCompensacionTest.java)
