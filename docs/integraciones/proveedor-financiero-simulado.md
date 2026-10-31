# Contrato del proveedor financiero simulado

Este documento describe el proveedor de ejecución financiera de demostración.
No mueve dinero real y solo existe en los perfiles `desarrollo` y `pruebas`.

## Operaciones

| Operación | Ruta | Efecto |
|---|---|---|
| Reservar | `POST /api/v1/integraciones/proveedor-financiero/reservas` | Crea una reserva para una revisión |
| Confirmar | `POST /api/v1/integraciones/proveedor-financiero/confirmaciones` | Confirma la reserva y marca el punto irreversible |
| Consultar | `GET /api/v1/integraciones/proveedor-financiero/operaciones/{clave}` | Recupera el estado por clave |
| Liberar | `POST /api/v1/integraciones/proveedor-financiero/liberaciones` | Libera una reserva aún no confirmada |

Las rutas exigen la cabecera `X-Clave-Integracion`. El valor se configura por
ambiente y no se registra en logs.

## Identidad y claves

Cada operación identifica:

- `solicitudId`;
- `revisionId`;
- `accion` (`RESERVA`, `CONFIRMACION` o `LIBERACION`);
- `clave` estable;
- `huella` de los parámetros.

Las claves no contienen secretos:

```text
reserva:{solicitudId}:{revisionId}
confirmacion:{solicitudId}:{revisionId}
liberacion:{solicitudId}:{revisionId}
```

La huella es SHA-256 en hexadecimal sobre la solicitud, la revisión, la acción,
el monto, la moneda y el tipo de operación. Un reintento debe conservar la misma
clave y la misma huella.

## Petición

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

La acción del cuerpo debe coincidir con la ruta. La clave debe corresponder a la
identidad estable y la huella a los parámetros enviados.

## Respuesta

```json
{
  "id": "00000000-0000-0000-0000-000000000003",
  "clave": "reserva:00000000-0000-0000-0000-000000000001:00000000-0000-0000-0000-000000000002",
  "accion": "RESERVA",
  "estado": "RESERVADA",
  "referencia": "RES-00000000-0000-0000-0000-000000000004",
  "huella": "64 caracteres hexadecimales",
  "actualizadoEn": "2026-10-31T12:00:00Z"
}
```

## Idempotencia y conflictos

| Caso | Resultado |
|---|---|
| Misma clave y misma huella | Devuelve el mismo resultado, sin repetir el efecto |
| Misma clave y huella distinta | `409 CONFLICTO_DATOS`, sin efecto nuevo |
| Clave distinta para la misma intención y acción | `400 DATOS_INVALIDOS`; además existe una restricción única por intención y acción |
| Consulta por clave existente | Devuelve el estado persistido |
| Consulta por clave inexistente | `404 RECURSO_NO_ENCONTRADO` |

## Estados

| Acción | Estado inicial | Resultado |
|---|---|---|
| `RESERVA` | No existe | `RESERVADA` |
| `CONFIRMACION` | Reserva `RESERVADA` | Reserva `CONFIRMADA` |
| `LIBERACION` | Reserva `RESERVADA` | Reserva `LIBERADA` |
| `CONFIRMACION` | Reserva `CONFIRMADA` o `LIBERADA` | `409`, sin efecto |
| `LIBERACION` | Reserva `CONFIRMADA` o `LIBERADA` | `409`, sin efecto |

La confirmación es el punto irreversible. Una reserva confirmada no puede
liberarse: la aplicación debe rechazar la cancelación posterior, no simular una
compensación de un efecto irreversible.

## Modos de fallo

El simulador soporta estos fallos controlados:

- `FALLO_ANTES_DEL_EFECTO`;
- `RESPUESTA_PERDIDA`;
- `CONSULTA_NO_DISPONIBLE`;
- `LIBERACION_FALLIDA`.

La configuración de fallos es en memoria y solo está disponible en el perfil
`pruebas` mediante:

```text
POST   /api/v1/integraciones/proveedor-financiero/pruebas/fallos
DELETE /api/v1/integraciones/proveedor-financiero/pruebas/fallos
```

El cuerpo del `POST` es:

```json
{
  "clave": "confirmacion:...:...",
  "modo": "RESPUESTA_PERDIDA"
}
```

Una respuesta perdida aplica el efecto y luego devuelve error, de modo que una
consulta posterior recupera el resultado. Un fallo antes del efecto no crea
ninguna operación.

## Adaptador HTTP

El adaptador de la aplicación usa `RestClient` y HTTP/1.1. Configura:

- URL base;
- tiempo de conexión;
- tiempo de lectura;
- secreto de integración.

Traducción de errores:

| Respuesta | Excepción |
|---|---|
| `409` | `ConflictoProveedorFinancieroException` |
| `5xx`, timeout o conexión | `ErrorRecuperableProveedorFinancieroException` |
| `4xx` | `ErrorDefinitivoProveedorFinancieroException` |
| Respuesta `2xx` inválida | `ErrorDefinitivoProveedorFinancieroException` |

El adaptador no reintenta automáticamente. La política de reintentos y
reconciliación pertenece al trabajador de ejecución.

## Uso desde la aplicación

Antes de llamar al proveedor, la aplicación persiste `intento_ejecucion` con la
clave financiera `solicitudId:numeroRevision` y la huella de la confirmación. El
trabajador reclama esa intención, reserva y confirma con las claves de acción
estables y registra el resultado en otra transacción corta.

La reserva y la confirmación se envían con la misma identidad y los mismos
parámetros. Si el trabajador se reinicia después de reclamar, la intención queda
`ENVIANDO`; la reconciliación y los incidentes se implementan en la sesión
siguiente. No se cambia la clave ni la huella para reintentar.

## Configuración

```yaml
integracion:
  proveedor-financiero:
    url-base: ${PROVEEDOR_FINANCIERO_URL:http://localhost:8080/api/v1/integraciones/proveedor-financiero}
    tiempo-conexion: ${PROVEEDOR_FINANCIERO_TIEMPO_CONEXION:PT2S}
    tiempo-lectura: ${PROVEEDOR_FINANCIERO_TIEMPO_LECTURA:PT5S}
    secreto: ${PROVEEDOR_FINANCIERO_SECRETO:}
```

En producción se debe configurar una URL y un secreto reales. El simulador no se
expone en ese perfil.

## Persistencia y retención

El estado se guarda en `operacion_simulada`. La tabla es independiente de las
tablas de negocio y no tiene claves foráneas hacia ellas.

No se aplica TTL ni limpieza automática. Una clave debe conservarse durante todo
el período de reintento y reconciliación. Un proveedor real debe ofrecer las
mismas garantías de idempotencia y consulta antes de conectarse.
