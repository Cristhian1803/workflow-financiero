# workflow-financiero

Backend de operaciones financieras con BPMN. El código Java vive en el paquete raíz `com.workflow.financiero`.

## Requisitos

- JDK 21 con `JAVA_HOME` configurado.
- Docker Desktop con WSL2 en Windows, o Docker Engine + Docker Compose en Linux/macOS.
- Git.
- **No se necesita Maven global**: el proyecto incluye Maven Wrapper.

## Base de datos

> Antes de ejecutar `docker compose` o las pruebas, verificar que Docker Desktop
> esté iniciado. Si se reinicia Windows, abrir Docker Desktop y esperar el motor.

Crear el archivo de variables locales:

```powershell
Copy-Item .env.example .env
```

Compose usa `POSTGRES_*`; Spring usa `DB_URL`, `DB_USER`, `DB_PASSWORD` y las variables JWT.

Generar un secreto JWT de al menos 32 bytes y copiarlo en `.env`:

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

`JWT_SECRETO` no tiene valor por defecto versionado. Si falta o es demasiado corto, la aplicación no arranca.

Levantar PostgreSQL 16:

```powershell
docker compose up -d postgres
docker compose ps
```

## Compilar y probar

Windows:

```powershell
.\mvnw.cmd clean verify
```

Linux/macOS:

```bash
chmod +x mvnw   # solo si el archivo no tiene permiso de ejecucion
./mvnw clean verify
```

Las pruebas de integración usan Testcontainers y generan un secreto JWT aleatorio por ejecución; no necesitan el `.env`.

## Ejecutar la aplicación

Perfil `desarrollo`:

```powershell
$env:SPRING_PROFILES_ACTIVE="desarrollo"
.\mvnw.cmd spring-boot:run
```

La aplicación lee `.env` automáticamente. Como alternativa se puede definir `JWT_SECRETO` en la sesión:

```powershell
$env:JWT_SECRETO="<secreto de al menos 32 bytes>"
```

El sembrador de usuarios demo corre al final del arranque. Si el primer login devuelve `401`, esperar un instante y reintentar.

Punto de salud:

```text
GET http://localhost:8080/actuator/health
```

## Autenticación

La API usa JWT firmado.

### Usuarios demo

Solo se crean en `desarrollo` y `pruebas`:

| Usuario | Clave por defecto | Roles | Perfil | Uso |
|---|---|---|---|---|
| `demo` | `demo` | `SOLICITANTE` | `NORMAL` | Solicitante |
| `demo2` | `demo` | `SOLICITANTE` | `NORMAL` | Solicitante alternativo |
| `auditor` | `demo` | `AUDITOR` | `NORMAL` | Auditoría |
| `admin` | `demo` | `ADMINISTRADOR` | `NORMAL` | Administración |
| `supervisor` | `demo` | `SUPERVISOR` | `NORMAL` | Aprobador |
| `gerente` | `demo` | `GERENTE` | `NORMAL` | Aprobador y superior de Supervisor |
| `riesgos` | `demo` | `RIESGOS` | `NORMAL` | Aprobador |
| `gerente_general` | `demo` | `GERENTE_GENERAL` | `NORMAL` | Delegado de Gerente |
| `jefe_riesgos` | `demo` | `JEFE_RIESGOS` | `NORMAL` | Delegado de Riesgos |
| `operaciones` | `demo` | `OPERACIONES` | `NORMAL` | Consulta operativa |

La clave se puede cambiar con `DEMO_CLAVE`. El sembrador es idempotente: no duplica usuarios ni sobrescribe claves existentes.

### Iniciar sesión

```powershell
$respuesta = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/v1/autenticacion/iniciar-sesion `
  -ContentType "application/json" `
  -Body '{"usuario":"demo","clave":"demo"}'

$respuesta.token
```

Respuesta:

```json
{
  "token": "...",
  "tipo": "Bearer",
  "expiraEn": "2026-10-29T20:00:00Z"
}
```

### Usuario actual

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8080/api/v1/autenticacion/yo `
  -Headers @{ Authorization = "Bearer $($respuesta.token)" }
```

Devuelve `id`, `usuario`, `nombre`, `roles` y `perfilRiesgo`; nunca devuelve el hash de la clave.

### Rutas

