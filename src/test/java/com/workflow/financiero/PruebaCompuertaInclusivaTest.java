package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Comprueba con un proceso mínimo que la compuerta inclusiva de Flowable 7.2.0
 * activa solo las ramas requeridas y que la unión espera únicamente esas ramas.
 */
@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
class PruebaCompuertaInclusivaTest extends PruebaIntegracionBase {

    private static final String CLAVE_PROCESO = "pruebaCompuertaInclusiva";

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    TaskService taskService;

    @Autowired
    HistoryService historyService;

    @AfterEach
    void limpiarProcesos() {
        runtimeService.createProcessInstanceQuery()
            .processDefinitionKey(CLAVE_PROCESO)
            .list()
            .forEach(proceso -> runtimeService.deleteProcessInstance(
                proceso.getId(), "Limpieza de prueba"));

        historyService.createHistoricProcessInstanceQuery()
            .processDefinitionKey(CLAVE_PROCESO)
            .list()
            .forEach(proceso -> historyService.deleteHistoricProcessInstance(
                proceso.getId()));
    }

    @Test
    @DisplayName("Sin ramas requeridas el proceso termina sin crear tareas")
    void sinRamasTerminaSinTareas() {
        ProcessInstance proceso = iniciar(false, false, false);

        assertThat(tareas(proceso.getId())).isEmpty();
        assertThat(estaActivo(proceso.getId())).isFalse();
        assertThat(historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(proceso.getId())
            .singleResult()
            .getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("Una sola rama crea únicamente su tarea")
    void unaRamaCreaSuTarea() {
        ProcessInstance proceso = iniciar(true, false, false);

        assertThat(clavesDeTareas(proceso.getId()))
            .containsExactly("tareaSupervisor");

        completar(proceso.getId(), "tareaSupervisor", "APROBADA");

        assertThat(estaActivo(proceso.getId())).isFalse();
    }

    @Test
    @DisplayName("Dos ramas esperan las dos decisiones")
    void dosRamasEsperanAmbasDecisiones() {
        ProcessInstance proceso = iniciar(true, true, false);

        assertThat(clavesDeTareas(proceso.getId()))
            .containsExactlyInAnyOrder("tareaSupervisor", "tareaGerente");

        completar(proceso.getId(), "tareaSupervisor", "APROBADA");

        assertThat(estaActivo(proceso.getId())).isTrue();
        assertThat(clavesDeTareas(proceso.getId()))
            .containsExactly("tareaGerente");

        completar(proceso.getId(), "tareaGerente", "APROBADA");

        assertThat(estaActivo(proceso.getId())).isFalse();
    }

    @Test
    @DisplayName("Tres ramas esperan las tres decisiones en cualquier orden")
    void tresRamasEsperanTodasLasDecisiones() {
        ProcessInstance proceso = iniciar(true, true, true);

        assertThat(clavesDeTareas(proceso.getId()))
            .containsExactlyInAnyOrder(
                "tareaSupervisor", "tareaGerente", "tareaRiesgos");

        completar(proceso.getId(), "tareaGerente", "APROBADA");
        assertThat(estaActivo(proceso.getId())).isTrue();

        completar(proceso.getId(), "tareaRiesgos", "APROBADA");
        assertThat(estaActivo(proceso.getId())).isTrue();

        completar(proceso.getId(), "tareaSupervisor", "APROBADA");
        assertThat(estaActivo(proceso.getId())).isFalse();
    }

    @Test
    @DisplayName("Las ramas no activadas no crean tareas")
    void ramasNoActivadasNoCreanTareas() {
        ProcessInstance proceso = iniciar(true, false, true);

        assertThat(clavesDeTareas(proceso.getId()))
            .containsExactlyInAnyOrder("tareaSupervisor", "tareaRiesgos");
        assertThat(clavesDeTareas(proceso.getId()))
            .doesNotContain("tareaGerente");

        completar(proceso.getId(), "tareaSupervisor", "APROBADA");
        completar(proceso.getId(), "tareaRiesgos", "APROBADA");

        assertThat(estaActivo(proceso.getId())).isFalse();
    }

    @Test
    @DisplayName("Un rechazo termina la instancia y cancela la otra rama")
    void rechazoTerminaLaInstancia() {
        ProcessInstance proceso = iniciar(true, true, false);

        completar(proceso.getId(), "tareaSupervisor", "RECHAZADA");

        assertThat(estaActivo(proceso.getId())).isFalse();
        assertThat(tareas(proceso.getId())).isEmpty();
        assertThat(historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(proceso.getId())
            .activityId("registrarRechazo")
            .count()).isEqualTo(1L);
    }

    private ProcessInstance iniciar(
        boolean supervisor,
        boolean gerente,
        boolean riesgos
    ) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requiereAlguna", supervisor || gerente || riesgos);
        variables.put("requiereSupervisor", supervisor);
        variables.put("requiereGerente", gerente);
        variables.put("requiereRiesgos", riesgos);
        return runtimeService.startProcessInstanceByKey(CLAVE_PROCESO, variables);
    }

    private List<Task> tareas(String procesoId) {
        return taskService.createTaskQuery()
            .processInstanceId(procesoId)
            .list();
    }

    private List<String> clavesDeTareas(String procesoId) {
        return tareas(procesoId).stream()
            .map(Task::getTaskDefinitionKey)
            .toList();
    }

    private void completar(String procesoId, String claveTarea, String decision) {
        Task tarea = taskService.createTaskQuery()
            .processInstanceId(procesoId)
            .taskDefinitionKey(claveTarea)
            .singleResult();
        assertThat(tarea).as("tarea %s", claveTarea).isNotNull();
        taskService.complete(tarea.getId(), Map.of("decision", decision));
    }

    private boolean estaActivo(String procesoId) {
        return runtimeService.createProcessInstanceQuery()
            .processInstanceId(procesoId)
            .count() > 0;
    }
}
