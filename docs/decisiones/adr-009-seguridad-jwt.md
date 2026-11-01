# ADR-009 — Seguridad con JWT emitido por la aplicación

**Estado:** aceptada · **Fecha:** 2026-11-01

## Contexto

La API es consumida por clientes HTTP sin sesión de servidor. Hay roles
funcionales (`SOLICITANTE`, `SUPERVISOR`, `GERENTE`, `RIESGOS`,
`ANALISTA_RIESGOS`, `JEFE_RIESGOS`, `OPERACIONES`, `ADMINISTRADOR`, `AUDITOR`) y
reglas de segregación que dependen de la identidad. No hay un proveedor de
identidad externo dentro del alcance.

## Decisión

Se usa Spring Security con `oauth2-resource-server` y JWT firmado con un secreto
simétrico por ambiente:

- `POST /api/v1/autenticacion/iniciar-sesion` valida usuario y clave BCrypt y
  emite un JWT con usuario, roles y expiración configurable.
- `ConfiguracionJwt` arma el codificador y el decodificador con `Nimbus`.
- `ConfiguracionSeguridad` protege las rutas por rol y método.
- Los usuarios demo se siembran solo en perfiles no productivos.

El token no incluye datos sensibles y el secreto se exige por variable de
entorno; la aplicación no arranca sin él.

## Alternativas descartadas

- **Sesión con cookie y estado en servidor.** Se descartó porque obliga a
  manejar sesiones, CSRF y almacenamiento compartido entre nodos.
- **Keycloak u otro proveedor OAuth2 externo.** Se descartó porque agrega un
  servicio de infraestructura que no aporta al objetivo de la prueba.
- **API keys por cliente.** Se descartaron porque no representan al usuario ni
  permiten aplicar segregación de funciones.

## Consecuencias

- La API es stateless y escala sin sesiones.
- No hay refresh token ni revocación inmediata: un token sigue siendo válido
  hasta su expiración.
- La rotación del secreto invalida todos los tokens emitidos.
- La firma del proveedor de riesgo es un mecanismo aparte (HMAC), no JWT.

## Dónde se ve

- [`ConfiguracionSeguridad.java`](../../src/main/java/com/workflow/financiero/infraestructura/seguridad/ConfiguracionSeguridad.java)
- [`ConfiguracionJwt.java`](../../src/main/java/com/workflow/financiero/infraestructura/seguridad/ConfiguracionJwt.java)
- [`ServicioAutenticacion.java`](../../src/main/java/com/workflow/financiero/aplicacion/servicio/ServicioAutenticacion.java)
- [`SembradorUsuariosDemo.java`](../../src/main/java/com/workflow/financiero/infraestructura/seguridad/SembradorUsuariosDemo.java)
- [`PruebaSeguridadTest.java`](../../src/test/java/com/workflow/financiero/PruebaSeguridadTest.java)
