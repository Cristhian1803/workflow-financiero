# ADR-006 — Ejecución financiera con intención durable

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

El proveedor financiero es un servicio HTTP que puede tardar, fallar o responder
y perder la conexión. Si la llamada se hace dentro de la transacción de negocio,
se mantienen bloqueos durante la espera y una caída deja el sistema sin saber si
el efecto ocurrió. Si se hace sin registrar nada antes, un reinicio pierde la
intención.

## Decisión

La ejecución se separa en tres pasos con transacciones cortas:

1. El delegate de BPMN no llama al proveedor. Persiste un `IntentoEjecucion`
   (`PENDIENTE`) con clave y huella estables, deja la revisión en `EJECUTANDO` y
   el proceso espera el mensaje `EJECUCION_FINALIZADA`.
2. `TrabajadorEjecucion` reclama la intención con
   `FOR UPDATE SKIP LOCKED`, llama a `reservar` y `confirmar` **fuera de
   transacción** y registra el resultado en otra transacción corta.
3. Si la respuesta es incierta, el intento queda en `RESULTADO_DESCONOCIDO`, se
   crea un incidente y se consulta al proveedor con la misma clave. La
   reconciliación decide si la revisión pasa a `EJECUTADA` o queda para
   intervención.

La clave financiera es `solicitudId:numeroRevision`; la huella se calcula sobre
solicitud, revisión, acción, monto, moneda y tipo de operación.

## Alternativas descartadas

- **Llamar al proveedor desde el delegate BPMN.** Se descartó porque la
  transacción del motor quedaría abierta durante el HTTP y porque un rollback no
  revierte un efecto ya confirmado.
- **Bandeja de salida genérica con un bus.** Se descartó porque agrega
  infraestructura y no reemplaza la idempotencia del proveedor; la intención
  durable cubre el caso concreto.
- **Reintentar sin clave estable.** Se descartó porque podría duplicar una
  reserva o una confirmación.

## Consecuencias

- El proceso sobrevive a un reinicio con la intención pendiente.
- La misma clave y huella devuelven el mismo resultado; una huella distinta es un
  conflicto.
- Los reintentos son limitados y, al agotarse, crean un incidente operativo.
- El trabajador usa polling; no se probó con más de un nodo, aunque
  `SKIP LOCKED` evita el doble reclamo.

## Dónde se ve

- [`IntentoEjecucion.java`](../../src/main/java/com/workflow/financiero/dominio/modelo/IntentoEjecucion.java)
- [`ServicioIntencionEjecucion.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioIntencionEjecucion.java)
- [`TrabajadorEjecucion.java`](../../src/main/java/com/workflow/financiero/infraestructura/ejecucion/TrabajadorEjecucion.java)
- [`IntentoEjecucionRepositorio.java`](../../src/main/java/com/workflow/financiero/infraestructura/persistencia/IntentoEjecucionRepositorio.java)
- [`V7__intento_ejecucion_durable.sql`](../../src/main/resources/db/migration/V7__intento_ejecucion_durable.sql)
- [`PruebaEjecucionIdempotenteTest.java`](../../src/test/java/com/workflow/financiero/PruebaEjecucionIdempotenteTest.java)
- [`PruebaRecuperacionEjecucionTest.java`](../../src/test/java/com/workflow/financiero/PruebaRecuperacionEjecucionTest.java)
