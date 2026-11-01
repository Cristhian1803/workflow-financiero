# Base de datos

El sistema usa PostgreSQL 16. Flyway versiona el esquema de negocio y Hibernate
solo valida que las entidades coincidan con las tablas. Flowable crea y
administra sus propias tablas `act_*` en la misma base.

## Responsabilidades

| Componente | Qué administra |
|---|---|
| Flyway | Tablas de negocio, catálogos e índices, con migraciones `V1` a `V12`. |
| Hibernate | Validación del mapeo (`ddl-auto: validate`); no crea ni altera tablas. |
| Flowable | Tablas `act_*` del motor, con `database-schema-update: true`. |
| PostgreSQL | Restricciones, unicidades, bloqueos y transacciones compartidas. |

La aplicación no arranca si una entidad no coincide con el esquema. Una
migración aplicada no se edita: cualquier cambio se agrega como versión nueva.

## Migraciones

| Versión | Archivo | Propósito |
|---|---|---|
| `V1` | `V1__spike_solicitud_prueba.sql` | Tablas del circuito mínimo del motor: `solicitud_prueba` y `evento_solicitud_prueba`. |
| `V2` | `V2__catalogos_base.sql` | Catálogos `moneda`, `tipo_operacion`, `perfil_riesgo` y `rol`, con sus semillas. |
| `V3` | `V3__solicitud_revision_auditoria.sql` | `usuario`, `usuario_rol`, `solicitud_financiera`, `revision_solicitud`, `evento_solicitud` y `registro_actor_rol`. |
| `V4` | `V4__aprobaciones_riesgo_ejecucion.sql` | `tarea_aprobacion`, `decision_aprobacion`, `evaluacion_riesgo`, `intento_ejecucion`, `evento_externo`, `notificacion` y `registro_idempotencia`. |
| `V5` | `V5__indice_proceso_revision.sql` | Índice único parcial del identificador de proceso por revisión. |
| `V6` | `V6__operacion_simulada.sql` | Estado persistido del proveedor financiero simulado. |
| `V7` | `V7__intento_ejecucion_durable.sql` | Reclamación, reintentos, referencias y `CHECK` de estado de `intento_ejecucion`. |
| `V8` | `V8__incidente_recuperacion_ejecucion.sql` | `incidente` e índices de recuperación. |
| `V9` | `V9__cancelacion_compensacion_basica.sql` | Estado `CANCELADA` y datos de cancelación en `intento_ejecucion`. |
| `V10` | `V10__intento_compensacion.sql` | `intento_compensacion` para la liberación durable. |
| `V11` | `V11__riesgo_externo_inbox.sql` | Estado, clave, reintentos y checks de `evaluacion_riesgo` y `evento_externo`. |
| `V12` | `V12__revision_manual_riesgo.sql` | `tarea_revision_riesgo` y unicidad de la evaluación manual vigente. |

`V1` conserva las tablas del endpoint de demostración. No se eliminan porque el
código del circuito mínimo sigue en el repositorio, pero no participan del flujo
principal.

`registro_idempotencia` existe desde `V4`, pero no tiene entidad ni se usa en el
flujo actual. La identidad financiera durable es `intento_ejecucion`, con clave
y huella estables; la tabla queda como base para una idempotencia de API por
usuario.

## Convención de nombres

- Tablas en singular y `snake_case`.
- Cada columna lleva el prefijo de cuatro letras por palabra del nombre de la
  tabla. Si la palabra tiene menos de cuatro letras, se usa completa.
- La clave primaria es `<prefijo>_id` y es `UUID`.
- Los catálogos usan `<prefijo>_codigo` como clave natural.
- Las claves foráneas repiten el prefijo de la tabla destino.

