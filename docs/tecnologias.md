# Tecnologías

Este documento explica qué se usa, para qué y qué se descartó. Las versiones
salen de [`pom.xml`](../pom.xml), de `docker-compose.yml` y de las propiedades del
proyecto. Cuando una versión la administra Spring Boot, se indica así y no se fija
a mano.

## Obligatorias del enunciado

| Tecnología | Versión | Qué es | Para qué se usa aquí | Alternativa descartada |
|---|---|---|---|---|
| Java | 21 (LTS) | Lenguaje y plataforma de ejecución. | Base de toda la aplicación; `BigDecimal`, `Instant` y `record` en los contratos. | Java 17: no aporta nada frente a la LTS elegida. |
| Spring Boot | 3.5.16 | Marco que integra web, datos, seguridad y configuración. | Estructura de la aplicación, inyección de dependencias y perfiles. | Quarkus o Micronaut: otra curva de aprendizaje y ecosistema. |
| Maven Wrapper | Incluido en el repositorio | Construcción reproducible sin Maven global. | `.\mvnw.cmd clean verify` y `spring-boot:run`. | Gradle: no aporta ventaja para este proyecto. |
| Flowable | 7.2.0 | Motor BPMN embebido. | Proceso `aprobacionFinanciera`, tareas humanas, compuertas, timers, mensajes y subproceso de cancelación. | Camunda 7, jBPM o un motor externo por REST. |
| PostgreSQL | 16 (`postgres:16-alpine`) | Base de datos relacional. | Estado de negocio, bloqueos, restricciones, inbox y tablas de Flowable. | H2: no reproduce bloqueos ni SQL real. |
| Spring Data JPA | Gestionada por Spring Boot | Persistencia con JPA/Hibernate. | Entidades, repositorios y bloqueo pesimista. | JDBC puro: más código y mapeo manual. |
| Spring Security | Gestionada por Spring Boot | Autenticación y autorización. | Protección de rutas, roles y segregación de funciones. | Apache Shiro: menos integración con el ecosistema. |
| OAuth2 Resource Server | Gestionada por Spring Boot | Validación de tokens JWT. | Decodificación y validación del token emitido por la aplicación. | Filtro propio de JWT: reinventa la validación. |
| JWT (Nimbus JOSE) | Gestionada por Spring Boot | Tokens firmados. | Sesión stateless de la API. | Sesión con cookie y estado en servidor. |
| springdoc-openapi | 2.10.0 | Generación de OpenAPI y Swagger UI. | Documentación interactiva de la API en `/swagger-ui.html`. | Springfox: sin soporte actual para Spring Boot 3. |
| JUnit 5 | Gestionada por Spring Boot | Framework de pruebas. | Pruebas unitarias y de integración. | TestNG: sin ventaja aquí. |
| Mockito y AssertJ | Gestionadas por Spring Boot | Dobles de prueba y aserciones fluidas. | Pruebas de servicios y verificación de efectos. | Aserciones manuales con `assertEquals`. |
| Testcontainers | Gestionada por Spring Boot | Contenedores efímeros para pruebas. | PostgreSQL 16 real en la suite. | Base local compartida: no es reproducible. |
| WireMock | 3.13.2 | Servidor HTTP simulado. | Prueba del contrato HTTP del proveedor financiero. | Un servidor real: no es determinista. |
| Docker Compose | Docker Desktop | Orquestación local de contenedores. | Levantar PostgreSQL para desarrollo y demostración. | Instalación manual de PostgreSQL. |
| Actuator | Gestionada por Spring Boot | Endpoints de salud e información. | `GET /actuator/health` para saber si la aplicación está arriba. | Un endpoint propio de salud. |

## Opcionales usadas

| Tecnología | Versión | Qué es | Para qué se usa aquí | Alternativa descartada |
|---|---|---|---|---|
| Lombok | Gestionada por Spring Boot | Genera código repetitivo en tiempo de compilación. | Constructores, getters y logs en servicios y entidades. | Escribir el código a mano: más ruido, sin beneficio didáctico. |

## Añadidas al stack mínimo

| Tecnología | Versión | Qué es | Para qué se usa aquí | Alternativa descartada |
|---|---|---|---|---|
| Flyway | Gestionada por Spring Boot | Versionado de esquema con SQL. | Migraciones V1–V12 y arranque reproducible desde una base vacía. | `ddl-auto: update` de Hibernate. |
| ArchUnit | 1.5.1 | Reglas de arquitectura como pruebas. | Está declarado, pero **todavía no tiene reglas activas**. | Revisión manual: es lo que se usa hoy. |

## No usadas a propósito

| Tecnología | Qué es | Por qué no se usa | Qué se usaría en su lugar |
|---|---|---|---|
| Kafka | Bus de eventos distribuido. | El despliegue es un monolito y la consistencia es transaccional. | Intención durable, inbox y eventos persistidos. |
| RabbitMQ | Cola de mensajes. | No hay consumidores externos ni necesidad de desacoplar por red. | Trabajadores con polling y `SKIP LOCKED`. |
| Redis | Caché y estructuras en memoria. | No hay cuello de botella de lectura ni sesiones distribuidas. | PostgreSQL e índices. |
| MapStruct | Generación de mapeadores. | Los contratos se construyen con métodos explícitos y son pocos. | Mapeo manual en los servicios. |
| Micrometer / Prometheus | Métricas y monitoreo. | El alcance pide salud básica, no un tablero operativo. | Actuator `health` e `info`. |
| Keycloak | Proveedor de identidad OAuth2. | Agrega un servicio de infraestructura que la prueba no requiere. | JWT emitido por la aplicación. |
| H2 | Base de datos en memoria. | No reproduce bloqueos, `SKIP LOCKED` ni los tipos de PostgreSQL. | PostgreSQL 16 con Testcontainers. |

## Criterio de versiones

- Las versiones de Spring Boot, Flowable, springdoc, WireMock y ArchUnit están
  fijadas en `pom.xml`.
- El resto de las dependencias la administra Spring Boot 3.5.16.
- La imagen de PostgreSQL está fijada en `docker-compose.yml`.
- No se agregan dependencias sin justificar su compatibilidad con Spring Boot
  3.5.16 y Flowable 7.2.0.
