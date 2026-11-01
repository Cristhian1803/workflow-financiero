# API REST

La API expone el registro, la revisión, la aprobación, la ejecución y la
cancelación de solicitudes financieras. Todas las rutas usan JSON y viven bajo
`/api/v1`. La documentación interactiva está en `/swagger-ui.html` y el contrato
generado en `/v3/api-docs`.

## Convenciones

| Tema | Regla |
|---|---|
| Autenticación | `Authorization: Bearer <token>` con JWT emitido por `POST /api/v1/autenticacion/iniciar-sesion`. |
| Correlación | Cabecera opcional `X-Identificador-Correlacion`. Si falta o es inválida, el servidor genera una. |
| Errores | Formato `ProblemDetail` con `type`, `title`, `status`, `detail`, `instance`, `codigo`, `identificadorCorrelacion` y, en validaciones, `errores`. |
| Fechas | ISO-8601 en UTC, por ejemplo `2026-10-31T12:00:00Z`. |
| Montos | Enteros en guaraníes (`PYG`), sin decimales. |
| Identidad | El servidor toma el usuario del token. El cliente no envía solicitante, rol, estado ni resultado. |

### Códigos de error

| Código | HTTP | Cuándo aparece |
|---|---|---|
| `CREDENCIALES_INVALIDAS` | 401 | Usuario o clave incorrectos. |
| `NO_AUTENTICADO` | 401 | Falta el token, es inválido o la firma externa no es válida. |
| `ACCESO_DENEGADO` | 403 | El rol no permite la operación. |
| `DATOS_INVALIDOS` | 400 | Cuerpo, parámetros o paginación inválidos. |
| `JSON_INVALIDO` | 400 | El cuerpo no es JSON válido. |
| `SOLICITUD_INVALIDA` | 400 | La solicitud no cumple las reglas de negocio. |
| `RECURSO_NO_ENCONTRADO` | 404 | El recurso no existe o no es visible para el actor. |
| `TAREA_NO_ENCONTRADA` | 404 | La tarea de aprobación no existe o no es visible. |
| `TAREA_REVISION_NO_ENCONTRADA` | 404 | La tarea de revisión de riesgo no existe. |
| `TAREA_YA_RESUELTA` | 409 | La tarea de aprobación ya fue resuelta. |
| `REVISION_RIESGO_YA_RESUELTA` | 409 | La revisión manual ya fue resuelta o no corresponde. |
| `TRANSICION_TAREA_INVALIDA` | 409 | La tarea no admite la transición solicitada. |
| `APROBACION_NO_PERMITIDA` | 403 | El actor no puede aprobar esa tarea: rol insuficiente o es el creador. |
| `SEGREGACION_FUNCIONES` | 403 | El actor intenta aprobar dos roles funcionales distintos en la misma solicitud. |
| `CONFLICTO_DATOS` | 409 | Conflicto de idempotencia o de estado. |
| `INCIDENTE_NO_ENCONTRADO` | 404 | El incidente no existe o no es visible. |
| `REINTENTO_NO_PERMITIDO` | 409 | El incidente no admite reintento. |
| `EJECUCION_NO_PERMITIDA` | 409 | La revisión no está lista para ejecutar. |
| `CANCELACION_NO_PERMITIDA` | 409 | La revisión es terminal o el efecto ya es irreversible. |
| `EVENTO_EXTERNO_INVALIDO` | 400 | Fecha, cuerpo o cabeceras del evento inválidos. |
| `EVENTO_EXTERNO_EN_CONFLICTO` | 409 | Mismo identificador de evento con otro contenido. |
| `REENVIO_NO_PERMITIDO` | 422 | La revisión no está rechazada, venció el plazo o se agotaron las revisiones. |
| `SERVICIO_NO_DISPONIBLE` | 503 | El proveedor simulado no responde. |
| `ERROR_INTERNO` | 500 | Error inesperado, sin detalles internos. |

## Resumen de rutas

