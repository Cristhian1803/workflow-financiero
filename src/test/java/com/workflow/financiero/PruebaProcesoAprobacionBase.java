package com.workflow.financiero;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.infraestructura.ejecucion.AdaptadorEjecucionControlada;
import com.workflow.financiero.infraestructura.seguridad.SembradorUsuariosDemo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base común para las pruebas de rutas y paralelismo. Crea solicitudes por HTTP
 * y consulta el estado real en PostgreSQL y Flowable.
 */
@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
@AutoConfigureMockMvc
abstract class PruebaProcesoAprobacionBase extends PruebaIntegracionBase {

    static final String RUTA_SOLICITUDES = "/api/v1/solicitudes";
    static final String RUTA_TAREAS = "/api/v1/tareas-aprobacion";
    static final String RUTA_LOGIN =
        "/api/v1/autenticacion/iniciar-sesion";
    static final String CLAVE_PROCESO = "aprobacionFinanciera";
    static final String MENSAJE_RIESGO = "RIESGO_EVALUADO";
    static final String USUARIOS_DEMO =
        "'demo', 'demo2', 'auditor', 'admin', 'supervisor', 'gerente', "
            + "'riesgos', 'gerente_general', 'jefe_riesgos', 'operaciones'";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SembradorUsuariosDemo sembrador;

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    TaskService taskService;

    @Autowired
    HistoryService historyService;

    @Autowired
    AdaptadorEjecucionControlada adaptadorEjecucion;

    @Autowired
    PuertoProceso puertoProceso;

    @BeforeEach
    void prepararBase() {
        limpiarProcesos();
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        sembrador.sembrar();
        activarUsuariosDemo();
    }

    @AfterEach
    void limpiarBase() {
        limpiarProcesos();
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        activarUsuariosDemo();
    }

    JsonNode crear(String usuario, long monto, String tipoOperacion)
        throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoOperacion", tipoOperacion);
        cuerpo.put("monto", monto);
        cuerpo.put("moneda", "PYG");
        cuerpo.put("descripcion", "Pago de prueba I-13");
        cuerpo.put("justificacion", "Operación autorizada para pruebas");

        MvcResult resultado = mockMvc.perform(conToken(
                post(RUTA_SOLICITUDES), token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(cuerpo)))
            .andExpect(status().isCreated())
            .andReturn();

