# Proceso BPMN de aprobación financiera

## Identificación

| Dato | Valor |
|---|---|
| Archivo fuente | `src/main/resources/processes/aprobacion-financiera.bpmn20.xml` |
| Copia de documentación | `docs/bpmn/aprobacion-financiera.bpmn20.xml` |
| Clave del proceso | `aprobacionFinanciera` |
| Nombre | `Aprobación financiera` |
| Versión inicial | 1 en una base de datos limpia; Flowable asigna la versión |
| Ejecutable | Sí |

El proceso recibe el plan de aprobación ya calculado por la aplicación. Las compuertas leen
las banderas `requiereAprobacion`, `requiereSupervisor`, `requiereGerente` y
`requiereRiesgos`; no recalculan montos, riesgos ni políticas.

Cada rama tiene su propia variable de decisión: `decisionSupervisor`, `decisionGerente` y
`decisionRiesgos`. Así una aprobación o un rechazo no pisa el resultado de otra rama.

## Flujo

1. Verifica la información persistida de la solicitud y la revisión.
2. Verifica que el riesgo esté clasificado.
3. Verifica que el plan recibido coincida con el calculado.
4. Si el plan está vacío, pasa directamente a la verificación de autorización.
5. Si hay aprobaciones, deja la revisión en `PENDIENTE_APROBACION` y abre una compuerta
   inclusiva con las ramas de Supervisor, Gerente y Riesgos que correspondan.
6. Cada tarea humana puede aprobar o rechazar. Un rechazo pasa por `registrarRechazo` y
   termina la instancia con `terminateAll=true`.
7. Las aprobaciones se unen en una compuerta inclusiva y luego se verifica la autorización.
8. Si el monto requiere evaluación externa, el proceso entra en un gateway de eventos que
   compite entre el mensaje `RIESGO_EVALUADO` y el timer `vencerRiesgoExterno`. La
   aplicación guarda el evento firmado en el inbox, consolida el máximo entre riesgo interno
   y externo y envía el mensaje a la instancia específica. El delegate lee la evaluación
   persistida, verifica el evento aplicado y solo entonces permite continuar. Un evento
   temprano queda pendiente y se reintenta; uno de una revisión anterior, cancelada, en
   revisión manual o terminal queda obsoleto. El evento no crea ni modifica aprobaciones.
   Si vence el timer, la evaluación externa pendiente queda `VENCIDA`, la revisión pasa a
   `REVISION_MANUAL` y se abre `tareaRevisionRiesgoManual`.
9. Marca la revisión como `APROBADA`, persiste la intención de ejecución y la deja
   en `EJECUTANDO`. El proceso espera el mensaje `EJECUCION_FINALIZADA`.
10. El trabajador de ejecución reclama la intención, reserva y confirma contra el
    proveedor simulado fuera de la transacción y envía el resultado. La compuerta
    `decidirResultadoEjecucion` distingue el resultado confirmado del desconocido:
    - `CONFIRMADA` pasa por `marcarEjecutada` y termina en `finProceso`;
    - el flujo por defecto pasa por `marcarResultadoDesconocido` y termina en
      `finResultadoDesconocido`.
11. La cancelación se solicita por API. La aplicación cancela las tareas, registra el
    evento y, si corresponde, libera la reserva antes de enviar
    `CANCELACION_SOLICITADA`. El subproceso de eventos interrumpe tareas, timers y el
    flujo principal y termina en `finCancelacion`.

## Actividades del proceso

| Actividad | Dónde se implementa |
|---|---|
| Registrar solicitud | `POST /api/v1/solicitudes` y `ServicioRegistroSolicitud`; la instancia se inicia en `inicioSolicitud`. |
| Validar información obligatoria | `ServicioValidacionSolicitud` con `ValidadorSolicitudCompuesto`; el proceso verifica la información persistida en `verificarSolicitud`. |
| Determinar nivel de riesgo | `ServicioClasificacionRiesgo` y `ClasificadorRiesgoInterno`; el proceso comprueba el nivel en `verificarRiesgo`. |
| Seleccionar la ruta de aprobación | `CalculadoraPlanAprobacion`; las compuertas `decidirAprobaciones` y `ramasAprobacion` activan solo las ramas necesarias. |
| Ejecutar las aprobaciones | Tareas `tareaSupervisor`, `tareaGerente` y `tareaRiesgos`, con `ServicioDecisionAprobacion`. |
| Verificar las condiciones de autorización | `verificarAutorizacion` y `VerificadorAutorizacionEjecucion`. |
| Ejecutar la operación | `prepararEjecucion`, `ServicioIntencionEjecucion` y `TrabajadorEjecucion`. El proceso espera `EJECUCION_FINALIZADA`. |
| Registrar el resultado | `marcarEjecutada` o `marcarResultadoDesconocido`, con el estado durable de `intento_ejecucion`. |
| Finalizar el proceso | `finProceso`, `finResultadoDesconocido`, `finRechazo` y `finCancelacion`. |

