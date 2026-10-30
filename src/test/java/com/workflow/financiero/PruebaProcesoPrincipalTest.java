package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.EventDefinition;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.Message;
import org.flowable.bpmn.model.MessageEventDefinition;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.TerminateEventDefinition;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifica que el proceso principal esté desplegado, que su modelo contenga el
 * flujo esperado y que el XML de documentación y los diagramas sean coherentes.
 */
@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
class PruebaProcesoPrincipalTest extends PruebaIntegracionBase {

    private static final String CLAVE_PROCESO = "aprobacionFinanciera";
    private static final Path RUTA_DOCS = Path.of(
        "docs", "bpmn", "aprobacion-financiera.bpmn20.xml");
    private static final Path RUTA_PNG = Path.of(
        "docs", "bpmn", "aprobacion-financiera.png");
    private static final Path RUTA_SVG = Path.of(
        "docs", "bpmn", "aprobacion-financiera.svg");

    @Autowired
    RepositoryService repositoryService;

    @Test
    @DisplayName("El proceso principal está desplegado y es ejecutable")
    void procesoDesplegadoYEjecutable() {
        ProcessDefinition definicion = definicion();
        BpmnModel modelo = repositoryService.getBpmnModel(definicion.getId());

        assertThat(definicion.getKey()).isEqualTo(CLAVE_PROCESO);
        assertThat(definicion.getName()).isEqualTo("Aprobación financiera");
        assertThat(definicion.isSuspended()).isFalse();
        assertThat(modelo.getMainProcess().isExecutable()).isTrue();
    }

    @Test
    @DisplayName("El modelo contiene las actividades, compuertas y eventos esperados")
    void modeloContieneLosElementosEsperados() {
        Process proceso = modelo().getMainProcess();

        assertThat(proceso.getFlowElement("inicioSolicitud")).isNotNull();
        assertThat(proceso.getFlowElement("verificarSolicitud")).isInstanceOf(ServiceTask.class);
        assertThat(proceso.getFlowElement("verificarRiesgo")).isInstanceOf(ServiceTask.class);
        assertThat(proceso.getFlowElement("verificarPlan")).isInstanceOf(ServiceTask.class);
        assertThat(proceso.getFlowElement("marcarPendienteAprobacion")).isInstanceOf(ServiceTask.class);
        assertThat(proceso.getFlowElement("verificarAutorizacion")).isInstanceOf(ServiceTask.class);
        assertThat(proceso.getFlowElement("verificarRiesgoExterno")).isInstanceOf(ServiceTask.class);
        assertThat(proceso.getFlowElement("marcarAprobada")).isInstanceOf(ServiceTask.class);
        assertThat(proceso.getFlowElement("prepararEjecucion")).isInstanceOf(ServiceTask.class);
        assertThat(proceso.getFlowElement("registrarResultado")).isInstanceOf(ServiceTask.class);
        assertThat(proceso.getFlowElement("registrarRechazo")).isInstanceOf(ServiceTask.class);
        assertThat(proceso.getFlowElement("decidirAprobaciones")).isNotNull();
        assertThat(proceso.getFlowElement("decidirRiesgoExterno")).isNotNull();
        assertThat(proceso.getFlowElement("ramasAprobacion")).isInstanceOf(InclusiveGateway.class);
        assertThat(proceso.getFlowElement("unionAprobaciones")).isInstanceOf(InclusiveGateway.class);
        assertThat(proceso.getFlowElement("tareaSupervisor")).isInstanceOf(UserTask.class);
        assertThat(proceso.getFlowElement("tareaGerente")).isInstanceOf(UserTask.class);
        assertThat(proceso.getFlowElement("tareaRiesgos")).isInstanceOf(UserTask.class);
        assertThat(proceso.getFlowElement("esperarRiesgoExterno"))
            .isInstanceOf(IntermediateCatchEvent.class);
        assertThat(proceso.getFlowElement("finProceso")).isInstanceOf(EndEvent.class);
        assertThat(proceso.getFlowElement("finRechazo")).isInstanceOf(EndEvent.class);
    }

