# ADR-008 — Riesgo externo asíncrono con inbox idempotente

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

La evaluación de riesgo externo no está disponible en el momento de crear la
solicitud. El proveedor responde por un canal asíncrono, puede duplicar un aviso,
enviar contenido distinto con el mismo identificador o responder antes de que el
proceso esté esperando. El resultado debe aplicarse una sola vez y no puede crear
aprobaciones por sí solo.

## Decisión

La integración se divide en dos mitades:

- **Salida:** `ServicioSolicitudRiesgoExterno` persiste una evaluación
  `PENDIENTE` con identidad estable y la envía por el puerto
  `PuertoProveedorRiesgo`. `TrabajadorRiesgoExterno` reintenta los envíos y las
  correlaciones pendientes.
- **Entrada:** `ServicioEventoExterno` valida la firma HMAC y la frescura, y
  guarda el evento en `evento_externo` **antes de aplicar nada**. La tabla tiene
  unicidad `(proveedor, identificador_evento)` y guarda una huella del cuerpo:
  mismo identificador y misma huella es un duplicado idempotente; misma clave y
  huella distinta es `409`. Si el proceso todavía no espera, el evento queda
  `PENDIENTE` y se correlaciona después.

El evento consolida el máximo entre riesgo interno y externo y envía
`RIESGO_EVALUADO` a la instancia. No crea ni modifica aprobaciones. Si vence el
timer, la evaluación queda `VENCIDA` y se abre una revisión manual.

## Alternativas descartadas

- **Llamada síncrona al proveedor durante la creación.** Se descartó porque la
  latencia y la disponibilidad del tercero bloquearían el registro.
- **Webhook directo al proceso, sin inbox.** Se descartó porque un evento
  temprano, duplicado o fuera de orden se perdería o rompería la correlación.
- **Confiar solo en la clave del evento, sin huella.** Se descartó porque no
  detectaría un mismo identificador con contenido distinto.

## Consecuencias

- El evento se puede recibir antes, durante o después de que el proceso espere.
- Un duplicado idéntico se acusa sin repetir el efecto.
- Un conflicto de contenido queda auditado y no cambia el proceso.
- El proveedor de riesgo es simulado; la firma y el contrato son reales.

## Dónde se ve

- [`ServicioSolicitudRiesgoExterno.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioSolicitudRiesgoExterno.java)
- [`ServicioEventoExterno.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioEventoExterno.java)
- [`EventoExternoRepositorio.java`](../../src/main/java/com/workflow/financiero/infraestructura/persistencia/EventoExternoRepositorio.java)
- [`TrabajadorRiesgoExterno.java`](../../src/main/java/com/workflow/financiero/infraestructura/ejecucion/TrabajadorRiesgoExterno.java)
- [`V4__aprobaciones_riesgo_ejecucion.sql`](../../src/main/resources/db/migration/V4__aprobaciones_riesgo_ejecucion.sql)
- [`V11__riesgo_externo_inbox.sql`](../../src/main/resources/db/migration/V11__riesgo_externo_inbox.sql)
- [`PruebaEventosRiesgoTest.java`](../../src/test/java/com/workflow/financiero/PruebaEventosRiesgoTest.java)
- [`docs/integraciones/riesgo-externo-simulado.md`](../integraciones/riesgo-externo-simulado.md)
