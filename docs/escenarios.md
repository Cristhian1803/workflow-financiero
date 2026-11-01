# Escenarios de negocio

Este documento describe los recorridos principales del sistema en un entorno
local. Sirven para conocer la operatoria, comprobar el comportamiento observable
y saber dónde inspeccionar cada estado. No se mueve dinero real: el proveedor
financiero y la evaluación de riesgo son simulados y solo existen en perfiles no
productivos.

Los casos de concurrencia y los bordes de monto se comprueban con la suite
automatizada; la matriz está en [`pruebas.md`](pruebas.md).

## Entorno local

Requisitos:

- JDK 21 con `JAVA_HOME`.
- Docker Desktop iniciado.
- Maven Wrapper del repositorio.
- PostgreSQL 16 en Docker Compose.

### Base limpia

Para empezar sin datos anteriores:

```powershell
docker compose down -v
docker compose up -d postgres
docker compose ps
```

`down -v` elimina el volumen y todos los datos. Se usa solo en un entorno local
de demostración.

### Arranque con tiempos acelerados

El perfil `pruebas` mantiene los usuarios demo y los simuladores, expone el
control de fallos y deja el trabajador y el ejecutor asíncrono apagados por
defecto. Para los recorridos se habilitan por variables de entorno:

```powershell
$env:SPRING_PROFILES_ACTIVE="pruebas"
$env:JWT_SECRETO="<secreto de al menos 32 bytes>"
$env:PROVEEDOR_FINANCIERO_SECRETO="<secreto de integración>"
$env:RIESGO_EXTERNO_SECRETO="<secreto de firma>"
$env:FLOWABLE_ASYNC_EXECUTOR_ACTIVATE="true"
$env:EJECUCION_TRABAJADOR_HABILITADO="true"
$env:EJECUCION_TRABAJADOR_INTERVALO_MS="500"
$env:APROBACION_DURACION_NOTIFICACION="PT10S"
$env:APROBACION_DURACION_REASIGNACION="PT20S"
$env:RIESGO_EXTERNO_VENCIMIENTO="PT15S"
.\mvnw.cmd spring-boot:run
```

Los plazos de aprobación por defecto siguen siendo `PT24H` y `PT48H`, y el
vencimiento externo `PT24H`. Los tiempos acelerados son solo para la
demostración. No se crea un perfil nuevo.

Con los tiempos acelerados, una tarea pendiente se notifica a los 10 segundos y
se reasigna a los 20. El ejecutor asíncrono puede tardar unos segundos más en
adquirir el timer. Para recorrer varios escenarios, conviene crear y resolver
cada solicitud dentro del mismo bloque y no dejarla pendiente entre bloques.

Comprobaciones iniciales:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`.
- Contrato OpenAPI: `http://localhost:8080/v3/api-docs`.
- Copia versionada: [`api/openapi.json`](api/openapi.json).

## Usuarios demo

Solo existen en los perfiles `desarrollo` y `pruebas`. La clave es el valor de
`DEMO_CLAVE`, por defecto `demo`.

| Usuario | Rol | Uso |
|---|---|---|
| `demo`, `demo2` | `SOLICITANTE` | Crear solicitudes. |
| `supervisor` | `SUPERVISOR` | Aprobar el primer tramo. |
| `gerente` | `GERENTE` | Aprobar montos altos. |
| `riesgos` | `RIESGOS` | Aprobar la rama de Riesgos. |
| `gerente_general` | `GERENTE_GENERAL` | Superior de Gerente. |
| `jefe_riesgos` | `JEFE_RIESGOS` | Superior de Riesgos y revisor manual. |
| `analista_riesgos` | `ANALISTA_RIESGOS` | Revisión manual de riesgo. |
| `operaciones` | `OPERACIONES` | Consultar y reintentar incidentes. |
| `auditor` | `AUDITOR` | Consultar historial. |
| `admin` | `ADMINISTRADOR` | Visibilidad completa y cancelación. |

## Autenticación

Cada recorrido empieza con un inicio de sesión. El token se usa en la cabecera
`Authorization: Bearer <token>` y no se guarda en archivos del repositorio.

```powershell
$token = (Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/autenticacion/iniciar-sesion `
  -ContentType "application/json" `
  -Body '{"usuario":"demo","clave":"demo"}').token
```

