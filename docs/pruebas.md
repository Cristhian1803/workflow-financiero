# Pruebas automatizadas y evidencia

Este documento resume la suite automatizada del sistema, su relación con los
escenarios obligatorios y la evidencia observable de cada comportamiento. No
usa porcentajes de cobertura como sustituto de escenarios: cada fila se apoya en
una prueba concreta y en un efecto verificable.

## Entorno de la ejecución

| Dato | Valor |
|---|---|
| Fecha | 2026-11-01 |
| Rama | `mejora-documentacion` |
| Commit base (HEAD al iniciar) | `92ccd55` |
| Sistema operativo | Windows |
| Java | 21.0.12.1 |
| Spring Boot | 3.5.16 |
| Flowable | 7.2.0 |
| PostgreSQL | 16.15 con Testcontainers |
| Docker | 29.9.2 |
| Comando | `.\mvnw.cmd clean verify` |

La suite usa PostgreSQL real con Testcontainers y Flowable real. No usa H2. Los
secretos de prueba se generan en cada ejecución y no se versionan.

## Resultado global

```text
Tests run: 467, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 03:50 min
```

La corrida completa se ejecutó después de estabilizar la prueba de recuperación y
de agregar los tres bordes inferiores a nivel de proceso. No se configuraron
reintentos de Surefire. Los cambios de esta iteración fueron de pruebas y
documentación; no se modificó lógica de producción.

### Corridas dirigidas

| Comando | Resultado |
|---|---|
| `.\mvnw.cmd "-Dtest=PruebaRutasProcesoTest" test` | 17 pruebas, 0 fallos |
| `.\mvnw.cmd "-Dtest=PruebaRechazoReenvioTest" test` | 20 pruebas, 0 fallos |
| `.\mvnw.cmd "-Dtest=PruebaRecuperacionEjecucionTest" test` | 16 pruebas, 0 fallos, repetida 3 veces |
| `.\mvnw.cmd "-Dtest=PruebaRutasProcesoTest,PruebaRechazoReenvioTest,PruebaRecuperacionEjecucionTest" test` | 53 pruebas, 0 fallos |
| `.\mvnw.cmd clean verify` | 467 pruebas, 0 fallos |

La clase `PruebaRecuperacionEjecucionTest` se ejecutó tres veces adicionales para
comprobar que la carrera de reclamación quedó cerrada. Las tres corridas dieron
16/16.

## Escenarios P-01 a P-10

