# workflow-financiero

Backend de operaciones financieras con BPMN. Una solicitud se registra, se
clasifica por riesgo, recorre las aprobaciones que correspondan, se ejecuta
contra un proveedor financiero simulado y puede cancelarse con compensación. El
proceso corre en Flowable 7.2.0 embebido, comparte transacción con JPA y usa
PostgreSQL 16.

## Requisitos

- JDK 21 con `JAVA_HOME`.
- Docker Desktop iniciado.
- Git.
- No se necesita Maven global: el repositorio incluye Maven Wrapper.

## Inicio rápido en Windows

1. Crear el archivo de variables locales:

```powershell
Copy-Item .env.example .env
```

2. Generar un `JWT_SECRETO` de al menos 32 bytes y copiarlo en `.env`:

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

3. Completar en `.env` los secretos de los simuladores. No pueden quedar vacíos
   para que el flujo completo funcione:

```text
PROVEEDOR_FINANCIERO_SECRETO=<valor no vacío>
RIESGO_EXTERNO_SECRETO=<valor no vacío>
```

4. Levantar PostgreSQL:

```powershell
docker compose up -d postgres
docker compose ps
```

5. Arrancar la aplicación con el perfil de desarrollo:

```powershell
$env:SPRING_PROFILES_ACTIVE="desarrollo"
.\mvnw.cmd spring-boot:run
```

6. Abrir `http://localhost:8080/swagger-ui.html` y consultar la salud en
   `http://localhost:8080/actuator/health`.

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

## Flujo mínimo

```powershell
$token = (Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/autenticacion/iniciar-sesion `
  -ContentType 'application/json' `
  -Body '{"usuario":"demo","clave":"demo"}').token

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/solicitudes `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType 'application/json' `
  -Body '{"tipoOperacion":"NORMAL","monto":25000000,"moneda":"PYG","descripcion":"Pago de servicios","justificacion":"Operación autorizada para pruebas"}'
```

Una solicitud de 25.000.000 PYG queda en `PENDIENTE_APROBACION` con una tarea de
Supervisor. Una de 5.000.000 no requiere aprobaciones y el trabajador la deja en
`EJECUTADA`. Los montos, roles y bordes están en [`docs/pruebas.md`](docs/pruebas.md).

## Perfiles

| Perfil | Uso | Trabajador | Usuarios demo | Simuladores |
|---|---|---|---|---|
| `desarrollo` | Uso local y Swagger | Habilitado | Sí | Sí |
| `pruebas` | Suite y demostración | Deshabilitado salvo override | Sí | Sí |
| Sin perfil | Producción | Según variable | No | No |

Variables principales:

| Variable | Para qué |
|---|---|
| `JWT_SECRETO` | Firma del token; obligatoria y de al menos 32 bytes. |
| `DEMO_CLAVE` | Clave de los usuarios demo. |
| `PROVEEDOR_FINANCIERO_SECRETO` | Clave del simulador financiero. |
| `RIESGO_EXTERNO_SECRETO` | Firma HMAC del riesgo externo. |
| `EJECUCION_TRABAJADOR_HABILITADO` | Enciende el trabajador de ejecución. |
| `APROBACION_DURACION_NOTIFICACION` | Plazo de notificación; por defecto `PT24H`. |
| `APROBACION_DURACION_REASIGNACION` | Plazo de reasignación; por defecto `PT48H`. |
| `RIESGO_EXTERNO_VENCIMIENTO` | Vencimiento de la evaluación externa; por defecto `PT24H`. |

El detalle de cada variable está en [`.env.example`](.env.example).

## Pruebas

```powershell
# Suite completa; requiere Docker
.\mvnw.cmd clean verify

# Una clase dirigida
.\mvnw.cmd "-Dtest=PruebaCancelacionCompensacionTest" test

# Varias clases
.\mvnw.cmd "-Dtest=PruebaConcurrenciaAprobacionTest,PruebaConcurrenciaCancelacionTest" test
```

La suite usa PostgreSQL 16 con Testcontainers y Flowable real. Los reportes
quedan en `target/surefire-reports/`.

## Modo demostración

Para mostrar tiempos acelerados se usa el perfil `pruebas` con overrides:

```powershell
$env:SPRING_PROFILES_ACTIVE="pruebas"
$env:EJECUCION_TRABAJADOR_HABILITADO="true"
$env:APROBACION_DURACION_NOTIFICACION="PT10S"
$env:APROBACION_DURACION_REASIGNACION="PT20S"
$env:RIESGO_EXTERNO_VENCIMIENTO="PT15S"
.\mvnw.cmd spring-boot:run
```

Los valores por defecto de producción siguen siendo `PT24H` y `PT48H`. Los
tiempos acelerados son solo para la demostración.

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/api.md`](docs/api.md) | Rutas, roles, cuerpos, respuestas y errores. |
| [`docs/api/openapi.json`](docs/api/openapi.json) | Contrato OpenAPI generado. |
| [`docs/arquitectura.md`](docs/arquitectura.md) | Capas, transacción, componentes y estados. |
| [`docs/bpmn/README.md`](docs/bpmn/README.md) | Proceso, tareas, compuertas y temporizadores. |
| [`docs/base-de-datos.md`](docs/base-de-datos.md) | Migraciones, catálogos y restricciones. |
| [`docs/escenarios.md`](docs/escenarios.md) | Recorridos de negocio, datos demo y limpieza. |
| [`docs/patrones.md`](docs/patrones.md) | Patrones aplicados y descartados. |
| [`docs/decisiones/README.md`](docs/decisiones/README.md) | Decisiones de arquitectura. |
| [`docs/pruebas.md`](docs/pruebas.md) | Pruebas automatizadas y evidencia. |
| [`docs/tecnologias.md`](docs/tecnologias.md) | Tecnologías y alternativas. |
| [`docs/limitaciones.md`](docs/limitaciones.md) | Alcance real y pendientes. |

## Estructura del código

| Paquete | Responsabilidad |
|---|---|
| `api` | Controladores, contratos, errores y configuración de OpenAPI. |
| `aplicacion` | Casos de uso, transacciones y puertos. |
| `dominio` | Modelo, estados, reglas y eventos. |
| `infraestructura` | Persistencia, BPMN, integraciones, seguridad y simuladores. |

El paquete raíz es `com.workflow.financiero`.

## Limitaciones

El sistema no mueve dinero real, no tiene multimoneda, no incluye una interfaz
gráfica y no migra instancias BPMN en vuelo. La lista completa, con motivo y
siguiente paso, está en [`docs/limitaciones.md`](docs/limitaciones.md).

## Solución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| La aplicación no arranca | Falta `JWT_SECRETO` o tiene menos de 32 bytes | Definirlo en `.env` con el comando del inicio rápido. |
| El simulador responde `401` | `PROVEEDOR_FINANCIERO_SECRETO` vacío | Asignar un valor no vacío y reiniciar. |
| El riesgo externo no avanza | `RIESGO_EXTERNO_SECRETO` vacío | Asignar un valor no vacío y reiniciar. |
| `docker compose` falla | Docker Desktop apagado | Abrirlo y esperar el motor. |
| El puerto 8080 está ocupado | Otro proceso escucha en ese puerto | Detenerlo o cambiar `server.port`. |
| Hibernate reporta esquema inválido | Base con un esquema viejo | Recrear la base con `docker compose down -v` y volver a levantar. |