Para otro usuario se cambia `usuario` y se usa la clave de `DEMO_CLAVE`.

## Cuerpos base

### Crear una solicitud

```json
{
  "tipoOperacion": "NORMAL",
  "monto": 25000000,
  "moneda": "PYG",
  "descripcion": "Pago de servicios",
  "justificacion": "Operación de demostración"
}
```

### Aprobar

```json
{
  "comentario": "Aprobado para continuar"
}
```

### Rechazar

```json
{
  "motivo": "El monto no coincide con la documentación"
}
```

### Cancelar

```json
{
  "motivo": "El cliente desistió de la operación"
}
```

### Resolver una revisión manual

```json
{
  "nivelRiesgo": "ALTO",
  "motivo": "El proveedor externo no respondió en el plazo",
  "evidencia": "Consulta y registro de incidente"
}
```

### Reintentar un incidente

```json
{
  "motivo": "El proveedor ya está disponible"
}
```

## Rutas de aprobación

| Monto | Riesgo interno | Aprobaciones | Evaluación externa |
|---|---|---|---|
| Hasta 10.000.000 | `BAJO` | Ninguna; validación automática | No |
| 10.000.001 a 50.000.000 | `BAJO` | `SUPERVISOR` | No |
| 50.000.001 a 200.000.000 | `MEDIO` | `SUPERVISOR` y `GERENTE` | No |
| Más de 200.000.000 | `ALTO` | `SUPERVISOR`, `GERENTE` y `RIESGOS` | Sí |
| Cualquier monto con `SENSIBLE` | `ALTO` | Agrega `RIESGOS` | Según el monto |

Los bordes exactos y las combinaciones de riesgo están en
[`pruebas.md`](pruebas.md).

## Inspección

### API

| Consulta | Ruta |
|---|---|
| Detalle de la solicitud | `GET /api/v1/solicitudes/{id}` |
| Historial de eventos | `GET /api/v1/solicitudes/{id}/historial` |
| Estado del proceso | `GET /api/v1/solicitudes/{id}/proceso` |
| Tareas de aprobación | `GET /api/v1/tareas-aprobacion` |
| Tareas de revisión manual | `GET /api/v1/tareas-revision-riesgo` |
| Notificaciones | `GET /api/v1/notificaciones` |
| Incidentes | `GET /api/v1/incidentes` |
| Operación simulada | `GET /api/v1/integraciones/proveedor-financiero/operaciones/{clave}` |

La consulta de operaciones del simulador exige la cabecera
`X-Clave-Integracion` con el valor de `PROVEEDOR_FINANCIERO_SECRETO`.

### Base de datos

```powershell
docker compose exec postgres psql -U workflow -d workflow_financiero
```

Las tablas principales son `revision_solicitud`, `intento_ejecucion`,
`operacion_simulada`, `tarea_aprobacion`, `incidente`, `evento_externo`,
`evaluacion_riesgo`, `tarea_revision_riesgo` y `evento_solicitud`. El esquema y
las columnas están en [`base-de-datos.md`](base-de-datos.md).

Consultas de referencia; se reemplaza `<uuid>` por el identificador real.

```sql
SELECT revisoli_numero, revisoli_estado, revisoli_nivel_riesgo, revisoli_monto
FROM revision_solicitud
WHERE revisoli_solifina_id = '<uuid>'
ORDER BY revisoli_numero;
```

```sql
SELECT i.inteejec_estado, i.inteejec_clave, i.inteejec_intentos
FROM intento_ejecucion i
JOIN revision_solicitud r ON r.revisoli_id = i.inteejec_revisoli_id
WHERE r.revisoli_solifina_id = '<uuid>';
```

```sql
SELECT opersimu_accion, opersimu_estado, opersimu_clave, opersimu_referencia
FROM operacion_simulada
WHERE opersimu_solifina_id = '<uuid>'
ORDER BY opersimu_creado_en;
```

Para el proceso, usar `revisoli_identificador_proceso` de la revisión y
consultar `act_ru_task` por `proc_inst_id_`.

## Escenario 1 — Registro y rutas de aprobación

**Objetivo:** recorrer el registro, las rutas de monto y el riesgo alto.

**Precondiciones:** entorno demo iniciado y base limpia.

**Identidad:** `demo` para crear; `supervisor`, `gerente` y `riesgos` para
aprobar.

