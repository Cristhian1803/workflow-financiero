# ADR-012 — Pruebas con PostgreSQL y Flowable reales

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

El sistema depende de bloqueos de PostgreSQL, restricciones únicas, transacciones
compartidas con Flowable y comportamiento real del motor BPMN. Una prueba con
base en memoria o con el motor simulado no demostraría esos comportamientos.

## Decisión

La suite de integración usa PostgreSQL 16 real en Testcontainers y Flowable real:

- Un contenedor compartido por ejecución (`PruebaIntegracionBase`) y limpieza
  determinista con `TRUNCATE` de las tablas de negocio.
- Pruebas concurrentes con transacciones independientes y `CountDownLatch`.
- Temporizadores ejecutados con `ManagementService`, sin esperas reales.
- WireMock para el contrato HTTP del proveedor financiero.
- Sin reintentos de Surefire: un fallo no se oculta.

Las reglas de negocio puras se prueban sin Spring. Las pruebas de API usan
`MockMvc` con la seguridad real.

## Alternativas descartadas

- **H2 en modo PostgreSQL.** Se descartó porque no reproduce los bloqueos,
  `SKIP LOCKED`, los tipos ni las restricciones de PostgreSQL.
- **Motor BPMN simulado.** Se descartó porque no probaría compuertas,
  temporizadores, mensajes ni el subproceso de cancelación reales.
- **Cobertura como criterio principal.** Se descartó porque un porcentaje no
  demuestra un efecto observable ni una carrera.

## Consecuencias

- La suite tarda alrededor de cuatro minutos y requiere Docker.
- Los resultados son reproducibles y cubren efectos, no solo respuestas HTTP.
- Las pruebas manuales complementan la suite, pero no la reemplazan.
- La evidencia y la matriz de escenarios están en [`docs/pruebas.md`](../pruebas.md).

## Dónde se ve

- [`PruebaIntegracionBase.java`](../../src/test/java/com/workflow/financiero/PruebaIntegracionBase.java)
- [`PruebaProcesoAprobacionBase.java`](../../src/test/java/com/workflow/financiero/PruebaProcesoAprobacionBase.java)
- [`PruebaConcurrenciaAprobacionTest.java`](../../src/test/java/com/workflow/financiero/PruebaConcurrenciaAprobacionTest.java)
- [`PruebaTransaccionMotorTest.java`](../../src/test/java/com/workflow/financiero/PruebaTransaccionMotorTest.java)
- [`docs/pruebas.md`](../pruebas.md)