        return leer(resultado);
    }

    ResultActions aprobar(
        String tareaId,
        String usuario,
        String comentario
    ) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("comentario", comentario);
        return mockMvc.perform(conToken(
                post(RUTA_TAREAS + "/" + tareaId + "/aprobar"), token(usuario))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(cuerpo)));
    }

    ResultActions rechazar(
        String tareaId,
        String usuario,
        String motivo
    ) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        return mockMvc.perform(conToken(
                post(RUTA_TAREAS + "/" + tareaId + "/rechazar"), token(usuario))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(cuerpo)));
    }

    String token(String usuario) throws Exception {
        Map<String, String> cuerpo = Map.of(
            "usuario", usuario,
            "clave", "demo");
        MvcResult resultado = mockMvc.perform(post(RUTA_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(cuerpo)))
            .andExpect(status().isOk())
            .andReturn();
        return leer(resultado).get("token").asText();
    }

    JsonNode leer(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    MockHttpServletRequestBuilder conToken(
        MockHttpServletRequestBuilder peticion,
        String token
    ) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    String identificadorProceso(String solicitudId) {
        return jdbcTemplate.queryForObject(
            "SELECT r.revisoli_identificador_proceso FROM revision_solicitud r "
                + "JOIN solicitud_financiera s "
                + "ON s.solifina_revisoli_actual_id = r.revisoli_id "
                + "WHERE s.solifina_id = ?",
            String.class,
            UUID.fromString(solicitudId));
    }

    String idTareaAprobacion(String solicitudId, String rol) {
        return jdbcTemplate.queryForObject(
            "SELECT t.tareapro_id::text FROM tarea_aprobacion t "
                + "JOIN revision_solicitud r "
                + "ON r.revisoli_id = t.tareapro_revisoli_id "
                + "WHERE r.revisoli_solifina_id = ? "
                + "AND t.tareapro_rol_codigo = ?",
            String.class,
            UUID.fromString(solicitudId),
            rol);
    }

    String estadoTarea(String tareaId) {
        return jdbcTemplate.queryForObject(
            "SELECT tareapro_estado FROM tarea_aprobacion WHERE tareapro_id = ?",
            String.class,
            UUID.fromString(tareaId));
    }

    String estadoRevision(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT revisoli_estado FROM revision_solicitud WHERE revisoli_id = ?",
            String.class,
            UUID.fromString(revisionId));
    }

    List<String> rolesTareasMotor(String procesoId) {
        return taskService.createTaskQuery()
            .processInstanceId(procesoId)
            .list()
            .stream()
            .map(Task::getTaskDefinitionKey)
            .toList();
    }

    List<String> rolesTareasNegocio(String solicitudId) {
        return jdbcTemplate.queryForList(
            "SELECT t.tareapro_rol_codigo FROM tarea_aprobacion t "
                + "JOIN revision_solicitud r "
                + "ON r.revisoli_id = t.tareapro_revisoli_id "
                + "WHERE r.revisoli_solifina_id = ? "
                + "ORDER BY t.tareapro_rol_codigo",
            String.class,
            UUID.fromString(solicitudId));
    }

    Task tareaMotor(String procesoId, String clave) {
        return taskService.createTaskQuery()
            .processInstanceId(procesoId)
            .taskDefinitionKey(clave)
            .singleResult();
    }

    List<String> tiposEvento(String solicitudId) {
        return jdbcTemplate.queryForList(
            "SELECT evensoli_tipo FROM evento_solicitud "
                + "WHERE evensoli_solifina_id = ? "
                + "ORDER BY evensoli_creado_en, evensoli_id",
            String.class,
            UUID.fromString(solicitudId));
    }

    long contarDecisiones(String solicitudId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decision_aprobacion d "
                + "JOIN tarea_aprobacion t "
                + "ON t.tareapro_id = d.deciapro_tareapro_id "
                + "JOIN revision_solicitud r "
                + "ON r.revisoli_id = t.tareapro_revisoli_id "
                + "WHERE r.revisoli_solifina_id = ?",
            Long.class,
            UUID.fromString(solicitudId));
    }

    boolean procesoActivo(String procesoId) {
        return runtimeService.createProcessInstanceQuery()
            .processInstanceId(procesoId)
            .count() > 0;
    }

    boolean procesoFinalizado(String procesoId) {
        return historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(procesoId)
            .singleResult()
            .getEndTime() != null;
    }

    long contarSuscripcionesRiesgo(String procesoId) {
        return runtimeService.createExecutionQuery()
            .processInstanceId(procesoId)
            .messageEventSubscriptionName(MENSAJE_RIESGO)
            .count();
    }

    List<AdaptadorEjecucionControlada.ObservacionEjecucion> observaciones(
        String revisionId
    ) {
        return adaptadorEjecucion.observaciones(UUID.fromString(revisionId));
    }

    void enviarRiesgoExterno(
        String procesoId,
        boolean resuelto,
        String nivel
    ) {
        puertoProceso.enviarMensaje(
            MENSAJE_RIESGO,
            procesoId,
            Map.of(
                "riesgoExternoResuelto", resuelto,
                "nivelRiesgoExterno", nivel
            )
        );
    }

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

    void activarUsuariosDemo() {
        jdbcTemplate.update(
            "UPDATE usuario SET usua_activo = TRUE "
                + "WHERE usua_usuario IN (" + USUARIOS_DEMO + ")");
    }
}
