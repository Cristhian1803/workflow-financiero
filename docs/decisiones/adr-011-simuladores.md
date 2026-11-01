# ADR-011 — Simuladores dentro del mismo despliegue

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

La prueba exige demostrar ejecución financiera, idempotencia, cancelación con
reserva, riesgo externo y recuperación, sin mover dinero real ni depender de un
tercero. Los adaptadores deben ser los mismos que usaría un proveedor real.

## Decisión

Los dos proveedores son simuladores que viven en la misma aplicación y solo se
activan en los perfiles `desarrollo` y `pruebas`:

- **Proveedor financiero:** expone las rutas
  `/api/v1/integraciones/proveedor-financiero/*`, persiste el estado en
  `operacion_simulada` y exige la cabecera `X-Clave-Integracion`. La aplicación lo
  consume por HTTP con `AdaptadorProveedorFinancieroHttp`.
- **Proveedor de riesgo:** implementa `PuertoProveedorRiesgo` como
  `ProveedorRiesgoSimulado`; el evento de respuesta se envía al endpoint firmado.

El control de fallos (`ConfiguracionFallosSimulador`) existe solo en el perfil
`pruebas`. En producción los simuladores no se exponen.

## Alternativas descartadas

- **WireMock o MockServer como proveedor externo.** Se descartó para el flujo
  principal porque no persiste estado ni demuestra el contrato completo. WireMock
  sí se usa para probar el contrato HTTP del adaptador.
- **Un proveedor real o un sandbox de terceros.** Se descartó porque la prueba no
  debe mover dinero ni depender de credenciales externas.

## Consecuencias

- El flujo completo se puede repetir sin dinero real.
- El estado del simulador es consultable por SQL, lo que facilita la evidencia.
- Hay que cuidar que las rutas y los usuarios demo no se activen en producción.
- La idempotencia del proveedor es parte del contrato y está probada.

## Dónde se ve

- [`ControladorProveedorFinancieroSimulado.java`](../../src/main/java/com/workflow/financiero/api/controlador/ControladorProveedorFinancieroSimulado.java)
- [`ServicioSimuladorProveedorFinanciero.java`](../../src/main/java/com/workflow/financiero/aplicacion/simulador/ServicioSimuladorProveedorFinanciero.java)
- [`ProveedorRiesgoSimulado.java`](../../src/main/java/com/workflow/financiero/infraestructura/externo/ProveedorRiesgoSimulado.java)
- [`ConfiguracionFallosSimulador.java`](../../src/main/java/com/workflow/financiero/aplicacion/simulador/ConfiguracionFallosSimulador.java)
- [`docs/integraciones/proveedor-financiero-simulado.md`](../integraciones/proveedor-financiero-simulado.md)
- [`docs/integraciones/riesgo-externo-simulado.md`](../integraciones/riesgo-externo-simulado.md)