| Tabla | Prefijo |
|---|---|
| `solicitud_financiera` | `solifina_` |
| `revision_solicitud` | `revisoli_` |
| `evento_solicitud` | `evensoli_` |
| `usuario` | `usua_` |
| `usuario_rol` | `usurol_` |
| `registro_actor_rol` | `regiactorol_` |
| `moneda` | `mone_` |
| `tipo_operacion` | `tipooper_` |
| `perfil_riesgo` | `perfries_` |
| `rol` | `rol_` |
| `tarea_aprobacion` | `tareapro_` |
| `decision_aprobacion` | `deciapro_` |
| `evaluacion_riesgo` | `evalries_` |
| `intento_ejecucion` | `inteejec_` |
| `evento_externo` | `evenexte_` |
| `notificacion` | `noti_` |
| `registro_idempotencia` | `regiidem_` |
| `operacion_simulada` | `opersimu_` |
| `incidente` | `inci_` |
| `intento_compensacion` | `intecomp_` |
| `tarea_revision_riesgo` | `tarereviries_` |

`V1` es la excepción histórica: usa `id`, `estado`, `monto` y otros nombres sin
prefijo.

## Catálogos

`V2` crea los catálogos y sus semillas:

| Catálogo | Valores iniciales |
|---|---|
| `moneda` | `PYG` (guaraní, 0 decimales, activa). |
| `tipo_operacion` | `NORMAL` (no sensible) y `SENSIBLE` (sensible). |
| `perfil_riesgo` | `NORMAL` y `ALTO`. |
| `rol` | `SOLICITANTE`, `SUPERVISOR`, `GERENTE`, `GERENTE_GENERAL`, `RIESGOS`, `ANALISTA_RIESGOS`, `JEFE_RIESGOS`, `OPERACIONES`, `AUDITOR` y `ADMINISTRADOR`. |

`rol_aprobador` marca los roles que pueden recibir tareas de aprobación. Los
usuarios demo se siembran desde la aplicación, no desde las migraciones.

## Tipos y reglas

| Dato | Tipo | Regla |
|---|---|---|
| Identificadores | `UUID` | Generados por la aplicación. |
| Montos | `NUMERIC(19,0)` | Enteros positivos; `BigDecimal` en Java. |
| Moneda | `CHAR(3)` | `PYG` por ahora. |
| Instantes | `TIMESTAMP WITH TIME ZONE` | `Instant` en Java, en UTC. |
| Versión | `BIGINT` | `@Version` para concurrencia optimista. |
| Booleanos | `BOOLEAN` | Con valor por defecto cuando corresponde. |

## Restricciones principales

### Solicitud y revisión

- `revision_solicitud` tiene unicidad por `(solicitud, número)` y por
  `(solicitud, revisión)` para permitir la FK compuesta.
- `revisoli_monto > 0`.
- `revisoli_estado` solo admite los doce estados del ciclo de vida.
- `revisoli_nivel_riesgo` admite `BAJO`, `MEDIO`, `ALTO` o nulo.
- `solicitud_financiera.solifina_revisoli_actual_id` es único y apunta a la
  revisión vigente.
- `V5` agrega un índice único parcial sobre `revisoli_identificador_proceso`
  cuando no es nulo.

### Aprobaciones

- Una tarea por revisión y rol: `uq_tarea_revision_rol`.
- Un identificador de tarea del motor por fila: `uq_tarea_motor`.
- Una decisión por tarea: `uq_decision_tarea`.
- Estados de tarea: `PENDIENTE`, `APROBADA`, `RECHAZADA`, `CANCELADA`.
- Decisiones: `APROBADA` o `RECHAZADA`.
- `registro_actor_rol` impide que un mismo actor cubra dos roles en la misma
  solicitud: su clave primaria es `(solicitud, usuario)`.

### Riesgo

- `evaluacion_riesgo.evalries_origen` admite `INTERNO`, `EXTERNO`, `MANUAL`.
- `V11` hace nullable el nivel, agrega estado y datos de envío, y crea un índice
  único parcial de la evaluación externa vigente por revisión.
- `V12` crea el índice único parcial de la evaluación manual vigente.
- `evento_externo` tiene unicidad por `(proveedor, identificador_evento)` y una
  FK compuesta hacia solicitud y revisión.