| ID | Escenario | Pruebas principales | Efecto o persistencia comprobada | Estado |
|---|---|---|---|---|
| P-01 | Registro correcto | `PruebaApiSolicitudTest.creaCincoMillonesSinAprobaciones`; `PruebaValidacionesTest.aceptaUnaSolicitudValidaYNormalizaLosCodigos`; `PruebaPersistenciaTest.guardaYRecuperaSolicitudRevisionYEvento`; `PruebaHistorialTest.elEventoGuardaActorRolEstadosYCorrelacion` | Solicitud, revisión, evento y proceso persistidos; respuesta 201 y `Location` | Cubierto |
| P-02 | Selección de rutas | `PruebaPoliticasAprobacionTest.calculaElPlanEnLosLimitesDeMonto`; `PruebaPoliticasAprobacionTest.agregaRiesgosCuandoElRiesgoEsAlto`; `PruebaRutasProcesoTest`; `PruebaValidacionesTest`; `PruebaRiesgoTest.clasificaLosBordesDeMonto` | Plan de aprobación, tareas de negocio y del motor, rol de Riesgos y evaluación externa | Cubierto |
| P-03 | Aprobaciones paralelas | `PruebaAprobacionesParalelasTest.gerenteLuegoRiesgosEsperaRiesgoExterno`; `riesgosLuegoGerenteEsperaRiesgoExterno`; `supervisorPrimeroEsperaRiesgoExterno`; `unaSolaAprobacionNoPreparaEjecucion`; `PruebaTareasAprobacionTest.doscientosCincuentaMillonesSincronizaTresTareas` | Tres tareas para más de 200M; dos actores distintos; espera de todas las aprobaciones y del riesgo externo | Cubierto |
| P-04 | Rechazo obligatorio | `PruebaRechazoReenvioTest.rechazoSupervisorCancelaGerente`; `rechazoGerenteCancelaSupervisor`; `rechazoRiesgosCancelaLasOtrasRamas`; `rechazoSupervisorCancelaGerenteYRiesgos`; `rechazoGerenteCancelaSupervisorYRiesgos`; `PruebaTareasAprobacionTest.rechazarCancelaLasDemasTareas` | Revisión `RECHAZADA`, tareas pendientes `CANCELADA`, sin intención y con cero reservas/confirmaciones en `operacion_simulada` | Cubierto |
| P-05 | Prohibición de autoaprobación | `PruebaTareasAprobacionTest.creadorConRolNoPuedeAprobar`; `creadorConRolAprobadorNoVeSuTarea`; `PruebaSegregacionTest.delegadoCreadorNoPuedeAprobar`; `PruebaEscalamientoTest.creadorComoSuperiorAsignadoNoPuedeAprobar`; `PruebaPoliticasAprobacionTest.verificadorRechazaDecisionDelCreador` | Petición directa denegada aunque el creador tenga el rol; no se persiste decisión | Cubierto |
| P-06 | Escalamiento | `PruebaEscalamientoTest.timerDeSupervisorGeneraNotificacion`; `losTresRolesRecibenSuNotificacion`; `timerDeReasignacionAsignaAlSuperior`; `losTresRolesSeReasignanASusSuperiores`; `duracionConfiguradaSeReflejaEnElProceso`; `reasignacionEsIdempotente` | Notificación visible, reasignación al superior, duraciones configurables y reintentos sin duplicados | Cubierto |
| P-07 | Ejecución concurrente | `PruebaEjecucionIdempotenteTest.dosTrabajadoresConcurrentesProducenUnSoloEfecto`; `proveedorSeInvocaSinTransaccion`; `confirmacionExitosaTerminaEjecucion`; `PruebaContratoProveedorTest.repeticionConcurrenteDeConfirmacionProduceUnSoloEfecto` | Dos hilos con transacciones independientes; una intención, una reserva y una confirmación; revisión `EJECUTADA` | Cubierto |
| P-08 | Prevención de duplicados | `PruebaContratoProveedorTest.mismaClaveConHuellaDistintaGeneraConflicto`; `confirmacionDuplicadaDevuelveMismoResultado`; `dosReservasIdenticasProducenUnSoloEfecto`; `PruebaEjecucionIdempotenteTest.trabajadorRepetidoNoDuplicaEfecto`; `unicidadPorRevisionImpideDuplicados`; `PruebaRecuperacionEjecucionTest.conflictoDeIdempotenciaNoSeReintenta` | Una sola operación por clave y huella; conflicto `409` detectado; reintento bloqueado | Cubierto |
| P-09 | Cancelación y compensación | `PruebaCancelacionCompensacionTest.cancelarAntesDeReservar`; `cancelarConReservaYCompensacion`; `cancelarDespuesDeConfirmacion`; `confirmacionEnTransitoQuedaPendiente`; `liberacionFallidaQuedaPendiente`; `liberacionFallidaReintentaHastaManualYSeResuelve`; `cancelacionDuplicadaConCompensacion`; `subprocesoDeCancelacionInterrumpeYTermina` | Una sola liberación; resultado incierto sin declarar `CANCELADA`; compensación fallida tratada; proceso BPMN real interrumpido | Cubierto |
| P-10 | Error y recuperación externa | `PruebaRecuperacionEjecucionTest.respuestaPerdidaEnConfirmacionSeReconcilia`; `respuestaPerdidaEnReservaSeReconcilia`; `consultaNoDisponibleDejaIncidente`; `intentosAgotadosCierranResultadoDesconocido`; `errorDefinitivoAntesDelEfecto`; `errorDefinitivoNoSeReintentaAutomaticamente`; `conflictoDeIdempotenciaNoSeReintenta`; `dosReintentosManualesSimultaneos`; `PruebaRecuperacionRiesgoTest.reintentosAgotadosCreanIncidente` | Respuesta perdida reconciliada; incidente con actor, motivo, reintentos y eventos; una sola confirmación; reintento manual auditado | Cubierto |

## Escenarios de concurrencia C-01 a C-05

