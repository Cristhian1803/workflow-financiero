# workflow-financiero

Backend de operaciones financieras con BPMN. El código Java vive en el paquete raíz `com.workflow.financiero`.

## Requisitos

- JDK 21 con `JAVA_HOME` configurado.
- Docker Desktop con WSL2 en Windows, o Docker Engine + Docker Compose en Linux/macOS.
- Git.
- **No se necesita Maven global**: el proyecto incluye Maven Wrapper.

## Base de datos

> Antes de ejecutar `docker compose` o las pruebas, verificar que Docker Desktop
> este iniciado. Si se reinicia Windows, abrir Docker Desktop y esperar el motor.

Crear el archivo de variables locales:

```powershell
Copy-Item .env.example .env
```

Levantar PostgreSQL 16:

```powershell
docker compose up -d postgres
docker compose ps
```

## Compilar y probar

Windows:

```powershell
.\mvnw.cmd clean verify
```

Linux/macOS:

```bash
chmod +x mvnw   # solo si el archivo no tiene permiso de ejecucion
./mvnw clean verify
```

## Ejecutar la aplicacion

```powershell
.\mvnw.cmd spring-boot:run
```

Punto de salud:

```text
GET http://localhost:8080/actuator/health
```

## Tecnologias

- Java 21
- Spring Boot 3.5.16
- Flowable 7.2.0
- PostgreSQL 16
- Maven Wrapper
- JUnit 5, Testcontainers, WireMock y ArchUnit

> Este README se completara con la documentacion de API, escenarios,
> arquitectura y decisiones a medida que avance el proyecto.
