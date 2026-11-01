# ADR-002 — Monolito modular en un solo despliegue

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

Una solicitud atraviesa registro, clasificación de riesgo, aprobaciones, ejecución
financiera y, si hace falta, cancelación y compensación. Esas etapas comparten el
mismo estado y necesitan consistencia fuerte: una aprobación y el avance del
proceso deben confirmarse juntas, y una cancelación no puede declararse antes de
liberar una reserva.

## Decisión

Se despliega una sola aplicación Spring Boot, organizada por paquetes y con
puertos internos entre capas. Los módulos no se comunican por red: comparten
procesos, transacciones y base de datos. Flowable corre embebido en el mismo
proceso y con el mismo `DataSource`.

## Alternativas descartadas

- **Microservicios por dominio** (solicitudes, aprobaciones, ejecución, riesgo)
  con un bus como Kafka o RabbitMQ. Se descartó porque introduce consistencia
  eventual, operación de varios servicios y escenarios de compensación
  distribuida que no aportan nada al objetivo de la prueba.
- **Dos o tres servicios con transacciones distribuidas (2PC).** Se descartó
  porque el proveedor financiero es HTTP y no participa de una transacción XA;
  además, 2PC no resuelve la idempotencia del efecto externo.

## Consecuencias

- Un solo artefacto y un solo `docker compose` para levantar el sistema.
- La transacción compartida entre JPA y Flowable es posible y está probada.
- El escalado es vertical. Si se necesitaran varias instancias, los trabajadores
  ya usan `FOR UPDATE SKIP LOCKED`, pero no se probó con más de un nodo.
- Los límites entre módulos son de paquete, no de red. Un cambio de límites es
  un refactor interno, no un cambio de contrato.

## Dónde se ve

- [`AplicacionWorkflowFinanciero.java`](../../src/main/java/com/workflow/financiero/AplicacionWorkflowFinanciero.java)
- [`pom.xml`](../../pom.xml)
- [`docker-compose.yml`](../../docker-compose.yml)
- [`ConfiguracionMotorFlowable.java`](../../src/main/java/com/workflow/financiero/infraestructura/bpmn/ConfiguracionMotorFlowable.java)
- La justificación ampliada está en [`docs/arquitectura.md`](../arquitectura.md).