| Método | Ruta | Rol | Propósito |
|---|---|---|---|
| POST | `/api/v1/autenticacion/iniciar-sesion` | Público | Emitir el token JWT. |
| GET | `/api/v1/autenticacion/yo` | Autenticado | Consultar el usuario del token. |
| POST | `/api/v1/solicitudes` | `SOLICITANTE` | Crear una solicitud e iniciar el proceso. |
| GET | `/api/v1/solicitudes` | `SOLICITANTE` o `ADMINISTRADOR` | Listar solicitudes visibles. |
| GET | `/api/v1/solicitudes/{id}` | Dueño, administrador o aprobador | Consultar el detalle. |
| GET | `/api/v1/solicitudes/{id}/historial` | Dueño, auditor o administrador | Consultar los eventos. |
| GET | `/api/v1/solicitudes/{id}/proceso` | Dueño, administrador o aprobador | Consultar el estado del proceso. |
| POST | `/api/v1/solicitudes/{id}/reenvios` | Dueño | Reenviar una solicitud rechazada. |
| POST | `/api/v1/solicitudes/{id}/cancelar` | Dueño o `ADMINISTRADOR` | Cancelar y compensar si hace falta. |
| GET | `/api/v1/tareas-aprobacion` | Roles de aprobación | Listar tareas pendientes. |
| POST | `/api/v1/tareas-aprobacion/{id}/aprobar` | Roles de aprobación | Aprobar una tarea. |
| POST | `/api/v1/tareas-aprobacion/{id}/rechazar` | Roles de aprobación | Rechazar una tarea. |
| GET | `/api/v1/tareas-revision-riesgo` | `ANALISTA_RIESGOS`, `JEFE_RIESGOS` | Listar revisiones manuales. |
| POST | `/api/v1/tareas-revision-riesgo/{id}/resolver` | `ANALISTA_RIESGOS`, `JEFE_RIESGOS` | Resolver una revisión manual. |
| GET | `/api/v1/notificaciones` | Autenticado | Listar notificaciones del actor. |
| GET | `/api/v1/incidentes` | `OPERACIONES`, `ADMINISTRADOR` | Listar incidentes abiertos. |
| POST | `/api/v1/incidentes/{id}/reintentar` | `OPERACIONES`, `ADMINISTRADOR` | Reintentar un incidente. |
| POST | `/api/v1/integraciones/riesgos/notificacion` | Firma HMAC | Recibir el riesgo externo. |
| POST | `/api/v1/integraciones/proveedor-financiero/*` | `X-Clave-Integracion` | Simulador, solo desarrollo y pruebas. |

## Autenticación

### Iniciar sesión

```http
POST /api/v1/autenticacion/iniciar-sesion
Content-Type: application/json
```

```json
{
  "usuario": "demo",
  "clave": "demo"
}
```

Respuesta `200`:

```json
{
  "token": "<jwt>",
  "tipo": "Bearer",
  "expiraEn": "2026-10-31T13:00:00Z"
}
```

Errores: `400 DATOS_INVALIDOS`, `401 CREDENCIALES_INVALIDAS`.

### Usuario actual

```http
GET /api/v1/autenticacion/yo
Authorization: Bearer <token>
```

```json
{
  "id": "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f",
  "usuario": "demo",
  "nombre": "Usuario Demo",
  "roles": ["SOLICITANTE"],
  "perfilRiesgo": "NORMAL"
}
```

## Solicitudes

### Crear

```http
POST /api/v1/solicitudes
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "tipoOperacion": "NORMAL",
  "monto": 25000000,
  "moneda": "PYG",
  "descripcion": "Pago de servicios",
  "justificacion": "Operación autorizada para pruebas"
}
```

Respuesta `201` con `Location: /api/v1/solicitudes/{id}`. El cuerpo es un
`SolicitudRespuesta`:

```json
{
  "id": "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f",
  "idRevision": "0b3c4d5e-6f70-4a1b-8c2d-3e4f5a6b7c8d",
  "revision": 1,
  "solicitante": "demo",
  "tipoOperacion": "NORMAL",
  "monto": 25000000,
  "moneda": "PYG",
  "descripcion": "Pago de servicios",
  "justificacion": "Operación autorizada para pruebas",
  "estado": "PENDIENTE_APROBACION",
  "nivelRiesgo": "BAJO",
  "aprobacionesRequeridas": ["SUPERVISOR"],
  "requiereEvaluacionExterna": false,
  "fechaCreacion": "2026-10-31T12:00:00Z",
  "fechaActualizacion": "2026-10-31T12:00:00Z"
}
```

Validaciones: `tipoOperacion` debe existir y estar activo; `monto` debe ser
positivo y respetar los bordes de las políticas; `moneda` solo acepta `PYG`;
`descripcion` admite hasta 500 caracteres y `justificacion` hasta 1000.

La revisión queda en `PENDIENTE_APROBACION` si el plan requiere aprobaciones o
en `EJECUTANDO` si no las requiere. La ejecución financiera la realiza el
trabajador, no la petición HTTP.

Errores: `400 DATOS_INVALIDOS`, `400 SOLICITUD_INVALIDA`, `401`, `403`.

### Listar

```http
GET /api/v1/solicitudes?pagina=0&tamano=20&estado=PENDIENTE_APROBACION
Authorization: Bearer <token>
```

