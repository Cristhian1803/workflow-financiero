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
8. Si el monto requiere evaluación externa, espera el mensaje `RIESGO_EVALUADO`. El fixture
   de pruebas envía `riesgoExternoResuelto=true` y `nivelRiesgoExterno`; el delegate registra
   `EVALUACION_EXTERNA_RESUELTA` y solo entonces permite continuar. El nivel externo no puede
   ser menor que el riesgo interno.
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

La espera externa se ubica después de la unión para que las tareas puedan abrirse y
resolverse sin depender del proveedor. La integración real, los duplicados y los eventos
tardíos son de etapas próximas.

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
resultado es incierto, la revisión queda `COMPENSANDO` y la recuperación se completa en
la siguiente iteración.

Cada tarea humana tiene dos temporizadores de frontera no interruptores:

| Tarea | Notificación | Reasignación |
|---|---|---|
| Supervisor | `notificarSupervisor24h` | `reasignarSupervisor48h` |
| Gerente | `notificarGerente24h` | `reasignarGerente48h` |
| Riesgos | `notificarRiesgos24h` | `reasignarRiesgos48h` |

Las duraciones se reciben como variables ISO-8601 (`duracionNotificacion` y
`duracionReasignacion`). No están fijadas en el XML.

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
