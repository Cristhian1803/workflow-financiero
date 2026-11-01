# ADR-010 — Esquema versionado con Flyway y prefijos

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

El esquema tiene tablas de negocio, catálogos, tablas de simulación y tablas
propias de Flowable. Hace falta que una base vacía quede reproducible con un solo
arranque y que el esquema no dependa de lo que Hibernate decida crear.

## Decisión

Flyway versiona las tablas de negocio con migraciones inmutables `V1` a `V12`.
Hibernate solo valida (`ddl-auto: validate`). Flowable crea sus tablas `act_*`
con `database-schema-update: true`.

Convenciones:

- Tablas en singular y `snake_case`.
- Columnas con prefijo de cuatro letras por palabra del nombre de la tabla; si la
  palabra tiene menos de cuatro letras, se usa completa. Ejemplo:
  `solicitud_financiera` → `solifina_`.
- UUID en `<prefijo>_id`; catálogos con `<prefijo>_codigo`.
- Montos `NUMERIC(19,0)` y `BigDecimal`; instantes `TIMESTAMP WITH TIME ZONE` e
  `Instant`.
- Restricciones `UNIQUE`, `CHECK` e índices declarados en la migración.

## Alternativas descartadas

- **`ddl-auto: update` de Hibernate.** Se descartó porque el esquema depende del
  orden de arranque y de las entidades, no de un historial reproducible.
- **Liquibase.** Se descartó porque no aporta ventajas sobre Flyway para este
  proyecto y el equipo ya trabaja con SQL versionado.

## Consecuencias

- Una base vacía se crea aplicando V1–V12.
- Una migración aplicada no se edita: un cambio se agrega como versión nueva.
- Las tablas de Flowable no se versionan con Flyway y no se tocan a mano.
- Los prefijos hacen las consultas más verbosas, pero evitan ambigüedad entre
  tablas con columnas parecidas.

## Dónde se ve

- [`src/main/resources/db/migration`](../../src/main/resources/db/migration)
- [`application.yml`](../../src/main/resources/application.yml)
- [`PruebaEsquemaTest.java`](../../src/test/java/com/workflow/financiero/PruebaEsquemaTest.java)
- [`docs/pruebas.md`](../pruebas.md)