    @Test
    @DisplayName("Las tareas humanas tienen grupos candidatos y dos timers no interruptores")
    void tareasHumanasTienenTimersNoInterruptores() {
        Process proceso = modelo().getMainProcess();
        Map<String, String> roles = Map.of(
            "tareaSupervisor", "SUPERVISOR",
            "tareaGerente", "GERENTE",
            "tareaRiesgos", "RIESGOS");

        roles.forEach((tareaId, rol) -> {
            UserTask tarea = (UserTask) proceso.getFlowElement(tareaId);
            assertThat(tarea.getCandidateGroups()).containsExactly(rol);

            List<BoundaryEvent> timers = proceso.findFlowElementsOfType(BoundaryEvent.class)
                .stream()
                .filter(evento -> tareaId.equals(evento.getAttachedToRefId()))
                .toList();

            assertThat(timers).as("timers de %s", tareaId).hasSize(2);
            assertThat(timers).allMatch(evento -> !evento.isCancelActivity());
            assertThat(timers)
                .extracting(this::duracionTimer)
                .containsExactlyInAnyOrder(
                    "${duracionNotificacion}", "${duracionReasignacion}");
            assertThat(timers)
                .extracting(evento -> evento.getOutgoingFlows().get(0).getTargetRef())
                .containsExactlyInAnyOrder(
                    "notificar" + sufijo(tareaId),
                    "reasignar" + sufijo(tareaId));
        });
    }

    @Test
    @DisplayName("La compuerta inclusiva activa tres ramas y la unión las espera")
    void compuertaInclusivaTieneTresRamasYUnaUnion() {
        Process proceso = modelo().getMainProcess();
        InclusiveGateway ramas = (InclusiveGateway) proceso.getFlowElement("ramasAprobacion");
        InclusiveGateway union = (InclusiveGateway) proceso.getFlowElement("unionAprobaciones");

        assertThat(ramas.getOutgoingFlows()).hasSize(3);
        assertThat(ramas.getOutgoingFlows())
            .extracting(SequenceFlow::getTargetRef)
            .containsExactlyInAnyOrder(
                "tareaSupervisor", "tareaGerente", "tareaRiesgos");
        assertThat(ramas.getOutgoingFlows())
            .allMatch(flujo -> flujo.getConditionExpression() != null
                && !flujo.getConditionExpression().isBlank());

        assertThat(union.getIncomingFlows()).hasSize(3);
        assertThat(union.getIncomingFlows())
            .extracting(SequenceFlow::getSourceRef)
            .containsExactlyInAnyOrder(
                "decidirSupervisor", "decidirGerente", "decidirRiesgos");
        assertThat(union.getOutgoingFlows()).hasSize(1);
        assertThat(union.getOutgoingFlows().get(0).getTargetRef())
            .isEqualTo("verificarAutorizacion");
    }

    @Test
    @DisplayName("Las condiciones, flujos por defecto y delegates son los esperados")
    void condicionesYDelegatesSonLosEsperados() {
        Process proceso = modelo().getMainProcess();
        ExclusiveGateway decidirAprobaciones = (ExclusiveGateway) proceso
            .getFlowElement("decidirAprobaciones");
        ExclusiveGateway decidirRiesgoExterno = (ExclusiveGateway) proceso
            .getFlowElement("decidirRiesgoExterno");

        assertThat(decidirAprobaciones.getDefaultFlow()).isEqualTo("flujoSinAprobacion");
        assertThat(decidirRiesgoExterno.getDefaultFlow()).isEqualTo("flujoSinRiesgoExterno");

        assertThat(condicion(proceso, "flujoRequiereAprobacion"))
            .contains("requiereAprobacion");
        assertThat(condicion(proceso, "flujoSupervisor"))
            .contains("requiereSupervisor");
        assertThat(condicion(proceso, "flujoGerente"))
            .contains("requiereGerente");
        assertThat(condicion(proceso, "flujoRiesgos"))
            .contains("requiereRiesgos");
        assertThat(condicion(proceso, "flujoRequiereRiesgoExterno"))
            .contains("requiereEvaluacionExterna");

        for (String rol : List.of("Supervisor", "Gerente", "Riesgos")) {
            assertThat(condicion(proceso, "flujo" + rol + "Aprobado"))
                .contains("APROBADA");
            assertThat(condicion(proceso, "flujo" + rol + "Rechazado"))
                .contains("RECHAZADA");
        }

        Map<String, String> delegates = Map.ofEntries(
            Map.entry("verificarSolicitud", "delegadoVerificarSolicitud"),
            Map.entry("verificarRiesgo", "delegadoVerificarRiesgo"),
            Map.entry("verificarPlan", "delegadoVerificarPlan"),
            Map.entry("marcarPendienteAprobacion", "delegadoMarcarPendienteAprobacion"),
            Map.entry("verificarAutorizacion", "delegadoVerificarAutorizacion"),
            Map.entry("verificarRiesgoExterno", "delegadoVerificarRiesgoExterno"),
            Map.entry("marcarAprobada", "delegadoMarcarAprobada"),
            Map.entry("prepararEjecucion", "delegadoPrepararEjecucion"),
            Map.entry("registrarResultado", "delegadoRegistrarResultado"),
            Map.entry("registrarRechazo", "delegadoRegistrarRechazo"),
            Map.entry("notificarSupervisor", "delegadoNotificarEscalamiento"),
            Map.entry("notificarGerente", "delegadoNotificarEscalamiento"),
            Map.entry("notificarRiesgos", "delegadoNotificarEscalamiento"),
            Map.entry("reasignarSupervisor", "delegadoReasignarTarea"),
            Map.entry("reasignarGerente", "delegadoReasignarTarea"),
            Map.entry("reasignarRiesgos", "delegadoReasignarTarea"));

        delegates.forEach((tareaId, expresion) -> {
            ServiceTask tarea = (ServiceTask) proceso.getFlowElement(tareaId);
            assertThat(tarea.getImplementation())
                .as("delegate de %s", tareaId)
                .contains(expresion);
        });
    }