- `POST /api/v1/autenticacion/iniciar-sesion`: público.
- `GET /api/v1/autenticacion/yo`: autenticado.
- `/actuator/health`: público.
- `/actuator/info`: requiere `ADMINISTRADOR`.
- Swagger y `/v3/api-docs`: públicos en `desarrollo` y `pruebas`; fuera de esos perfiles requieren autenticación.

Swagger UI en desarrollo:

```text
http://localhost:8080/swagger-ui.html
```

## API de solicitudes

La creación requiere el rol `SOLICITANTE` e inicia el proceso BPMN. Si el plan no requiere aprobaciones, la revisión queda en `APROBADA`; si las requiere, queda en `PENDIENTE_APROBACION` y el motor abre las tareas humanas correspondientes.

| Método | Ruta | Autorización |
|---|---|---|
| `POST` | `/api/v1/solicitudes` | `SOLICITANTE` |
| `POST` | `/api/v1/solicitudes/{id}/reenvios` | Dueño |
| `GET` | `/api/v1/solicitudes/{id}` | Dueño, aprobador relacionado o administrador |
| `GET` | `/api/v1/solicitudes` | Dueño o administrador |
| `GET` | `/api/v1/solicitudes/{id}/historial` | Dueño, auditor o administrador |
| `GET` | `/api/v1/solicitudes/{id}/proceso` | Dueño, aprobador relacionado o administrador |
| `GET` | `/api/v1/tareas-aprobacion` | Aprobador o delegado |
| `POST` | `/api/v1/tareas-aprobacion/{id}/aprobar` | Actor elegible |
| `POST` | `/api/v1/tareas-aprobacion/{id}/rechazar` | Actor elegible |
| `GET` | `/api/v1/notificaciones` | Autenticado |

### Crear

```powershell
$creacion = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/v1/solicitudes `
  -Headers @{ Authorization = "Bearer $($respuesta.token)" } `
  -ContentType "application/json" `
  -Body '{"tipoOperacion":"NORMAL","monto":25000000,"moneda":"PYG","descripcion":"Pago de demostracion","justificacion":"Operacion autorizada para pruebas"}'

$creacion
```

La respuesta `201` incluye `id`, `idRevision`, `revision`, `solicitante`, tipo, monto, moneda, descripción, justificación, `estado`, `nivelRiesgo`, `aprobacionesRequeridas`, `requiereEvaluacionExterna`, `fechaCreacion` y `fechaActualizacion`.

Para 5 millones el plan está vacío y la revisión termina en `APROBADA`. Para 25 millones el riesgo es `BAJO`, el plan es `SUPERVISOR` y la revisión queda en `PENDIENTE_APROBACION`. Para 100 millones el riesgo es `MEDIO` y el plan es `GERENTE` y `SUPERVISOR`. Para 250 millones el riesgo es `ALTO`, el plan incluye `GERENTE`, `RIESGOS` y `SUPERVISOR`, y `requiereEvaluacionExterna` es `true`.

### Consultar detalle

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/v1/solicitudes/$($creacion.id)" `
  -Headers @{ Authorization = "Bearer $($respuesta.token)" }
```

### Listar y paginar

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/v1/solicitudes?pagina=0&tamano=20&estado=PENDIENTE_APROBACION" `
  -Headers @{ Authorization = "Bearer $($respuesta.token)" }
```

`pagina` empieza en `0`; `tamano` admite de `1` a `100`. El administrador ve todas las solicitudes; el solicitante solo las propias.

### Historial

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/v1/solicitudes/$($creacion.id)/historial" `
  -Headers @{ Authorization = "Bearer $($respuesta.token)" }
```

Devuelve eventos inmutables con actor, rol, estados, detalle, correlación y fecha.

### Proceso

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/v1/solicitudes/$($creacion.id)/proceso" `
  -Headers @{ Authorization = "Bearer $($respuesta.token)" }
```

La respuesta incluye `identificadorProceso`, `iniciado`, `finalizado`, `estadoRevision`, `actividadesActivas` y `tareas`. Para una instancia activa muestra las tareas humanas con su id de negocio, rol, estado, asignado y vencimiento; para una finalizada consulta el historial y devuelve las listas vacías.

Una solicitud ajena no visible responde `404`; un usuario sin el rol necesario responde `403`. El dueño, el administrador y un aprobador con tarea en la revisión actual pueden consultar el detalle y el proceso.

### Tareas de aprobación

```powershell
$loginSupervisor = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/v1/autenticacion/iniciar-sesion `
  -ContentType "application/json" `
  -Body '{"usuario":"supervisor","clave":"demo"}'

