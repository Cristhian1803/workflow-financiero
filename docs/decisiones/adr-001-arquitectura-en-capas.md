# ADR-001 — Arquitectura en capas con entidades JPA en el dominio

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

El backend es una aplicación Spring Boot con cuatro responsabilidades claras:
exponer HTTP, orquestar casos de uso, modelar las reglas del negocio y hablar con
PostgreSQL, Flowable y los proveedores externos. El plazo de la prueba es corto y
el dominio es pequeño, pero las transiciones de estado y las reglas de aprobación
necesitan pruebas rápidas y aisladas.

## Decisión

Se usa una arquitectura en capas:

- `api`: controladores, contratos de entrada y salida, manejo de errores.
- `aplicacion`: casos de uso, transacciones y orquestación.
- `dominio`: modelo, estados, reglas y validaciones.
- `infraestructura`: persistencia, BPMN, integraciones, seguridad y simuladores.

Las entidades JPA viven en `dominio.modelo` con sus anotaciones, y los
repositorios Spring Data viven en `infraestructura.persistencia`. Los servicios de
aplicación los consumen directamente. Es una **concesión deliberada**: no se
implementa arquitectura hexagonal pura.

La separación se sostiene por convención. `dominio` no importa ninguna otra capa;
`api` y `aplicacion` dependen de `dominio`; `infraestructura` implementa los
puertos de `aplicacion`.

## Alternativas descartadas

- **Hexagonal pura con modelo de dominio sin anotaciones y mapeadores
  separados.** Se descartó porque obliga a mantener dos modelos y a escribir
  mapeadores para un dominio que todavía está cambiando, sin un beneficio
  concreto para esta entrega.
- **Separar las entidades JPA en `infraestructura` y usar modelos de dominio
  inmutables.** Se descartó por el mismo motivo: duplica el modelo y agrega
  conversiones en cada transacción.

## Consecuencias

- Menos código y menos conversiones.
- `aplicacion` queda acoplada a JPA y a algunos adaptadores concretos. Hoy hay 33
  clases de `aplicacion` que importan `infraestructura.persistencia`, y
  `ServicioReintentoIncidente` usa `TrabajadorRecuperacionEjecucion`.
- La separación de capas no está verificada por una regla automática: ArchUnit
  está declarado en `pom.xml`, pero no tiene reglas activas.
- Si el dominio crece, el primer paso sería extraer las entidades a un modelo
  propio y agregar reglas de dependencia.

## Dónde se ve

- [`api/package-info.java`](../../src/main/java/com/workflow/financiero/api/package-info.java)
- [`aplicacion/package-info.java`](../../src/main/java/com/workflow/financiero/aplicacion/package-info.java)
- [`dominio/package-info.java`](../../src/main/java/com/workflow/financiero/dominio/package-info.java)
- [`infraestructura/package-info.java`](../../src/main/java/com/workflow/financiero/infraestructura/package-info.java)
- [`dominio/modelo`](../../src/main/java/com/workflow/financiero/dominio/modelo)
- [`infraestructura/persistencia`](../../src/main/java/com/workflow/financiero/infraestructura/persistencia)
- [`ServicioReintentoIncidente.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioReintentoIncidente.java)
- La vista completa está en [`docs/arquitectura.md`](../arquitectura.md).