## Inventario del proceso

### Tareas humanas

| Nodo | Nombre | Grupos candidatos |
|---|---|---|
| `tareaSupervisor` | Aprobación de Supervisor | `SUPERVISOR` |
| `tareaGerente` | Aprobación de Gerente | `GERENTE` |
| `tareaRiesgos` | Aprobación de Riesgos | `RIESGOS` |
| `tareaRevisionRiesgoManual` | Revisión manual de riesgo | `ANALISTA_RIESGOS`, `JEFE_RIESGOS` |

Cada tarea de aprobación tiene dos temporizadores de frontera no interruptores:
notificación a las 24 horas y reasignación a las 48 horas. La revisión manual se
abre cuando vence la espera del riesgo externo y su resolución no crea
aprobaciones.

### Tareas automáticas

| Nodo | Delegado | Propósito |
|---|---|---|
| `verificarSolicitud` | `delegadoVerificarSolicitud` | Comprueba la solicitud y la revisión persistidas. |
| `verificarRiesgo` | `delegadoVerificarRiesgo` | Exige el riesgo clasificado. |
| `verificarPlan` | `delegadoVerificarPlan` | Compara el plan recibido con el calculado. |
| `marcarPendienteAprobacion` | `delegadoMarcarPendienteAprobacion` | Deja la revisión en `PENDIENTE_APROBACION`. |
| `verificarAutorizacion` | `delegadoVerificarAutorizacion` | Comprueba que todas las aprobaciones requeridas estén resueltas. |
| `prepararRevisionManual` | `delegadoPrepararRevisionManual` | Marca la evaluación vencida y prepara la tarea manual. |
| `verificarRiesgoExterno` | `delegadoVerificarRiesgoExterno` | Lee la evaluación persistida y valida el evento aplicado. |
| `marcarAprobada` | `delegadoMarcarAprobada` | Transiciona la revisión a `APROBADA`. |
| `prepararEjecucion` | `delegadoPrepararEjecucion` | Persiste la intención durable y deja la revisión en `EJECUTANDO`. |
| `marcarEjecutada` | `delegadoMarcarEjecutada` | Registra el resultado confirmado. |
| `marcarResultadoDesconocido` | `delegadoMarcarResultadoDesconocido` | Registra el resultado incierto y el incidente. |
| `registrarRechazo` | `delegadoRegistrarRechazo` | Cancela las tareas y cierra la revisión como `RECHAZADA`. |
| `notificarSupervisor`, `notificarGerente`, `notificarRiesgos` | `delegadoNotificarEscalamiento` | Crean la notificación de las 24 horas. |
| `reasignarSupervisor`, `reasignarGerente`, `reasignarRiesgos` | `delegadoReasignarTarea` | Reasignan la tarea al superior a las 48 horas. |

### Compuertas y eventos

| Nodo | Tipo | Función |
|---|---|---|
| `decidirAprobaciones` | Exclusiva | Decide si el plan requiere aprobaciones. |
| `ramasAprobacion` | Inclusiva | Activa las ramas de Supervisor, Gerente y Riesgos. |
| `decidirSupervisor`, `decidirGerente`, `decidirRiesgos` | Exclusivas | Separan aprobación y rechazo de cada rama. |
| `unionAprobaciones` | Inclusiva | Espera todas las ramas activas. |
| `decidirRiesgoExterno` | Exclusiva | Decide si hace falta evaluación externa. |
| `esperarEventoRiesgoExterno` | Basada en eventos | Compite entre `RIESGO_EVALUADO` y el vencimiento. |
| `decidirRevisionManual` | Exclusiva | Decide si la revisión manual puede continuar o cerrar. |
| `decidirResultadoEjecucion` | Exclusiva | Distingue resultado confirmado y desconocido. |
| `inicioSolicitud` | Inicio | Primer nodo del proceso. |
| `finProceso` | Fin | Ejecución confirmada. |
| `finResultadoDesconocido` | Fin | Ejecución con resultado incierto. |
| `finRechazo` | Fin | Rechazo con `terminateAll`. |
| `finCancelacion` | Fin | Cierre del subproceso de cancelación. |
| `subprocesoCancelacion` | Subproceso de eventos | Escucha `CANCELACION_SOLICITADA` e interrumpe el alcance. |