Invoke-RestMethod `
  -Uri http://localhost:8080/api/v1/tareas-aprobacion `
  -Headers @{ Authorization = "Bearer $($loginSupervisor.token)" }
```

Devuelve solo tareas `PENDIENTE` de la revisión actual visibles para el actor. Incluye las tareas asignadas y las tareas sin asignar de sus roles funcionales o delegados. El creador de la solicitud no ve sus propias tareas. Cada elemento incluye `fechaCreacion`, `fechaNotificacion` calculada con `flujo.aprobacion.notificacion` y `fechaVencimiento` guardada con `flujo.aprobacion.reasignacion`, además de los datos de la solicitud.

Los plazos y superiores se configuran en `flujo.aprobacion`:

```yaml
flujo:
  aprobacion:
    notificacion: ${APROBACION_DURACION_NOTIFICACION:PT24H}
    reasignacion: ${APROBACION_DURACION_REASIGNACION:PT48H}
    superiores:
      SUPERVISOR: GERENTE
      GERENTE: GERENTE_GENERAL
      RIESGOS: JEFE_RIESGOS
```

### Notificaciones

Cuando una tarea de aprobación sigue pendiente al cumplirse el plazo de notificación, el proceso crea una notificación persistida para el rol funcional. La API devuelve las notificaciones dirigidas al actor o a sus roles activos, sin duplicados y ordenadas por fecha descendente.

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8080/api/v1/notificaciones `
  -Headers @{ Authorization = "Bearer $($loginSupervisor.token)" }
```

Cada elemento incluye `id`, `tipo`, `contenido`, `estado`, `idTarea`, `idSolicitud`, `revision`, `fechaCreacion` y `fechaLectura`. En esta etapa no hay endpoint para marcar como leída ni envío de correo. Una tarea aprobada o asignada antes del vencimiento no genera la notificación de 24 horas.

### Aprobar y rechazar

```powershell
$loginSupervisor = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/v1/autenticacion/iniciar-sesion `
  -ContentType "application/json" `
  -Body '{"usuario":"supervisor","clave":"demo"}'

$tarea = (Invoke-RestMethod `
  -Uri http://localhost:8080/api/v1/tareas-aprobacion `
  -Headers @{ Authorization = "Bearer $($loginSupervisor.token)" })[0]

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/tareas-aprobacion/$($tarea.id)/aprobar" `
  -Headers @{ Authorization = "Bearer $($loginSupervisor.token)" } `
  -ContentType "application/json" `
  -Body '{"comentario":"Aprobado para continuar"}'
```

Para rechazar se usa `/rechazar` con `motivo` obligatorio:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/tareas-aprobacion/$($tarea.id)/rechazar" `
  -Headers @{ Authorization = "Bearer $($loginSupervisor.token)" } `
  -ContentType "application/json" `
  -Body '{"motivo":"El monto no coincide con la documentación"}'