`pagina` empieza en cero y `tamano` admite entre 1 y 100. `estado` es opcional.
El administrador ve todas y un solicitante ve solo las propias; los demás roles
reciben `403`. El orden es por fecha de creación descendente.

```json
{
  "contenido": [
    {
      "id": "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f",
      "revision": 1,
      "solicitante": "demo",
      "tipoOperacion": "NORMAL",
      "monto": 25000000,
      "moneda": "PYG",
      "estado": "PENDIENTE_APROBACION",
      "nivelRiesgo": "BAJO",
      "fechaCreacion": "2026-10-31T12:00:00Z"
    }
  ],
  "pagina": 0,
  "tamano": 20,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

### Detalle

```http
GET /api/v1/solicitudes/{id}
```

Devuelve el mismo `SolicitudRespuesta` de la creación. Pueden consultarla el
dueño, un administrador o un aprobador con tarea en la revisión actual. Un
solicitante sin visibilidad recibe `404` y un rol sin permiso recibe `403`.

### Historial

```http
GET /api/v1/solicitudes/{id}/historial
```

```json
[
  {
    "id": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
    "revision": 1,
    "tipo": "SOLICITUD_CREADA",
    "actor": "demo",
    "rol": "SOLICITANTE",
    "estadoAnterior": "RECIBIDA",
    "estadoNuevo": "VALIDANDO",
    "detalle": "Solicitud registrada",
    "identificadorCorrelacion": "corr-manual-123",
    "fecha": "2026-10-31T12:00:00Z"
  }
]
```

El historial es de solo adición: no hay rutas para modificarlo ni borrarlo.

### Proceso

```http
GET /api/v1/solicitudes/{id}/proceso
```

```json
{
  "idSolicitud": "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f",
  "revision": 1,
  "identificadorProceso": "25001",
  "iniciado": true,
  "finalizado": false,
  "estadoRevision": "PENDIENTE_APROBACION",
  "actividadesActivas": ["tareaSupervisor"],
  "tareas": [
    {
      "id": "tarea-1",
      "rol": "SUPERVISOR",
      "nombre": "Aprobación de Supervisor",
      "estado": "PENDIENTE",
      "asignadoA": null,
      "fechaVencimiento": "2026-11-01T12:00:00Z"
    }
  ]
}
```

Si el proceso ya terminó, `finalizado` es `true` y la información se reconstruye
desde el historial.

### Reenviar

```http
POST /api/v1/solicitudes/{id}/reenvios
```

El cuerpo es igual al de la creación. Solo el dueño puede reenviar una revisión
`RECHAZADA`, dentro de los 30 días y con un máximo de tres revisiones. Crea una
revisión y un proceso nuevos, recalcula riesgo y plan, y conserva el historial
anterior. Respuesta `201`.

Errores: `403` si el actor no es el dueño, `404` si no es visible, `409` si la
revisión no está rechazada, `422 REENVIO_NO_PERMITIDO` si venció el plazo o se
agotaron las revisiones.

### Cancelar

```http
POST /api/v1/solicitudes/{id}/cancelar
```

```json
{
  "motivo": "El cliente desistió de la operación"
}
```

Solo el dueño o un administrador pueden cancelar. Respuestas:

| Situación | HTTP | Cuerpo |
|---|---|---|
| Sin reserva ni despacho en curso | `200` | `estadoRevision: CANCELADA`, `compensacionPendiente: false` |
| Reserva liberada antes de confirmar | `200` | `estadoRevision: CANCELADA` |
| Confirmación en tránsito o desconocida | `202` | `estadoRevision: COMPENSANDO`, `compensacionPendiente: true` |
| Confirmación ya efectiva | `409` | `CANCELACION_NO_PERMITIDA` |
| Liberación fallida sin resolución segura | `202` | `COMPENSANDO` e incidente de compensación |

Nunca se declara `CANCELADA` antes de liberar una reserva. Una reserva
confirmada no se libera.

## Tareas de aprobación

### Bandeja

```http
GET /api/v1/tareas-aprobacion
Authorization: Bearer <token>
```

Devuelve solo las tareas `PENDIENTE` de la revisión actual visibles para el
actor: las asignadas y las de sus roles funcionales o delegados. El creador no
ve sus propias tareas.

```json
[
  {
    "id": "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f",
    "idSolicitud": "8a7f6e5d-4c3b-2a1f-0e9d-8c7b6a5f4e3d",
    "idRevision": "7b6a5f4e-3d2c-1b0a-9f8e-7d6c5b4a3f2e",
    "revision": 1,
    "rol": "SUPERVISOR",
    "estado": "PENDIENTE",
    "asignadoA": null,
    "fechaCreacion": "2026-10-31T12:00:00Z",
    "fechaNotificacion": "2026-11-01T12:00:00Z",
    "fechaVencimiento": "2026-11-02T12:00:00Z",
    "solicitante": "demo",
    "tipoOperacion": "NORMAL",
    "monto": 25000000,
    "moneda": "PYG",
    "nivelRiesgo": "BAJO",
    "descripcion": "Pago de servicios",
    "justificacion": "Operación autorizada para pruebas"
  }
]
```

### Aprobar

```http
POST /api/v1/tareas-aprobacion/{id}/aprobar
```

```json
{
  "comentario": "Aprobado para continuar"
}
```

El comentario es opcional y admite hasta 1000 caracteres. La aprobación, el
evento, el rol funcional y el avance de Flowable se registran en la misma
transacción. El creador no puede aprobar y un actor no puede cubrir dos roles
funcionales distintos en la misma solicitud.

Respuesta `200`:

```json
{
  "idTarea": "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f",
  "decision": "APROBADA",
  "fecha": "2026-10-31T12:00:00Z",
  "estadoTarea": "APROBADA",
  "estadoRevision": "PENDIENTE_APROBACION",
  "delegado": false
}
```

### Rechazar

```http
POST /api/v1/tareas-aprobacion/{id}/rechazar
```

```json
{
  "motivo": "El monto no coincide con la documentación"
}
```

El motivo es obligatorio y admite hasta 1000 caracteres. El rechazo completa la
tarea, cancela las demás tareas pendientes de la revisión y cierra el proceso.
Respuesta `200` con el mismo contrato de decisión y `decision: RECHAZADA`.

Errores de ambas rutas: `403 APROBACION_NO_PERMITIDA` o `SEGREGACION_FUNCIONES`,
`404 TAREA_NO_ENCONTRADA`, `409 TAREA_YA_RESUELTA` o
`TRANSICION_TAREA_INVALIDA`.

## Revisión manual de riesgo

Se abre cuando vence el temporizador de la evaluación externa. Solo la ven y la
resuelven `ANALISTA_RIESGOS` y `JEFE_RIESGOS`; el creador no resuelve su propia
revisión.

```http
GET /api/v1/tareas-revision-riesgo
POST /api/v1/tareas-revision-riesgo/{id}/resolver
```

```json
{
  "nivelRiesgo": "ALTO",
  "motivo": "El proveedor externo no respondió en el plazo",
  "evidencia": "Consulta y registro de incidente"
}
```

`nivelRiesgo` solo acepta `BAJO`, `MEDIO` o `ALTO`. La resolución registra
actor, fecha, nivel, motivo y evidencia, consolida el máximo con el riesgo
interno, recalcula el plan y devuelve la revisión a `PENDIENTE_APROBACION`. No
crea aprobaciones ni exime de ellas.

Respuesta `200`:

```json
{
  "idTarea": "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f",
  "estadoTarea": "RESUELTA",
  "estadoRevision": "PENDIENTE_APROBACION",
  "nivelRiesgo": "ALTO",
  "fecha": "2026-10-31T12:00:00Z"
}
```

Errores: `400 DATOS_INVALIDOS`, `403`, `404 TAREA_REVISION_NO_ENCONTRADA`,
`409 REVISION_RIESGO_YA_RESUELTA` o `CONFLICTO_DATOS` si el plan recalculado no
coincide.

## Notificaciones

```http
GET /api/v1/notificaciones
```

Devuelve las notificaciones del actor o de sus roles activos, ordenadas por
fecha de creación descendente.

```json
[
  {
    "id": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
    "tipo": "ESCALAMIENTO_24H",
    "contenido": "La tarea de aprobación de SUPERVISOR lleva 24 horas pendiente",
    "estado": "PENDIENTE",
    "idTarea": "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f",
    "idSolicitud": "8a7f6e5d-4c3b-2a1f-0e9d-8c7b6a5f4e3d",
    "revision": 1,
    "fechaCreacion": "2026-11-01T12:00:00Z",
    "fechaLectura": null
  }
]
```

El estado de lectura todavía no se modifica desde la API.

## Incidentes

```http
GET /api/v1/incidentes
POST /api/v1/incidentes/{id}/reintentar
```

Solo `OPERACIONES` y `ADMINISTRADOR`. El reintento exige motivo y conserva la
clave y la huella financiera; revalida la autorización antes de repetir el
efecto.

```json
{
  "motivo": "El proveedor ya está disponible"
}
```

| Resultado | HTTP |
|---|---|
| Incidente resuelto | `200` |
| Reintento aceptado con resultado aún incierto | `202` |
| Incidente no reintentable | `409 REINTENTO_NO_PERMITIDO` |

```json
{
  "id": "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f",
  "idSolicitud": "8a7f6e5d-4c3b-2a1f-0e9d-8c7b6a5f4e3d",
  "idRevision": "7b6a5f4e-3d2c-1b0a-9f8e-7d6c5b4a3f2e",
  "tipo": "EJECUCION",
  "referencia": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "estado": "ABIERTO",
  "motivo": "Resultado desconocido del proveedor",
  "actor": "operaciones",
  "reintentable": true,
  "reintentos": 1,
  "ultimoResultado": "La consulta no confirmó el efecto",
  "fechaCreacion": "2026-10-31T12:00:00Z",
  "fechaActualizacion": "2026-10-31T12:05:00Z"
}
```

## Riesgo externo

```http
POST /api/v1/integraciones/riesgos/notificacion
```

No usa JWT: se autentica con firma HMAC. Cabeceras obligatorias:

```text
X-Proveedor
X-Identificador-Evento
X-Timestamp
X-Firma
```

La firma es HMAC-SHA256 sobre `proveedor + "\n" + identificadorEvento + "\n" +
timestamp + "\n" + cuerpoCrudo`. La marca de tiempo debe estar dentro de la
ventana configurada.

```json
{
  "proveedor": "proveedor-riesgo-simulado",
  "identificadorEvento": "evt-123",
  "identificadorSolicitud": "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f",
  "numeroRevision": 1,
  "nivelRiesgo": "ALTO",
  "fechaProveedor": "2026-10-31T12:00:00Z",
  "resultado": "Evaluación externa de demostración"
}
```

| Situación | HTTP | `estado` |
|---|---|---|
| Evento aplicado | `200` | `PROCESADO` |
| Duplicado idéntico | `200` | `DUPLICADO` |
| Evento de una revisión obsoleta | `200` | `OBSOLETO` |
| Proceso todavía no espera | `202` | `PENDIENTE` |
| Firma ausente o inválida | `401` | — |
| Fecha o cuerpo inválidos | `400` | — |
| Mismo identificador con otra huella | `409` | — |

El detalle del contrato y de la correlación está en
[`integraciones/riesgo-externo-simulado.md`](integraciones/riesgo-externo-simulado.md).

## Proveedor financiero simulado

Solo existe en los perfiles `desarrollo` y `pruebas`. No mueve dinero real y no
se expone en producción. Exige la cabecera `X-Clave-Integracion`.

| Método | Ruta | Efecto |
|---|---|---|
| POST | `/api/v1/integraciones/proveedor-financiero/reservas` | Reserva. |
| POST | `/api/v1/integraciones/proveedor-financiero/confirmaciones` | Confirma la reserva. |
| POST | `/api/v1/integraciones/proveedor-financiero/liberaciones` | Libera una reserva no confirmada. |
| GET | `/api/v1/integraciones/proveedor-financiero/operaciones/{clave}` | Consulta por clave. |

```json
{
  "solicitudId": "00000000-0000-0000-0000-000000000001",
  "revisionId": "00000000-0000-0000-0000-000000000002",
  "accion": "RESERVA",
  "clave": "reserva:00000000-0000-0000-0000-000000000001:00000000-0000-0000-0000-000000000002",
  "huella": "64 caracteres hexadecimales",
  "monto": 25000000,
  "moneda": "PYG",
  "tipoOperacion": "NORMAL"
}
```

El contrato completo, la idempotencia y los modos de fallo están en
[`integraciones/proveedor-financiero-simulado.md`](integraciones/proveedor-financiero-simulado.md).

## Endpoint de demostración

`/api/v1/solicitudes-prueba` es el circuito mínimo del motor que se usó para
verificar la transacción compartida. Solo existe en `desarrollo` y `pruebas`, no
se documenta en OpenAPI y no debe usarse para operaciones reales. El código se
conserva como referencia.

## OpenAPI y Swagger

- Swagger UI: `/swagger-ui.html`.
- Contrato JSON: `/v3/api-docs`.
- Copia versionada: [`api/openapi.json`](api/openapi.json).

En `desarrollo` y `pruebas` la documentación es pública para facilitar la
revisión. En producción queda detrás de la autenticación. El contrato incluye el
esquema `bearerAuth` y el error uniforme `ErrorApiRespuesta`.

La copia versionada se genera desde la aplicación en ejecución. Las rutas del
simulador aparecen porque el contrato se exporta en un perfil no productivo; el
endpoint de demostración no aparece.