### Eventos y temporizadores

| Nodo | Tipo | Función |
|---|---|---|
| `esperarRiesgoExterno` | Evento intermedio de mensaje | Espera `RIESGO_EVALUADO` con el resultado externo. |
| `vencerRiesgoExterno` | Evento intermedio de temporizador | Vence la espera y abre la revisión manual. |
| `esperarResultadoEjecucion` | Evento intermedio de mensaje | Espera `EJECUCION_FINALIZADA` del trabajador. |
| `notificarSupervisor24h`, `notificarGerente24h`, `notificarRiesgos24h` | Temporizadores de frontera no interruptores | Crean la notificación de escalamiento. |
| `reasignarSupervisor48h`, `reasignarGerente48h`, `reasignarRiesgos48h` | Temporizadores de frontera no interruptores | Reasignan la tarea al superior. |
| `inicioCancelacion` | Evento de inicio | Inicio del subproceso de cancelación. |

Los temporizadores usan variables ISO-8601 (`duracionNotificacion`,
`duracionReasignacion` y `duracionRiesgoExterno`); no están fijados en el XML.

## Comportamiento ante rechazo, cancelación, vencimiento y fallo

| Situación | Comportamiento |
|---|---|
| Rechazo | `registrarRechazo` cancela las tareas pendientes, marca la revisión `RECHAZADA` y termina la instancia con `terminateAll`. |
| Cancelación | La aplicación libera la reserva antes de enviar `CANCELACION_SOLICITADA`. El subproceso interrumpe tareas y timers. |
| Vencimiento del riesgo externo | La evaluación queda `VENCIDA`, la revisión pasa a `REVISION_MANUAL` y se abre `tareaRevisionRiesgoManual`. |
| Resultado desconocido | Se persiste `RESULTADO_DESCONOCIDO`, se crea un incidente y el proceso termina en `finResultadoDesconocido`. |
| Fallo del proveedor | El trabajador reintenta con la misma clave. Si se agota, el incidente queda para reintento operativo. |
| Fallo de compensación | La revisión queda `COMPENSANDO`, el proceso sigue activo y el trabajador reintenta o abre un incidente. |

La espera externa se ubica después de la unión para que las tareas puedan abrirse y
resolverse sin depender del proveedor. La integración simulada, los duplicados, los
conflictos, los eventos tempranos, el vencimiento y la revisión manual están implementados.
El timer usa la variable ISO-8601 `duracionRiesgoExterno`, configurable por ambiente.

La tarea `tareaRevisionRiesgoManual` tiene grupos candidatos `ANALISTA_RIESGOS` y
`JEFE_RIESGOS`. Un `TaskListener` copia la tarea a `tarea_revision_riesgo` cuando Flowable
la crea. La resolución registra nivel, motivo y evidencia, consolida el riesgo y recalcula
el plan; no crea decisiones de aprobación. Después de resolver, el proceso vuelve a
`PENDIENTE_APROBACION` y continúa por la verificación ya existente.

La espera de ejecución no llama al proveedor: el delegate solo persiste la intención y
transiciona la revisión. El trabajador de aplicación hace la llamada externa y luego
envía `EJECUCION_FINALIZADA`. Si el trabajador está deshabilitado, la revisión queda en
`EJECUTANDO` con la intención `PENDIENTE`; al reiniciar, el trabajador la retoma.

Cuando el resultado es incierto, el trabajador persiste `RESULTADO_DESCONOCIDO`, crea un
incidente y consulta al proveedor con la misma clave antes de cerrar el proceso. Si la
consulta confirma, el mensaje enviado es `CONFIRMADA` y el proceso termina en
`finProceso`. Si no se puede resolver, el proceso termina en `finResultadoDesconocido` y
el incidente queda abierto para el reintento operativo. La recuperación posterior puede
actualizar la revisión a `EJECUTADA` directamente si el proceso ya terminó.

## Cancelación

El proceso tiene un subproceso de eventos interrumpible `subprocesoCancelacion` con el
mensaje `CANCELACION_SOLICITADA`. La aplicación registra la cancelación y, si hay una
reserva no confirmada, la libera antes de enviar el mensaje. Así el cierre del alcance
BPMN no destruye la compensación: la compensación se orquesta en la aplicación y el
subproceso solo detiene el flujo.

- Si el proceso está activo y espera el mensaje, el subproceso interrumpe las tareas
  humanas y los timers y termina en `finCancelacion`.
