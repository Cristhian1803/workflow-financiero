# ADR-003 — Flowable embebido con transacción compartida

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

El proceso de aprobación tiene tareas humanas, compuertas inclusivas,
temporizadores, una espera por mensaje de ejecución y un subproceso de
cancelación. El estado de negocio (`revision_solicitud`) y el estado del motor
(`act_*`) deben avanzar juntos: si se registra una aprobación pero el proceso no
avanza, la solicitud queda inconsistente.

## Decisión

Flowable 7.2.0 se ejecuta embebido en la aplicación. `ConfiguracionMotorFlowable`
le entrega el mismo `DataSource` y el mismo `PlatformTransactionManager` que usa
JPA. El delegate de BPMN y el servicio de aplicación participan de la misma
transacción: la decisión, el evento, la tarea de negocio y el avance del motor
hacen commit o rollback juntos.

Flowable crea y administra sus propias tablas `act_*` con
`database-schema-update: true`; Flyway solo versiona las tablas de negocio.

## Alternativas descartadas

- **Motor externo consumido por REST.** Se descartó porque separa la transacción
  del negocio: habría que coordinar dos commits y compensar si uno falla.
- **Motor embebido con transacción separada y un outbox hacia el proceso.** Se
  descartó porque agrega una bandeja de salida y latencia sin resolver nada que
  la transacción compartida no resuelva en este despliegue.

## Consecuencias

- Un rollback de negocio también revierte el avance del motor.
- Actualizar Flowable exige probar el despliegue y las migraciones del motor.
- Las instancias en vuelo no se migran al cambiar la versión del proceso.
- El esquema del motor no se versiona con Flyway; se crea con la configuración de
  Flowable.

## Dónde se ve

- [`ConfiguracionMotorFlowable.java`](../../src/main/java/com/workflow/financiero/infraestructura/bpmn/ConfiguracionMotorFlowable.java)
- [`application.yml`](../../src/main/resources/application.yml)
- [`AdaptadorProcesoFlowable.java`](../../src/main/java/com/workflow/financiero/infraestructura/bpmn/AdaptadorProcesoFlowable.java)
- [`PruebaTransaccionMotorTest.java`](../../src/test/java/com/workflow/financiero/PruebaTransaccionMotorTest.java)
- [`docs/bpmn/README.md`](../bpmn/README.md)