| ID | Escenario | Pruebas principales | Efecto o persistencia comprobada | Estado |
|---|---|---|---|---|
| C-01 | Dos aprobadores sobre la misma tarea | `PruebaConcurrenciaAprobacionTest.dosAprobadoresProducenUnaSolaDecision`; `mismoActorNoApruebaDosRolesEnParalelo`; `timerYAprobacionConcurrentes` | Una sola decisión persistida; una tarea resuelta; carreras con timers consistentes | Cubierto |
| C-02 | Dos ejecuciones idénticas | `PruebaEjecucionIdempotenteTest.dosTrabajadoresConcurrentesProducenUnSoloEfecto`; `dosPreparacionesConcurrentesNoDuplicanIntencion`; `PruebaContratoProveedorTest.repeticionConcurrenteDeReservaProduceUnSoloEfecto`; `repeticionConcurrenteDeConfirmacionProduceUnSoloEfecto` | Una intención y un efecto observable aunque dos hilos ejecuten a la vez | Cubierto |
| C-03 | Cancelación durante la aprobación | `PruebaConcurrenciaCancelacionTest.cancelarAntesDeAprobarImpideAprobacion`; `aprobacionParcialLuegoCancelacion`; `aprobacionYCancelacionConcurrentes`; `cancelarRevisionEnEjecucionSinReservaQuedaCancelada`; `PruebaCancelacionCompensacionTest.cancelarDuranteAprobacion`; `cancelarConReservaYCompensacion`; `subprocesoDeCancelacionInterrumpeYTermina` | Ambos órdenes posibles; no quedan tareas pendientes; sin ejecución posterior; BPMN real interrumpido | Cubierto |
| C-04 | Confirmación externa con respuesta perdida | `PruebaRecuperacionEjecucionTest.respuestaPerdidaEnConfirmacionSeReconcilia`; `respuestaPerdidaEnReservaSeReconcilia`; `PruebaContratoProveedorTest.confirmacionConRespuestaPerdidaConservaEfecto` | Se consulta la misma clave; se registra una sola confirmación; no se repite el efecto | Cubierto |
| C-05 | Notificación externa duplicada | `PruebaEventosRiesgoTest.duplicadoIdenticoNoSeAplicaDosVeces`; `mismoIdentificadorConOtraHuellaGeneraConflicto`; `eventoTempranoQuedaPendiente`; `eventoDeRevisionAnteriorQuedaObsoleto`; `eventoPosteriorALaEjecucionQuedaObsoleto`; `eventoPosteriorALaCancelacionQuedaObsoleto`; `PruebaRecuperacionRiesgoTest.eventoDuranteRevisionManualEsObsoleto` | Un evento se aplica una sola vez; duplicado idéntico no cambia el proceso; conflicto detectado; temprano/tardío tratado | Cubierto |

## Bordes de monto

Los nueve valores adyacentes se comprueban en la política y en el proceso real:

| Valor | Política | Proceso real | Roles esperados |
|---|---|---|---|
| 9.999.999 | `calculaElPlanEnLosLimitesDeMonto` | `bordesInferioresMantienenLaRuta` | Ninguno |
| 10.000.000 | `calculaElPlanEnLosLimitesDeMonto` | `diezMillonesSinTareas` | Ninguno |
| 10.000.001 | `calculaElPlanEnLosLimitesDeMonto` | `diezMillonesUnGuaraniExigeSupervisor` | Supervisor |
| 49.999.999 | `calculaElPlanEnLosLimitesDeMonto` | `bordesInferioresMantienenLaRuta` | Supervisor |
| 50.000.000 | `calculaElPlanEnLosLimitesDeMonto` | `cincuentaMillonesExigeSupervisor` | Supervisor |
| 50.000.001 | `calculaElPlanEnLosLimitesDeMonto` | `cincuentaMillonesUnGuaraniExigeSupervisorYGerente` | Supervisor y Gerente |
| 199.999.999 | `calculaElPlanEnLosLimitesDeMonto` | `bordesInferioresMantienenLaRuta` | Supervisor y Gerente |
| 200.000.000 | `calculaElPlanEnLosLimitesDeMonto` | `doscientosMillonesExigeSupervisorYGerente` | Supervisor y Gerente |
| 200.000.001 | `calculaElPlanEnLosLimitesDeMonto` | `doscientosMillonesUnGuaraniEsperaRiesgoExterno` | Supervisor, Gerente y Riesgos; evaluación externa |