- Si el proceso es de una versión anterior sin esa suscripción, el adaptador elimina la
  instancia con un motivo controlado.
- Si el proceso ya terminó, la cancelación no vuelve a intervenir en el motor.

La cancelación no se declara antes de liberar la reserva. Si la liberación falla o el
resultado es incierto, la revisión queda `COMPENSANDO`, el proceso sigue activo y el
trabajador de compensación consulta o reintenta con la misma clave. El mensaje
`CANCELACION_SOLICITADA` se envía recién cuando la liberación queda confirmada. Si la
confirmación de ejecución gana la carrera, la revisión pasa a `EJECUTADA`, la
compensación se rechaza y el proceso cierra por el flujo de ejecución.

Cada tarea humana tiene dos temporizadores de frontera no interruptores:

| Tarea | Notificación | Reasignación |
|---|---|---|
| Supervisor | `notificarSupervisor24h` | `reasignarSupervisor48h` |
| Gerente | `notificarGerente24h` | `reasignarGerente48h` |
| Riesgos | `notificarRiesgos24h` | `reasignarRiesgos48h` |

Las duraciones se reciben como variables ISO-8601 (`duracionNotificacion`,
`duracionReasignacion` y `duracionRiesgoExterno`). No están fijadas en el XML.

El temporizador de 24 horas crea una notificación persistida para el rol funcional. El de
48 horas reasigna la tarea al superior configurado, conserva el rol original y no completa la
tarea. Si no hay un superior activo, registra `REASIGNACION_FALLIDA` y notifica al rol
`ADMINISTRADOR`; la tarea sigue pendiente. La decisión del delegado conserva el rol funcional
y queda marcada como delegada.

## Política de versiones

- Una instancia existente permanece en la versión con la que se inició.
- No se migran instancias automáticamente.
- Un cambio en el XML crea una versión nueva y solo afecta a procesos nuevos.
- Cualquier migración explícita futura requiere una decisión y una prueba propias.

## Diagramas

Los archivos `aprobacion-financiera.png` y `aprobacion-financiera.svg` se generan desde el
mismo XML. No se editan por separado.

En Windows, con Chrome instalado:

```powershell
$env:PUPPETEER_EXECUTABLE_PATH="C:\Program Files\Google\Chrome\Application\chrome.exe"
npx.cmd --yes bpmn-to-image "src/main/resources/processes/aprobacion-financiera.bpmn20.xml;docs/bpmn/aprobacion-financiera.png,docs/bpmn/aprobacion-financiera.svg" --no-title --no-footer
```

En otros sistemas, el separador entre el XML y las salidas puede ser `:` según la versión de
`bpmn-to-image`.

## Validación

La copia de `docs/bpmn` se compara con el modelo desplegado mediante el parseo del XML, no
por bytes. La prueba también verifica que todos los nodos y flujos tengan información
gráfica y que los diagramas existan y no estén vacíos.

| Comprobación | Resultado esperado |
|---|---|
| Copia de `docs/bpmn` contra `src/main/resources/processes` | Mismo modelo al parsear el XML. |
| Información gráfica de nodos y flujos | Presente en todos los elementos. |
| Diagramas PNG y SVG | Existen y tienen contenido. |
| Despliegue del proceso | `aprobacionFinanciera` se inicia y avanza con Flowable real. |

```powershell
.\mvnw.cmd "-Dtest=PruebaProcesoPrincipalTest" test
```

## Alcance

La API inicia el proceso al crear la solicitud, persiste el identificador de instancia en la
revisión y sincroniza cada tarea humana con `tarea_aprobacion`. El camino sin aprobaciones
queda en `EJECUTANDO` con la intención pendiente; los caminos con aprobaciones quedan en
`PENDIENTE_APROBACION` y la bandeja `GET /api/v1/tareas-aprobacion` muestra las tareas visibles
para cada aprobador o delegado. Los endpoints `POST /api/v1/tareas-aprobacion/{id}/aprobar` y
`/rechazar` registran la decisión y avanzan el motor en la misma transacción. Un rechazo cancela
las demás tareas de negocio de la revisión y usa el fin de terminación del proceso para cerrar
las ramas restantes. El reenvío no modifica el BPMN: crea una revisión y una instancia nuevas y
conserva la anterior con su historial. El temporizador de 24 horas crea una notificación
persistida y consultable por el rol destinatario; el de 48 horas reasigna la tarea al superior
configurado y permite resolverla por delegación. El paralelismo completo, la espera de riesgo
externo, la ejecución idempotente simulada, el cierre por resultado desconocido y la
cancelación con subproceso de eventos están probados en el proceso real.