    @Test
    @DisplayName("El rechazo termina todas las ramas y el fin normal no lo hace")
    void rechazoTerminaTodasLasRamas() {
        Process proceso = modelo().getMainProcess();
        EndEvent finRechazo = (EndEvent) proceso.getFlowElement("finRechazo");
        EndEvent finProceso = (EndEvent) proceso.getFlowElement("finProceso");

        TerminateEventDefinition terminacion = finRechazo.getEventDefinitions().stream()
            .filter(TerminateEventDefinition.class::isInstance)
            .map(TerminateEventDefinition.class::cast)
            .findFirst()
            .orElseThrow();
        assertThat(terminacion.isTerminateAll()).isTrue();
        assertThat(finProceso.getEventDefinitions())
            .noneMatch(TerminateEventDefinition.class::isInstance);
    }

    @Test
    @DisplayName("La espera externa usa el mensaje RIESGO_EVALUADO")
    void esperaExternaUsaElMensajeEsperado() {
        BpmnModel modelo = modelo();
        IntermediateCatchEvent espera = (IntermediateCatchEvent) modelo
            .getMainProcess()
            .getFlowElement("esperarRiesgoExterno");

        MessageEventDefinition definicionMensaje = espera.getEventDefinitions().stream()
            .filter(MessageEventDefinition.class::isInstance)
            .map(MessageEventDefinition.class::cast)
            .findFirst()
            .orElseThrow();
        assertThat(definicionMensaje.getMessageRef()).isEqualTo("RIESGO_EVALUADO");
        Message mensaje = modelo.getMessages().stream()
            .filter(candidato -> "RIESGO_EVALUADO".equals(candidato.getName()))
            .findFirst()
            .orElseThrow();

        assertThat(mensaje.getId()).isEqualTo("mensajeRiesgoEvaluado");
    }

    @Test
    @DisplayName("Todos los nodos y flujos tienen información gráfica")
    void todosLosElementosTienenDi() {
        BpmnModel modelo = modelo();
        Collection<FlowElement> elementos = modelo.getMainProcess().getFlowElements();

        for (FlowElement elemento : elementos) {
            if (elemento instanceof SequenceFlow flujo) {
                assertThat(modelo.getFlowLocationGraphicInfo(flujo.getId()))
                    .as("DI del flujo %s", flujo.getId())
                    .isNotNull()
                    .hasSizeGreaterThanOrEqualTo(2);
            } else if (elemento instanceof FlowNode nodo) {
                assertThat(modelo.getGraphicInfo(nodo.getId()))
                    .as("DI del nodo %s", nodo.getId())
                    .isNotNull();
                assertThat(modelo.getGraphicInfo(nodo.getId()).getWidth())
                    .as("ancho de %s", nodo.getId())
                    .isPositive();
                assertThat(modelo.getGraphicInfo(nodo.getId()).getHeight())
                    .as("alto de %s", nodo.getId())
                    .isPositive();
            }
        }
    }