**Datos:**

| Caso | Monto | Tipo | Aprobaciones esperadas |
|---|---|---|---|
| Automático | 5.000.000 | `NORMAL` | Ninguna |
| Supervisor | 25.000.000 | `NORMAL` | `SUPERVISOR` |
| Supervisor y Gerente | 100.000.000 | `NORMAL` | `SUPERVISOR`, `GERENTE` |
| Riesgo externo | 250.000.000 | `NORMAL` | `SUPERVISOR`, `GERENTE`, `RIESGOS` |
| Riesgo alto | 5.000.000 | `SENSIBLE` | `RIESGOS` |

**Pasos:**

1. Iniciar sesión como `demo`.
2. Crear una solicitud de 5.000.000 y consultar el detalle hasta que la revisión
   quede `EJECUTADA`.
3. Crear una solicitud de 25.000.000, iniciar sesión como `supervisor`, listar
   las tareas, aprobar la tarea de `SUPERVISOR` y esperar `EJECUTADA`.
4. Crear una solicitud de 100.000.000 y aprobar las tareas de `SUPERVISOR` y
   `GERENTE` con sus usuarios.
5. Crear una solicitud de 250.000.000 y aprobar las tres tareas. El proceso queda
   esperando la evaluación externa; continuar en el escenario 7.
6. Crear una solicitud de 5.000.000 con tipo `SENSIBLE`, comprobar que el riesgo
   interno es `ALTO` y que se abre una tarea de `RIESGOS`. Aprobarla y esperar
   `EJECUTADA`.

**Resultado esperado:** la solicitud de 5M no abre tareas; las demás abren
exactamente los roles indicados. Ninguna revisión pasa a `EJECUTANDO` antes de
completar sus aprobaciones. Las solicitudes aprobadas terminan en `EJECUTADA` con
una reserva y una confirmación en el simulador.

**Cómo inspeccionarlo:** `GET /api/v1/solicitudes/{id}`,
`GET /api/v1/solicitudes/{id}/proceso`, `GET /api/v1/tareas-aprobacion` y las
consultas de `revision_solicitud`, `tarea_aprobacion` y `operacion_simulada`.

**Limpieza:** cancelar las solicitudes que queden pendientes o reiniciar la base
al terminar los recorridos.

## Escenario 2 — Aprobaciones paralelas

**Objetivo:** comprobar que las ramas de aprobación avanzan en paralelo y que el
proceso espera a todas.

**Precondiciones:** entorno demo iniciado.

**Identidad:** `demo`, `supervisor`, `gerente` y `riesgos`.

**Datos:** dos solicitudes de 100.000.000 y una de 250.000.000.

**Pasos:**

1. Crear una solicitud de 100M. Aprobar primero `SUPERVISOR` y después
   `GERENTE`. Comprobar que la ejecución empieza solo después de la segunda
   aprobación.
2. Crear otra solicitud de 100M. Aprobar primero `GERENTE` y después
   `SUPERVISOR`. Comprobar el mismo resultado.
3. Crear una solicitud de 250M. Aprobar `GERENTE` y `RIESGOS` en cualquier orden
   y `SUPERVISOR` antes o después. Comprobar que las tres tareas existen y que el
   proceso no avanza hasta completarlas.

**Resultado esperado:** las tareas de una misma revisión coexisten, cada una se
resuelve con su rol y la revisión no ejecuta hasta que todas están aprobadas.

**Cómo inspeccionarlo:** `GET /api/v1/tareas-aprobacion`,
`GET /api/v1/solicitudes/{id}/proceso`, el historial y la tabla
`tarea_aprobacion`.

**Limpieza:** cancelar las solicitudes pendientes o reiniciar la base.

## Escenario 3 — Escalamiento por vencimiento

**Objetivo:** observar la notificación de las 24 horas y la reasignación de las
48 horas con tiempos acelerados.

**Precondiciones:** entorno demo con `APROBACION_DURACION_NOTIFICACION=PT10S` y
`APROBACION_DURACION_REASIGNACION=PT20S`.

**Identidad:** `demo`, `supervisor` y `gerente`.

**Datos:** solicitud de 25.000.000.

**Pasos:**

1. Crear la solicitud y dejar la tarea de `SUPERVISOR` pendiente.
2. Esperar entre 20 y 25 segundos. Iniciar sesión como `supervisor` y consultar
   `GET /api/v1/notificaciones`.
