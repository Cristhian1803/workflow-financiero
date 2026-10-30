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

| Usuario | Clave por defecto | Roles | Perfil |
|---|---|---|---|
| `demo` | `demo` | `SOLICITANTE` | `NORMAL` |
| `demo2` | `demo` | `SOLICITANTE` | `NORMAL` |
| `auditor` | `demo` | `AUDITOR` | `NORMAL` |
| `admin` | `demo` | `ADMINISTRADOR` | `NORMAL` |

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

La creación requiere el rol `SOLICITANTE` y termina en `EVALUANDO_RIESGO`. No inicia el proceso BPMN ni abre tareas.

| Método | Ruta | Autorización |
|---|---|---|
| `POST` | `/api/v1/solicitudes` | `SOLICITANTE` |
| `GET` | `/api/v1/solicitudes/{id}` | Dueño o administrador |
| `GET` | `/api/v1/solicitudes` | Dueño o administrador |
| `GET` | `/api/v1/solicitudes/{id}/historial` | Dueño, auditor o administrador |
| `GET` | `/api/v1/solicitudes/{id}/proceso` | Dueño o administrador |

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

Para 25 millones el riesgo es `BAJO` y el plan es `SUPERVISOR`. Para 100 millones el riesgo es `MEDIO` y el plan es `GERENTE` y `SUPERVISOR`. Para 250 millones el riesgo es `ALTO`, el plan incluye `GERENTE`, `RIESGOS` y `SUPERVISOR`, y `requiereEvaluacionExterna` es `true`.

### Consultar detalle

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/v1/solicitudes/$($creacion.id)" `
  -Headers @{ Authorization = "Bearer $($respuesta.token)" }
```

### Listar y paginar

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/v1/solicitudes?pagina=0&tamano=20&estado=EVALUANDO_RIESGO" `
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

En esta etapa responde `iniciado = false`, `identificadorProceso = null` y `tareas = []`.

Una solicitud ajena no visible responde `404`; un usuario sin el rol necesario responde `403`.

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
- La API de solicitudes no aprueba, rechaza, cancela, reenvía ni ejecuta operaciones.
- La creación no inicia Flowable ni abre tareas aun.
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
