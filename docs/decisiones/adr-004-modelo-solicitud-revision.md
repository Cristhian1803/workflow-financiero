# ADR-004 — Modelo de solicitud con revisiones sucesivas

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

Una solicitud puede rechazarse y reenviarse. El reenvío no puede borrar la
revisión anterior: hay decisiones, eventos y un proceso asociados que deben
quedar auditables. Además, el estado de negocio no puede duplicarse en dos
lugares sin una regla clara de cuál manda.

## Decisión

El modelo separa dos entidades:

- `SolicitudFinanciera` es el agregado estable. Guarda el solicitante, la
  descripción y un puntero a la revisión vigente (`solifina_revisoli_actual_id`).
- `RevisionSolicitud` es la unidad de trabajo. Tiene número, monto, moneda, tipo
  de operación, estado e identificador del proceso. Su estado es el único estado
  de negocio.

El historial vive en `evento_solicitud`, que es de solo adición. El reenvío crea
una revisión nueva con número siguiente y conserva la anterior y su historial.

## Alternativas descartadas

- **Un solo estado en la solicitud, con una tabla de historial de cambios.** Se
  descartó porque el reenvío necesita conservar la revisión anterior completa,
  no solo su estado: sus tareas, decisiones, evaluación de riesgo y proceso
  siguen siendo consultables.
- **Una tabla por estado o un histórico de estados como fuente principal.** Se
  descartó porque complica las consultas y no reemplaza la revisión como unidad
  transaccional.

## Consecuencias

- El estado se consulta siempre en `revision_solicitud`.
- Hay unicidad por solicitud y número de revisión.
- Las consultas de historial y de proceso se hacen por revisión.
- La solicitud puede tener revisiones terminales anteriores sin afectar a la
  vigente.

## Dónde se ve

- [`SolicitudFinanciera.java`](../../src/main/java/com/workflow/financiero/dominio/modelo/SolicitudFinanciera.java)
- [`RevisionSolicitud.java`](../../src/main/java/com/workflow/financiero/dominio/modelo/RevisionSolicitud.java)
- [`V3__solicitud_revision_auditoria.sql`](../../src/main/resources/db/migration/V3__solicitud_revision_auditoria.sql)
- [`ServicioReenvioSolicitud.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioReenvioSolicitud.java)
- [`PruebaRechazoReenvioTest.java`](../../src/test/java/com/workflow/financiero/PruebaRechazoReenvioTest.java)