    @Test
    @DisplayName("La copia de docs representa el mismo modelo desplegado")
    void copiaDeDocsRepresentaElMismoModelo() {
        assertThat(RUTA_DOCS).exists();

        BpmnModel desplegado = modelo();
        BpmnModel copia = new BpmnXMLConverter().convertToBpmnModel(
            () -> {
                try {
                    return Files.newInputStream(RUTA_DOCS);
                } catch (IOException excepcion) {
                    throw new UncheckedIOException(excepcion);
                }
            },
            false,
            false);

        assertThat(firma(copia)).isEqualTo(firma(desplegado));
    }

    @Test
    @DisplayName("Los diagramas PNG y SVG existen y tienen contenido")
    void diagramasExistenYNoEstanVacios() throws IOException {
        assertThat(RUTA_PNG).exists().isRegularFile();
        assertThat(RUTA_SVG).exists().isRegularFile();
        assertThat(Files.size(RUTA_PNG)).isPositive();
        assertThat(Files.size(RUTA_SVG)).isPositive();

        byte[] png = Files.readAllBytes(RUTA_PNG);
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');

        assertThat(Files.readString(RUTA_SVG)).contains("<svg");
    }

    private String condicion(Process proceso, String flujoId) {
        SequenceFlow flujo = (SequenceFlow) proceso.getFlowElement(flujoId);
        assertThat(flujo).as("flujo %s", flujoId).isNotNull();
        assertThat(flujo.getConditionExpression())
            .as("condición de %s", flujoId)
            .isNotNull();
        return flujo.getConditionExpression();
    }

    private ProcessDefinition definicion() {
        ProcessDefinition definicion = repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionKey(CLAVE_PROCESO)
            .latestVersion()
            .singleResult();
        assertThat(definicion).as("proceso %s desplegado", CLAVE_PROCESO).isNotNull();
        return definicion;
    }

    private BpmnModel modelo() {
        return repositoryService.getBpmnModel(definicion().getId());
    }

    private String duracionTimer(BoundaryEvent evento) {
        EventDefinition definicion = evento.getEventDefinitions().get(0);
        return ((TimerEventDefinition) definicion).getTimeDuration();
    }

    private String sufijo(String tareaId) {
        return switch (tareaId) {
            case "tareaSupervisor" -> "Supervisor";
            case "tareaGerente" -> "Gerente";
            case "tareaRiesgos" -> "Riesgos";
            default -> throw new IllegalArgumentException("Tarea desconocida: " + tareaId);
        };
    }

    private FirmaProceso firma(BpmnModel modelo) {
        Process proceso = modelo.getMainProcess();
        Map<String, String> elementos = new TreeMap<>();
        Map<String, String> flujos = new TreeMap<>();
        Map<String, String> timers = new TreeMap<>();
        Map<String, Boolean> terminaciones = new TreeMap<>();

        for (FlowElement elemento : proceso.getFlowElements()) {
            elementos.put(elemento.getId(), elemento.getClass().getSimpleName());

            if (elemento instanceof SequenceFlow flujo) {
                String condicion = flujo.getConditionExpression() == null
                    ? ""
                    : flujo.getConditionExpression();
                flujos.put(flujo.getId(), flujo.getSourceRef() + "->"
                    + flujo.getTargetRef() + "|" + condicion);
            }

            if (elemento instanceof BoundaryEvent boundary) {
                timers.put(boundary.getId(), boundary.getAttachedToRefId()
                    + "|" + boundary.isCancelActivity()
                    + "|" + duracionTimer(boundary));
            }

            if (elemento instanceof EndEvent fin) {
                boolean termina = fin.getEventDefinitions().stream()
                    .filter(TerminateEventDefinition.class::isInstance)
                    .map(TerminateEventDefinition.class::cast)
                    .anyMatch(TerminateEventDefinition::isTerminateAll);
                terminaciones.put(fin.getId(), termina);
            }
        }

        Map<String, String> mensajes = modelo.getMessages().stream()
            .collect(Collectors.toMap(
                Message::getId,
                Message::getName,
                (existente, nuevo) -> existente,
                TreeMap::new));

        return new FirmaProceso(
            proceso.getId(),
            proceso.getName(),
            proceso.isExecutable(),
            elementos,
            flujos,
            timers,
            mensajes,
            terminaciones);
    }

    private record FirmaProceso(
        String id,
        String nombre,
        boolean ejecutable,
        Map<String, String> elementos,
        Map<String, String> flujos,
        Map<String, String> timers,
        Map<String, String> mensajes,
        Map<String, Boolean> terminaciones
    ) {
    }
}