```

Ambas respuestas incluyen `idTarea`, `decision`, `fecha`, `estadoTarea`, `estadoRevision` y `delegado`. El id es el de la tarea de negocio, no el de Flowable.

Reglas aplicadas:

- El creador no puede aprobar su propia solicitud.
- Un actor no puede aprobar dos roles funcionales distintos en la misma solicitud.
- El mismo rol se puede repetir en otra revisión.
- Si la tarea tiene asignado, solo el asignado puede resolverla y debe tener el rol funcional o la delegación.
- El rol registrado es el rol funcional de la tarea, aunque actúe un superior por delegación.
- Un rechazo cancela las demás tareas pendientes de la revisión y termina el proceso sin preparar ejecución.

Errores principales: `400 DATOS_INVALIDOS`, `401 NO_AUTENTICADO`, `403 APROBACION_NO_PERMITIDA`, `403 SEGREGACION_FUNCIONES`, `404 TAREA_NO_ENCONTRADA`, `409 TAREA_YA_RESUELTA` y `409 TRANSICION_TAREA_INVALIDA`.

### Rutas y aprobaciones paralelas

El proceso activa solo las ramas del plan:

| Monto | Riesgo | Ramas |
|---|---|---|
| Hasta 10 millones | Bajo o medio | Ninguna |
| Hasta 10 millones | Alto | `RIESGOS` |
| Hasta 50 millones | Bajo o medio | `SUPERVISOR` |
| Hasta 50 millones | Alto | `SUPERVISOR`, `RIESGOS` |
| Hasta 200 millones | Bajo o medio | `SUPERVISOR`, `GERENTE` |
| Hasta 200 millones | Alto | `SUPERVISOR`, `GERENTE`, `RIESGOS` |
| Más de 200 millones | Alto | `SUPERVISOR`, `GERENTE`, `RIESGOS` |

Gerente y Riesgos son independientes y se pueden aprobar en cualquier orden. La revisión solo pasa a `APROBADA` cuando todas las ramas activadas aprobaron. Para más de 200 millones, después de las aprobaciones el proceso espera el mensaje `RIESGO_EVALUADO`; en `desarrollo` no hay una ruta HTTP para enviarlo, así que la solicitud queda en `PENDIENTE_APROBACION` hasta la integración de próximas etapas. El fixture se usa solo en pruebas automatizadas.

### Reenvío

Solo el dueño puede reenviar una solicitud cuya revisión actual esté `RECHAZADA`, dentro de los 30 días posteriores al rechazo y con un máximo de tres revisiones en total. El reenvío crea una revisión y un proceso nuevos, recalcula riesgo y plan y conserva el historial, las decisiones y las tareas de la revisión anterior.

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/solicitudes/$($creacion.id)/reenvios" `
  -Headers @{ Authorization = "Bearer $($respuesta.token)" } `
  -ContentType "application/json" `
  -Body '{"tipoOperacion":"NORMAL","monto":75000000,"moneda":"PYG","descripcion":"Pago corregido","justificacion":"Se ajusta el monto a la documentacion"}'
```

La respuesta `201` tiene el mismo contrato que la creación, con `revision` incrementada y el `Location` de la solicitud. Errores: `400 SOLICITUD_INVALIDA`, `403 ACCESO_DENEGADO`, `404 RECURSO_NO_ENCONTRADO`, `409 CONFLICTO_DATOS` y `422 REENVIO_NO_PERMITIDO`.

## API de demostración

Requiere `Authorization: Bearer <token>`:

```text
POST http://localhost:8080/api/v1/solicitudes-prueba
GET  http://localhost:8080/api/v1/solicitudes-prueba/{id}
```

No es la API final de solicitudes.

## Limitaciones de esta versión

- No hay refresh tokens, revocación inmediata ni recuperación de contraseña.
- Un token sigue válido hasta su vencimiento; el usuario se revalida como activo en cada consulta, pero los roles del token no cambian hasta que vence.
- Los roles del token no cambian hasta que vence; `/autenticacion/yo` muestra los roles actuales de la base.
- El sembrador no crea usuarios en producción. La provisión de usuarios reales queda fuera de esta iteración.
- La API no cancela solicitudes ni ejecuta operaciones financieras reales. El reenvío está disponible solo para el dueño y con las reglas de la sección de reenvío.
- Las rutas combinadas, el paralelismo, el rechazo y el reenvío están implementados. La integración real de riesgo externo, la cancelación y la compensación quedan para futuras etapas.
- La notificación de 24 horas está implementada. La reasignación de 48 horas y la delegación completa todavía no.
- No se implementó marcar notificaciones como leídas ni envío de correo.
- El creador no puede aprobar sus propias tareas.
- El detalle recalcula el plan de aprobación con las políticas vigentes, porque el plan no se persiste.
- El auditor puede consultar el historial, pero no el detalle ni el proceso de una solicitud ajena.
- La API `/api/v1/solicitudes-prueba` es la demostración de prueba y no debe usarse para operaciones reales.

## Tecnologías

- Java 21
- Spring Boot 3.5.16
- Spring Security OAuth2 Resource Server y JWT
- Flowable 7.2.0
- PostgreSQL 16
- Maven Wrapper
- JUnit 5, Testcontainers, WireMock y ArchUnit

> Este README se completará con la documentación de API, escenarios,
> arquitectura y decisiones a medida que avance el proyecto.