La prueba parametrizada `bordesInferioresMantienenLaRuta` agregó los tres valores
inferiores que no estaban cubiertos a nivel de proceso. No se duplicaron nueve
procesos completos porque la política ya demuestra los nueve valores y el proceso
cubre cada transición relevante.

### Combinaciones de riesgo y tipo de operación

| Caso | Evidencia | Resultado |
|---|---|---|
| Riesgo alto de 5M y 10M | `PruebaPoliticasAprobacionTest.agregaRiesgosCuandoElRiesgoEsAlto` | Riesgos |
| Riesgo alto de 25M | `PruebaPoliticasAprobacionTest.agregaRiesgosCuandoElRiesgoEsAlto` | Supervisor y Riesgos |
| Riesgo alto de 100M y 250M | `PruebaPoliticasAprobacionTest.agregaRiesgosCuandoElRiesgoEsAlto` | Supervisor, Gerente y Riesgos, sin duplicar Riesgos |
| Riesgo medio de 5M y 25M | `PruebaPoliticasAprobacionTest.agregaRiesgosCuandoElRiesgoEsAlto` | Sin tareas y Supervisor, respectivamente |
| Tipo sensible de 10M | `PruebaRutasProcesoTest.diezMillonesAltoExigeRiesgos` | Riesgos |
| Tipo sensible de 50M | `PruebaRutasProcesoTest.cincuentaMillonesAltoExigeSupervisorYRiesgos` | Supervisor y Riesgos |
| Tipo sensible de 100M | `PruebaRutasProcesoTest.cienMillonesAltoExigeTresRamas` | Supervisor, Gerente y Riesgos |
| Perfil de riesgo alto | `PruebaRiesgoTest.elTipoSensibleYElPerfilAltoElevanElRiesgo` | Riesgo elevado a alto |

## Negativos y seguridad

| Control | Pruebas | Evidencia |
|---|---|---|
| `401` sin token o token inválido | `PruebaSeguridadTest.rechazaTokenAusente`; `rechazaTokenAlterado`; `rechazaTokenVencido`; `rechazaTokenConEmisorIncorrecto`; `aprobarExigeAutenticacion`; `rechazarExigeAutenticacion`; `cancelarExigeAutenticacion` | Respuesta uniforme y correlación |
| `403` por rol insuficiente | `PruebaSeguridadTest.rechazaCreacionSinRolSolicitante`; `bandejaRechazaSolicitante`; `bandejaRechazaAdministrador`; `aprobarRechazaSolicitante`; `rechazarRechazaSolicitante`; `revisionRiesgoRechazaSolicitante`; `revisionRiesgoRechazaRolAprobacion` | Acceso denegado sin cambiar estado |
| `404` recurso ajeno o inexistente | `PruebaTareasAprobacionTest.tareaInexistenteResponde404`; `tareaNoVisibleResponde404`; `PruebaSeguridadTest.resolverRevisionInexistente`; `PruebaRecuperacionEjecucionTest.apiIncidentesExigeRolOperativo` | No se revela ni se modifica el recurso |
| `409` conflicto de estado o idempotencia | `PruebaTareasAprobacionTest.tareaYaResueltaResponde409`; `PruebaRechazoReenvioTest.reenvioNoRechazadoDevuelve409`; `PruebaRecuperacionEjecucionTest.conflictoDeIdempotenciaNoSeReintenta`; `PruebaRecuperacionRiesgoTest.resolucionDuplicadaNoAplicaDosVeces` | Sin segundo efecto |
| `422` reenvío fuera de plazo | `PruebaRechazoReenvioTest.reenvioFueraDeLos30Dias` | Se rechaza sin crear revisión |
| Autoaprobación | Pruebas P-05 | El creador no aprueba ni ve su tarea |
| Doble rol funcional | `PruebaSegregacionTest.mismoActorNoApruebaDosRoles`; `PruebaConcurrenciaAprobacionTest.mismoActorNoApruebaDosRolesEnParalelo` | Un actor no ocupa dos roles en la misma solicitud |
| Timers contra resolución | `PruebaEscalamientoTest`; `PruebaConcurrenciaAprobacionTest.timerYAprobacionConcurrentes` | No se notifica ni reasigna una tarea ya resuelta |
| Evento temprano y tardío | `PruebaEventosRiesgoTest.eventoTempranoQuedaPendiente`; `eventoDeRevisionAnteriorQuedaObsoleto`; `eventoPosteriorALaEjecucionQuedaObsoleto`; `eventoPosteriorALaCancelacionQuedaObsoleto` | Inbox durable y aplicación única |
| Compensación fallida | `PruebaCancelacionCompensacionTest.liberacionFallidaQuedaPendiente`; `liberacionFallidaReintentaHastaManualYSeResuelve` | No se declara `CANCELADA` sin liberar |
| Revisión manual autorizada | `PruebaRecuperacionRiesgoTest.analistaResuelveYRecalculaPlan`; `jefeRiesgosResuelve`; `rolSinPermisoNoResuelve`; `creadorConRolRevisorNoResuelve` | Solo los roles autorizados resuelven; el creador no |