3. Esperar hasta 40 segundos desde la creación. Consultar de nuevo la bandeja de
   `supervisor` y la de `gerente`.
4. Consultar las notificaciones de `gerente`.

**Resultado esperado:** aparece una notificación `ESCALAMIENTO_24H` para el rol
`SUPERVISOR`; después la tarea queda asignada a `gerente`, sale de la bandeja de
`supervisor` y genera una notificación `REASIGNACION_48H` para el superior.

**Cómo inspeccionarlo:** `GET /api/v1/notificaciones`,
`GET /api/v1/tareas-aprobacion`, `GET /api/v1/solicitudes/{id}/proceso` y las
tablas `notificacion`, `tarea_aprobacion` y `evento_solicitud`.

**Limpieza:** cancelar la solicitud como `demo` o reiniciar la base.

## Escenario 4 — Rechazo y reenvío

**Objetivo:** comprobar que un rechazo impide la ejecución y que el reenvío
conserva el historial y recalcula el plan.

**Precondiciones:** entorno demo iniciado.

**Identidad:** `demo` para crear y reenviar; `supervisor` para rechazar.

**Datos:** solicitud inicial de 25.000.000; reenvío de 100.000.000.

**Pasos:**

1. Crear la solicitud de 25M.
2. Iniciar sesión como `supervisor`, listar la tarea y rechazarla con un motivo.
3. Consultar el detalle, el historial y el proceso.
4. Iniciar sesión como `demo` y reenviar la solicitud con monto de 100M.
5. Consultar la nueva revisión y la bandeja de `supervisor` y `gerente`.

**Resultado esperado:** la revisión rechazada queda `RECHAZADA`, sus tareas
pendientes quedan `CANCELADA` y no existe intención de ejecución ni operación en
el simulador. El reenvío crea la revisión 2, conserva los eventos anteriores y
abre tareas de `SUPERVISOR` y `GERENTE`.

**Cómo inspeccionarlo:** `GET /api/v1/solicitudes/{id}`,
`GET /api/v1/solicitudes/{id}/historial`,
`GET /api/v1/solicitudes/{id}/proceso`, `GET /api/v1/tareas-aprobacion` y las
tablas `revision_solicitud`, `tarea_aprobacion`, `intento_ejecucion` y
`operacion_simulada`.

**Limpieza:** cancelar la revisión nueva o reiniciar la base.

## Escenario 5 — Cancelación y compensación

**Objetivo:** distinguir una cancelación sin reserva de una cancelación con una
reserva que debe liberarse.

**Precondiciones:** entorno demo iniciado.

**Identidad:** `demo` para crear y cancelar; `supervisor` para aprobar.

**Datos:** dos solicitudes de 25.000.000.

### Sin reserva

1. Crear una solicitud de 25M y dejarla en `PENDIENTE_APROBACION`.
2. Cancelarla como `demo` con un motivo.
3. Consultar el detalle, el proceso y las operaciones del simulador.

**Resultado esperado:** la revisión queda `CANCELADA`, las tareas pendientes
quedan `CANCELADA` y no hay reserva ni confirmación.

### Con reserva

1. Crear una solicitud de 25M y conservar su `id` y su `idRevision`.
2. Configurar un fallo antes del efecto en la confirmación. El control de fallos
   solo existe en `pruebas`, exige la cabecera `X-Clave-Integracion` y se pierde
   al reiniciar la aplicación. La clave del simulador usa el identificador de la
   revisión, no el número de revisión:

```powershell
$idSolicitud = "<uuid>"
$idRevision = "<revision-uuid>"
$clave = "confirmacion:$idSolicitud`:$idRevision"
$cuerpo = @{ clave = $clave; modo = "FALLO_ANTES_DEL_EFECTO" } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/integraciones/proveedor-financiero/pruebas/fallos `
  -Headers @{ "X-Clave-Integracion" = $env:PROVEEDOR_FINANCIERO_SECRETO } `
  -ContentType "application/json" `
  -Body $cuerpo
```

3. Aprobar la tarea de `SUPERVISOR`. El trabajador crea la reserva y la
   confirmación falla antes de aplicarse.
4. Cancelar la solicitud como `demo`.
5. Consultar el detalle hasta que la revisión quede `CANCELADA` o `COMPENSANDO`.
6. Limpiar los fallos configurados:

```powershell
Invoke-RestMethod -Method Delete `
  -Uri http://localhost:8080/api/v1/integraciones/proveedor-financiero/pruebas/fallos `
  -Headers @{ "X-Clave-Integracion" = $env:PROVEEDOR_FINANCIERO_SECRETO }
```

**Resultado esperado:** la reserva existe, la confirmación no se aplicó y la
cancelación libera la reserva. La revisión termina en `CANCELADA`, con una
operación `LIBERACION` y sin una `CONFIRMACION` efectiva.

**Cómo inspeccionarlo:** `GET /api/v1/solicitudes/{id}`,
`GET /api/v1/solicitudes/{id}/historial`, las tablas `intento_ejecucion`,
`intento_compensacion` y `operacion_simulada`, y la consulta de operaciones del
simulador.

**Limpieza:** limpiar los fallos y reiniciar la base si se desea un entorno
limpio.

## Escenario 6 — Fallo y recuperación

**Objetivo:** observar una respuesta perdida, un incidente de ejecución y el
reintento operativo.

**Precondiciones:** entorno demo iniciado y `operaciones` disponible.

**Identidad:** `demo`, `supervisor` y `operaciones`.

**Datos:** dos solicitudes de 25.000.000.

### Respuesta perdida

1. Crear una solicitud de 25M y conservar su `id` y su `idRevision`.
2. Configurar `RESPUESTA_PERDIDA` en la confirmación. La clave usa el
   identificador de la revisión:

```powershell
$idSolicitud = "<uuid>"
$idRevision = "<revision-uuid>"
$cuerpo = @{
  clave = "confirmacion:$idSolicitud`:$idRevision"
  modo = "RESPUESTA_PERDIDA"
} | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/integraciones/proveedor-financiero/pruebas/fallos `
  -Headers @{ "X-Clave-Integracion" = $env:PROVEEDOR_FINANCIERO_SECRETO } `
  -ContentType "application/json" `
  -Body $cuerpo
```

3. Aprobar la tarea de `SUPERVISOR`.
4. Consultar la solicitud y las operaciones hasta que la revisión quede
   `EJECUTADA`.

**Resultado esperado:** la confirmación se aplicó una sola vez; el trabajador
recibió un error y la recuperación consultó la misma clave. El intento queda
`CONFIRMADA`, hay una sola confirmación y el incidente de ejecución que se abre
al recibir el error queda `RESUELTO` al confirmarse el resultado.

### Incidente y reintento

1. Crear otra solicitud de 25M.
2. Configurar `FALLO_ANTES_DEL_EFECTO` en la reserva. Usar el mismo bloque
   anterior con la clave `reserva:<solicitudId>:<idRevision>` y el modo
   `FALLO_ANTES_DEL_EFECTO`.
3. Aprobar la tarea de `SUPERVISOR` y esperar a que se abra un incidente de
   ejecución.
4. Limpiar los fallos.
5. Iniciar sesión como `operaciones`, consultar `GET /api/v1/incidentes` y
   reintentar el incidente con un motivo.
6. Consultar la solicitud hasta que quede `EJECUTADA`.

**Resultado esperado:** el incidente queda `RESUELTO`, la revisión termina en
`EJECUTADA`, se conserva la misma clave y huella financiera y existe una sola
reserva y una sola confirmación.

**Cómo inspeccionarlo:** `GET /api/v1/incidentes`,
`GET /api/v1/solicitudes/{id}`, el historial y las tablas `incidente`,
`intento_ejecucion` y `operacion_simulada`.

**Limpieza:** limpiar los fallos y reiniciar la base si se desea.

## Escenario 7 — Eventos externos y revisión manual

**Objetivo:** recibir el resultado de una evaluación externa, tratar duplicados y
eventos fuera de tiempo, y resolver el vencimiento con una revisión manual.

**Precondiciones:** entorno demo iniciado; `RIESGO_EXTERNO_SECRETO` configurado.

**Identidad:** `demo`, `supervisor`, `gerente`, `riesgos` y `analista_riesgos`.

**Datos:** solicitudes de 250.000.000.

### Notificación firmada

Después de aprobar las tres tareas, el proceso espera `RIESGO_EVALUADO`. Enviar
la notificación con firma HMAC. El ejemplo usa la revisión 1; si la solicitud fue
reenviada, ajustar `$revision`:

