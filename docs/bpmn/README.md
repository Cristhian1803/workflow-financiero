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
9. Marca la revisión como `APROBADA`, prepara la ejecución controlada y registra el
   resultado simulado. La operación financiera real no se confirma aun.

La espera externa se ubica después de la unión para que las tareas puedan abrirse y
resolverse sin depender del proveedor. La integración real, los duplicados y los eventos
tardíos son de etapas próximas.

Cada tarea humana tiene dos temporizadores de frontera no interruptores:

| Tarea | Notificación | Reasignación |
|---|---|---|
| Supervisor | `notificarSupervisor24h` | `reasignarSupervisor48h` |
| Gerente | `notificarGerente24h` | `reasignarGerente48h` |
| Riesgos | `notificarRiesgos24h` | `reasignarRiesgos48h` |

Las duraciones se reciben como variables ISO-8601 (`duracionNotificacion` y
`duracionReasignacion`). No están fijadas en el XML.

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
termina en `APROBADA`; los caminos con aprobaciones quedan en `PENDIENTE_APROBACION` y la
bandeja `GET /api/v1/tareas-aprobacion` muestra las tareas visibles para cada aprobador o
delegado. Los endpoints `POST /api/v1/tareas-aprobacion/{id}/aprobar` y `/rechazar` registran
la decisión y avanzan el motor en la misma transacción. Un rechazo cancela las demás tareas
de negocio de la revisión y usa el fin de terminación del proceso para cerrar las ramas
restantes. El paralelismo completo y la espera de riesgo externo se prueban en la etapa
siguiente.