- Estados del evento: `PENDIENTE`, `PROCESADO`, `DUPLICADO`, `CONFLICTO`,
  `OBSOLETO`.
- Nivel del evento: `BAJO`, `MEDIO`, `ALTO` o nulo.

### Ejecución y compensación

- `intento_ejecucion` tiene una fila por revisión y una clave única.
- Estados: `PENDIENTE`, `ENVIANDO`, `CONFIRMADA`, `RESULTADO_DESCONOCIDO`,
  `FALLIDA`, `COMPENSANDO`, `COMPENSADA`, `CANCELADA`.
- `intento_compensacion` tiene una fila por revisión y una clave única.
- Estados: `PENDIENTE`, `LIBERANDO`, `LIBERADA`, `CANCELADA`, `RECHAZADA`,
  `FALLIDA`, `REVISION_MANUAL`.
- Los índices `idx_intento_reclamacion`, `idx_intento_recuperacion` e
  `idx_intento_compensacion_reclamable` sostienen la reclamación con
  `FOR UPDATE SKIP LOCKED`.

### Incidentes

- Un incidente por causa: `(tipo, referencia)`.
- Tipos: `EJECUCION`, `COMPENSACION`, `RIESGO_EXTERNO`.
- Estados: `ABIERTO`, `REINTENTANDO`, `RESUELTO`, `CERRADO`.

### Simulador

- `operacion_simulada` no tiene claves foráneas hacia el dominio.
- Unicidad por clave y por `(solicitud, revisión, acción)`.
- Acciones: `RESERVA`, `CONFIRMACION`, `LIBERACION`.
- Estados: `RESERVADA`, `CONFIRMADA`, `LIBERADA`.
- `opersimu_monto > 0`.

## Tablas de Flowable

Flowable usa el mismo `DataSource` y el mismo gestor de transacciones que JPA.
Sus tablas se reconocen por el prefijo `act_` (`act_ru_task`, `act_hi_*`,
`act_ge_*`, entre otras) y se crean con `database-schema-update: true`.

- No se versionan con Flyway.
- No se modifican a mano.
- No se borran para limpiar datos de negocio.
- El historial se configura en `audit`.

La transacción compartida permite que una aprobación y el avance del motor se
confirmen o reviertan juntos. El detalle está en
[`arquitectura.md`](arquitectura.md) y en
[`decisiones/adr-003-flowable-transaccion-compartida.md`](decisiones/adr-003-flowable-transaccion-compartida.md).

## Cómo recrear la base

Con Docker Compose:

```powershell
docker compose down -v
docker compose up -d postgres
docker compose ps
```

`down -v` elimina el volumen y todos los datos. Sirve para una base de
demostración, no para conservar información.

Para probar sin borrar la base actual, crear una base temporal:

```powershell
docker compose exec postgres psql -U workflow -d postgres -c "CREATE DATABASE workflow_financiero_nueva;"
$env:DB_URL="jdbc:postgresql://localhost:5432/workflow_financiero_nueva"
.\mvnw.cmd spring-boot:run
```

Flyway aplica las doce migraciones al arrancar. Al terminar:

```powershell
docker compose exec postgres psql -U workflow -d workflow_financiero_nueva -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
docker compose exec postgres psql -U workflow -d postgres -c "DROP DATABASE workflow_financiero_nueva;"
```

## Verificación

| Comprobación | Cómo |
|---|---|
| Migraciones aplicadas | `flyway_schema_history` con doce filas y `success = true`. |
| Esquema coherente con las entidades | La aplicación arranca con `ddl-auto: validate`. |
| Tablas del motor | Flowable crea las tablas `act_*` en el mismo esquema. |
| Restricciones | Los `CHECK`, `UNIQUE`, `FK` e índices están declarados en las migraciones. |

Las migraciones son la fuente de verdad del esquema. Si una tabla o columna
cambia, se agrega una migración nueva y se actualiza este documento en el mismo
cambio.