```powershell
$idSolicitud = "<uuid>"
$revision = 1
$evento = "evt-$idSolicitud-$revision"
$proveedor = "proveedor-riesgo-simulado"
$timestamp = [DateTime]::UtcNow.ToString(
  "yyyy-MM-ddTHH:mm:ssZ",
  [System.Globalization.CultureInfo]::InvariantCulture
)
$cuerpo = [ordered]@{
  proveedor = $proveedor
  identificadorEvento = $evento
  identificadorSolicitud = $idSolicitud
  numeroRevision = $revision
  nivelRiesgo = "ALTO"
  fechaProveedor = $timestamp
  resultado = "Evaluación externa de demostración"
} | ConvertTo-Json -Compress
$bytes = [System.Text.Encoding]::UTF8.GetBytes($cuerpo)
$canonico = "$proveedor`n$evento`n$timestamp`n$cuerpo"
$hmac = [System.Security.Cryptography.HMACSHA256]::new(
  [System.Text.Encoding]::UTF8.GetBytes($env:RIESGO_EXTERNO_SECRETO)
)
$firma = (($hmac.ComputeHash(
  [System.Text.Encoding]::UTF8.GetBytes($canonico)
)) | ForEach-Object { $_.ToString("x2") }) -join ""
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/integraciones/riesgos/notificacion `
  -Headers @{
    "X-Proveedor" = $proveedor
    "X-Identificador-Evento" = $evento
    "X-Timestamp" = $timestamp
    "X-Firma" = $firma
  } `
  -ContentType "application/json; charset=utf-8" `
  -Body $bytes
```

**Resultado esperado:** la primera notificación responde `200 PROCESADO` si el
proceso ya espera. Si todavía no llegó a la espera, responde `202 PENDIENTE` y el
trabajador la correlaciona. En ambos casos la revisión aplica el riesgo externo y
continúa hasta `EJECUTADA`.

### Duplicado, temprano, tardío y conflicto

- **Duplicado:** repetir exactamente la misma llamada, sin recalcular cuerpo,
  timestamp ni firma. Responde `200` con estado `DUPLICADO` y no repite el
  efecto.
- **Temprano:** enviar la notificación antes de que el proceso espere. Responde
  `202` con estado `PENDIENTE`; el trabajador la correlaciona cuando el proceso
  llega a la espera.
- **Tardío:** enviar una notificación nueva después de que la revisión terminó o
  fue cancelada. Responde `200` con estado `OBSOLETO`.
- **Conflicto:** reutilizar el mismo `identificadorEvento` con otro cuerpo.
  Responde `409 EVENTO_EXTERNO_EN_CONFLICTO` sin aplicar un segundo efecto.

### Vencimiento y revisión manual

1. Crear una solicitud de 250M y aprobar las tres tareas.
2. No enviar la notificación externa. Esperar entre 20 y 25 segundos; el timer
   configurado es `PT15S`, pero el ejecutor puede tardar en adquirirlo.
3. Iniciar sesión como `analista_riesgos` y consultar
   `GET /api/v1/tareas-revision-riesgo`.
4. Resolver la tarea con el cuerpo de revisión manual.
5. Consultar la solicitud y el proceso.

**Resultado esperado:** la evaluación externa queda `VENCIDA`, la revisión pasa
a `REVISION_MANUAL`, se crea la tarea manual y, al resolverla, se registra una
evaluación `MANUAL`, la tarea queda `RESUELTA` y el proceso continúa.

**Cómo inspeccionarlo:** `GET /api/v1/tareas-revision-riesgo`,
`GET /api/v1/solicitudes/{id}/historial`,
`GET /api/v1/solicitudes/{id}/proceso` y las tablas `evento_externo`,
`evaluacion_riesgo`, `tarea_revision_riesgo` y `revision_solicitud`.

**Limpieza:** cancelar las solicitudes pendientes o reiniciar la base.

## Escenario 8 — Seguridad y autorización

**Objetivo:** comprobar que las restricciones no dependen de la interfaz y que
los roles se validan en el servidor.

**Precondiciones:** entorno demo iniciado y una tarea de `SUPERVISOR` pendiente.

**Identidad:** `demo`, `demo2`, `supervisor` y `gerente`.

