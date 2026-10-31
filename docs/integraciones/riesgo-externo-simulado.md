# Riesgo externo simulado

La evaluación externa se solicita para montos superiores a 200.000.000 PYG. La
aplicación persiste la intención en `evaluacion_riesgo` antes de enviarla y
mantiene una clave estable por revisión:

```text
riesgo:{solicitudId}:{revisionId}
```

El proveedor simulado registra la solicitud y devuelve una referencia. La
notificación asíncrona se representa llamando al endpoint HTTP descrito abajo.

## Notificación

```text
POST /api/v1/integraciones/riesgos/notificacion
```

Cabeceras obligatorias:

```text
X-Proveedor
X-Identificador-Evento
X-Timestamp
X-Firma
```

Cuerpo:

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

`nivelRiesgo` solo acepta `BAJO`, `MEDIO` o `ALTO`. `fechaProveedor` debe ser
un instante ISO-8601 dentro de la ventana configurada.

## Firma

La firma es HMAC-SHA256 sobre este texto canónico:

```text
proveedor + "\n" +
identificadorEvento + "\n" +
timestamp + "\n" +
cuerpoCrudo
```

`cuerpoCrudo` son los bytes exactos enviados, sin reserializar. La firma se
compara en tiempo constante. `X-Timestamp` también debe estar dentro de la
ventana.

Configuración:

```yaml
flujo:
  riesgo-externo:
    proveedor: ${RIESGO_EXTERNO_PROVEEDOR:proveedor-riesgo-simulado}
    secreto: ${RIESGO_EXTERNO_SECRETO:}
    ventana-tiempo: ${RIESGO_EXTERNO_VENTANA_TIEMPO:PT5M}
    reintento: ${RIESGO_EXTERNO_REINTENTO:PT5S}
    maximo-intentos: ${RIESGO_EXTERNO_MAXIMO_INTENTOS:3}
```

Si el secreto está vacío, el endpoint rechaza todas las notificaciones. No se
guardan secretos ni firmas completas.

## Respuestas

| Situación | HTTP | Cuerpo |
|---|---|---|
| Evento aplicado | `200` | `estado: PROCESADO` |
| Duplicado idéntico | `200` | `estado: DUPLICADO` |
| Revisión anterior o terminal | `200` | `estado: OBSOLETO` |
| Proceso todavía no espera | `202` | `estado: PENDIENTE` |
| Firma ausente o inválida | `401` | `NO_AUTENTICADO` |
| Fecha o cuerpo inválidos | `400` | `EVENTO_EXTERNO_INVALIDO` o `DATOS_INVALIDOS` |
| Mismo identificador con otra huella | `409` | `EVENTO_EXTERNO_EN_CONFLICTO` |

## Inbox y correlación

La aplicación guarda el evento en `evento_externo` antes de intentar
correlacionarlo. El estado de procesamiento es:

```text
PENDIENTE, PROCESADO, DUPLICADO, CONFLICTO, OBSOLETO
```

La correlación usa `identificadorSolicitud` y `numeroRevision`. Las FKs de
solicitud y revisión pueden quedar nulas mientras el evento espera. Un evento
temprano queda pendiente y se reintenta sin consumir intentos fallidos. Un
evento de una revisión anterior, cancelada, compensando, en revisión manual o
terminal queda obsoleto.

Cuando la revisión es la actual y el proceso espera `RIESGO_EVALUADO`:

1. se bloquea la evaluación externa vigente;
2. se consolida el máximo entre riesgo interno y externo;
3. se actualiza el nivel de la revisión;
4. se marca la evaluación `RECIBIDA`;
5. se registra `EVALUACION_EXTERNA_RESUELTA`;
6. se marca el evento `PROCESADO`;
7. se envía `RIESGO_EVALUADO` a la instancia específica.

La actualización y el envío del mensaje ocurren en la misma transacción corta.
Si el envío falla, se revierte y el evento queda pendiente para reintento.

El delegate `verificarRiesgoExterno` lee la evaluación persistida y comprueba el
identificador del evento. No confía en variables del mensaje y no crea
aprobaciones.

## Reintentos

El trabajador reclama las evaluaciones externas pendientes con `SKIP LOCKED`,
llama al proveedor fuera de la transacción y guarda la referencia. Si el envío
falla, agenda un reintento con la misma clave. Al agotar los intentos deja de
programar; el vencimiento y la revisión manual corresponden a la etapa
siguiente.

Los eventos pendientes se reclaman con `SKIP LOCKED` y se correlacionan en una
transacción nueva. Un fallo recuperable de correlación incrementa intentos y
programa el siguiente; una espera normal no incrementa intentos.
