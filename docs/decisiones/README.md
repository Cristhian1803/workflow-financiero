# Decisiones de arquitectura

Cada archivo registra una decisión vigente, el contexto que la motivó, la
alternativa que se descartó y dónde se puede comprobar en el código. Las
decisiones se numeran y no se reescriben: si una cambia, se agrega un ADR nuevo
que la reemplaza.

| ADR | Decisión | Estado |
|---|---|---|
| [ADR-001](adr-001-arquitectura-en-capas.md) | Arquitectura en capas con entidades JPA en el dominio | Aceptada |
| [ADR-002](adr-002-monolito-modular.md) | Monolito modular en un solo despliegue | Aceptada |
| [ADR-003](adr-003-flowable-transaccion-compartida.md) | Flowable embebido con transacción compartida | Aceptada |
| [ADR-004](adr-004-modelo-solicitud-revision.md) | Modelo de solicitud con revisiones sucesivas | Aceptada |
| [ADR-005](adr-005-concurrencia-y-bloqueo.md) | Bloqueo pesimista con orden fijo y versión optimista | Aceptada |
| [ADR-006](adr-006-ejecucion-con-intencion-durable.md) | Ejecución financiera con intención durable | Aceptada |
| [ADR-007](adr-007-cancelacion-y-compensacion.md) | Cancelación orquestada con compensación durable | Aceptada |
| [ADR-008](adr-008-riesgo-externo-e-inbox.md) | Riesgo externo asíncrono con inbox idempotente | Aceptada |
| [ADR-009](adr-009-seguridad-jwt.md) | Seguridad con JWT emitido por la aplicación | Aceptada |
| [ADR-010](adr-010-esquema-flyway.md) | Esquema versionado con Flyway y prefijos | Aceptada |
| [ADR-011](adr-011-simuladores.md) | Simuladores dentro del mismo despliegue | Aceptada |
| [ADR-012](adr-012-estrategia-de-pruebas.md) | Pruebas con PostgreSQL y Flowable reales | Aceptada |

Las consecuencias que hoy limitan el sistema están reunidas en
[`docs/limitaciones.md`](../limitaciones.md).