**Datos:** solicitudes de 25.000.000 y 100.000.000.

### Casos básicos

1. **Sin token:** llamar a `GET /api/v1/solicitudes` sin `Authorization`.
   Respuesta `401 NO_AUTENTICADO`.
2. **Rol insuficiente:** iniciar sesión como `demo2` e intentar aprobar una tarea
   de `SUPERVISOR`. Respuesta `403 ACCESO_DENEGADO`.
3. **Recurso ajeno o inexistente:** con `demo2`, consultar una solicitud de
   `demo` o un identificador aleatorio. Respuesta `404 RECURSO_NO_ENCONTRADO`.

### Autoaprobación

1. Otorgar temporalmente el rol `SUPERVISOR` a `demo`:

```sql
INSERT INTO usuario_rol (usurol_usua_id, usurol_rol_codigo)
SELECT usua_id, 'SUPERVISOR'
FROM usuario
WHERE usua_usuario = 'demo'
ON CONFLICT (usurol_usua_id, usurol_rol_codigo) DO NOTHING;
```

2. Iniciar sesión de nuevo como `demo` para obtener un token con el rol nuevo.
3. Crear una solicitud de 25M. La tarea de `SUPERVISOR` existe, pero `demo` no la
   ve en su bandeja porque es el creador.
4. Obtener el identificador de la tarea con `supervisor` e intentar aprobarla con
   el token de `demo`. Respuesta `403 APROBACION_NO_PERMITIDA`.
5. Retirar el rol temporal:

```sql
DELETE FROM usuario_rol
WHERE usurol_rol_codigo = 'SUPERVISOR'
  AND usurol_usua_id = (
    SELECT usua_id FROM usuario WHERE usua_usuario = 'demo'
  );
```

### Doble rol funcional

1. Otorgar temporalmente el rol `GERENTE` a `supervisor`:

```sql
INSERT INTO usuario_rol (usurol_usua_id, usurol_rol_codigo)
SELECT usua_id, 'GERENTE'
FROM usuario
WHERE usua_usuario = 'supervisor'
ON CONFLICT (usurol_usua_id, usurol_rol_codigo) DO NOTHING;
```

2. Iniciar sesión de nuevo como `supervisor`.
3. Crear una solicitud de 100M como `demo` y aprobar la tarea de `SUPERVISOR`
   con `supervisor`.
4. Intentar aprobar la tarea de `GERENTE` con el mismo `supervisor`. Respuesta
   `403 SEGREGACION_FUNCIONES`.
5. Retirar el rol temporal:

```sql
DELETE FROM usuario_rol
WHERE usurol_rol_codigo = 'GERENTE'
  AND usurol_usua_id = (
    SELECT usua_id FROM usuario WHERE usua_usuario = 'supervisor'
  );
```

**Resultado esperado:** las restricciones se aplican en el servidor, no cambian
el estado de la solicitud y no crean decisiones nuevas.

**Cómo inspeccionarlo:** códigos HTTP, `GET /api/v1/solicitudes/{id}/historial`,
`GET /api/v1/tareas-aprobacion` y la tabla `registro_actor_rol`.

**Limpieza:** retirar los roles temporales y reiniciar la base si se desea. Después
de retirar un rol hay que iniciar sesión de nuevo para que el token refleje los
roles vigentes.

## Limpieza final

1. Limpiar los fallos del simulador si quedaron configurados.
2. Cancelar las solicitudes pendientes o reiniciar la base.
3. Detener la aplicación.
4. Recrear la base:

```powershell
docker compose down -v
docker compose up -d postgres
```

5. Comprobar que no quedan datos de negocio:

```sql
SELECT COUNT(*) FROM solicitud_financiera;
SELECT COUNT(*) FROM revision_solicitud;
SELECT COUNT(*) FROM intento_ejecucion;
SELECT COUNT(*) FROM operacion_simulada;
SELECT COUNT(*) FROM incidente;
SELECT COUNT(*) FROM evento_externo;
SELECT COUNT(*) FROM tarea_revision_riesgo;
SELECT COUNT(*) FROM act_ru_execution;
SELECT COUNT(*) FROM act_ru_task;
```

Al volver a arrancar, Flyway aplica las migraciones y el sembrador crea los
usuarios demo. La base queda lista para un recorrido nuevo.