## Evidencia de la corrección de estabilidad

La prueba `PruebaRecuperacionEjecucionTest.dosTrabajadoresDeRecuperacionNoDuplican`
presentaba una carrera: con `ejecucion.trabajador.reclamacion-vencida=PT0S`, un
segundo trabajador podía reclamar una intención que el primero acababa de dejar
en `ENVIANDO`. La confirmación seguía siendo única por idempotencia, pero el
contador de operaciones procesadas podía ser 2.

La configuración de pruebas se alineó con producción:

```properties
ejecucion.trabajador.reclamacion-vencida=PT30S
ejecucion.trabajador.reintento-espera=PT0S
```

`reintento-espera=PT0S` se mantiene para que una intención con resultado
desconocido sea elegible de inmediato. `envioAbandonadoSeRecupera` sigue
funcionando porque fija explícitamente una reclamación de hace un minuto. La
prueba se ejecutó tres veces adicionales: 16/16 en cada una.

## Prueba manual complementaria

Además de la suite, se levantó la aplicación con el perfil `pruebas` y PostgreSQL
real para comprobar el comportamiento por HTTP y en la base:

1. Se creó una solicitud de 5M y se confirmó que el trabajador la dejó en
   `EJECUTADA`, con una reserva y una confirmación.
2. Se crearon solicitudes de 9.999.999, 49.999.999 y 199.999.999. La primera no
   abrió tareas; la segunda abrió Supervisor; la tercera abrió Supervisor y
   Gerente.
3. Se rechazó una solicitud de 25M como Supervisor. Quedó en `RECHAZADA`, sin
   intención de ejecución y con cero operaciones en `operacion_simulada`.
4. Se forzó `FALLO_ANTES_DEL_EFECTO` en la reserva de una solicitud de 5M. La
   revisión quedó en `RESULTADO_DESCONOCIDO` y se abrió un incidente. Después de
   limpiar el fallo, se reintentó como `operaciones`: la revisión quedó
   `EJECUTADA`, el incidente `RESUELTO`, con actor `operaciones`, motivo del
   reintento, un reintento registrado y una sola reserva/confirmación.
5. Se verificaron los eventos `INCIDENTE_CREADO`, `INCIDENTE_REINTENTANDO` e
   `INCIDENTE_RESUELTO`, además de `EJECUCION_CONFIRMADA`.

La base local se reinició al terminar para dejar el entorno limpio. Esta prueba
manual complementa la suite; no sustituye las aserciones automáticas.

## Limitaciones conocidas

- La suite de integración requiere Docker, Testcontainers y PostgreSQL 16. No se
  ejecuta con H2.
- `PruebaConcurrenciaCancelacionTest` usa un adaptador de prueba para observar las
  solicitudes de cancelación. La cancelación BPMN real se evidencia en
  `PruebaCancelacionCompensacionTest.subprocesoDeCancelacionInterrumpeYTermina`.
- Las pruebas manuales se ejecutan en una base local y no forman parte de la
  suite automatizada.
- No se usan porcentajes de cobertura como evidencia de comportamiento.
