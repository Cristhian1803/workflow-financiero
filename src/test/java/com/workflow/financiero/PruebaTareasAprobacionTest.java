package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.financiero.aplicacion.configuracion.PropiedadesAprobacion;
import com.workflow.financiero.aplicacion.servicio.SincronizadorTareasAprobacion;
import com.workflow.financiero.infraestructura.seguridad.SembradorUsuariosDemo;
import java.time.Instant;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
@AutoConfigureMockMvc
class PruebaTareasAprobacionTest extends PruebaIntegracionBase {

    private static final String RUTA_SOLICITUDES = "/api/v1/solicitudes";
    private static final String RUTA_TAREAS = "/api/v1/tareas-aprobacion";
    private static final String RUTA_LOGIN =
        "/api/v1/autenticacion/iniciar-sesion";
    private static final String CLAVE_PROCESO = "aprobacionFinanciera";
    private static final String USUARIOS_DEMO =
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
    SincronizadorTareasAprobacion sincronizador;

    @Autowired
    PropiedadesAprobacion propiedadesAprobacion;

    @BeforeEach
    void preparar() {
        limpiarProcesos();
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        sembrador.sembrar();
        activarUsuariosDemo();
        quitarSupervisorDemo();
    }

    @AfterEach
    void limpiar() {
        quitarSupervisorDemo();
        limpiarProcesos();
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        activarUsuariosDemo();
    }

    @Test
    @DisplayName("Cinco millones no crea tareas de negocio")
    void cincoMillonesNoCreaTareas() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();

        assertThat(creada.get("estado").asText()).isEqualTo("APROBADA");
        assertThat(contarTareas(solicitudId)).isZero();
        assertThat(bandeja("supervisor").size()).isZero();
    }

    @Test
    @DisplayName("Veinticinco millones sincroniza la tarea de Supervisor")
    void veinticincoMillonesSincronizaSupervisor() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String procesoId = identificadorProceso(solicitudId);

        Task tareaMotor = taskService.createTaskQuery()
            .processInstanceId(procesoId)
            .taskDefinitionKey("tareaSupervisor")
            .singleResult();
        assertThat(tareaMotor).isNotNull();

        Map<String, Object> tarea = tareaAprobacion(solicitudId, "SUPERVISOR");
        assertThat(tarea.get("tareapro_identificador_tarea_motor"))
            .isEqualTo(tareaMotor.getId());
        assertThat(tarea.get("tareapro_estado")).isEqualTo("PENDIENTE");
        assertThat(tarea.get("tareapro_fecha_vencimiento")).isNotNull();
        assertThat(tarea.get("tareapro_asignado_usua_id")).isNull();

        JsonNode bandeja = bandeja("supervisor");
        assertThat(bandeja.size()).isEqualTo(1);

        JsonNode tareaApi = bandeja.get(0);
        assertThat(tareaApi.get("id").asText())
            .isEqualTo(idTareaAprobacion(solicitudId, "SUPERVISOR"));
        assertThat(tareaApi.get("idSolicitud").asText()).isEqualTo(solicitudId);
        assertThat(tareaApi.get("idRevision").asText())
            .isEqualTo(creada.get("idRevision").asText());
        assertThat(tareaApi.get("revision").asInt()).isEqualTo(1);
        assertThat(tareaApi.get("rol").asText()).isEqualTo("SUPERVISOR");
        assertThat(tareaApi.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(tareaApi.hasNonNull("asignadoA")).isFalse();
        assertThat(tareaApi.get("solicitante").asText()).isEqualTo("demo");
        assertThat(tareaApi.get("tipoOperacion").asText()).isEqualTo("NORMAL");
        assertThat(tareaApi.get("monto").asLong()).isEqualTo(25_000_000L);
        assertThat(tareaApi.get("moneda").asText()).isEqualTo("PYG");
        assertThat(tareaApi.get("nivelRiesgo").asText()).isEqualTo("BAJO");
        assertThat(tareaApi.get("descripcion").asText())
            .isEqualTo("Pago de prueba I-12a");
        assertThat(tareaApi.get("justificacion").asText())
            .isEqualTo("Operación autorizada para pruebas");
        assertThat(tareaApi.has("identificadorTareaMotor")).isFalse();

        Instant fechaCreacion = Instant.parse(
            tareaApi.get("fechaCreacion").asText());
        Instant fechaNotificacion = Instant.parse(
            tareaApi.get("fechaNotificacion").asText());
        Instant fechaVencimiento = Instant.parse(
            tareaApi.get("fechaVencimiento").asText());
        Instant vencimientoEsperado = fechaCreacion
            .plus(propiedadesAprobacion.reasignacion());

        assertThat(fechaNotificacion).isEqualTo(
            fechaCreacion.plus(propiedadesAprobacion.notificacion()));
        assertThat(fechaVencimiento)
            .isBetween(
                vencimientoEsperado.minusSeconds(1),
                vencimientoEsperado.plusSeconds(1));
    }

    @Test
    @DisplayName("Cien millones sincroniza Supervisor y Gerente")
    void cienMillonesSincronizaDosTareas() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();

        assertThat(rolesTareas(solicitudId))
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE");
        assertThat(bandeja("supervisor").size()).isEqualTo(1);
        assertThat(bandeja("gerente").size()).isEqualTo(2);
        assertThat(bandeja("riesgos").size()).isZero();
    }

    @Test
    @DisplayName("Doscientos cincuenta millones sincroniza tres tareas y delegaciones")
    void doscientosCincuentaMillonesSincronizaTresTareas() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();

        assertThat(rolesTareas(solicitudId))
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE", "RIESGOS");
        assertThat(bandeja("supervisor").size()).isEqualTo(1);
        assertThat(bandeja("gerente").size()).isEqualTo(2);
        assertThat(bandeja("riesgos").size()).isEqualTo(1);

        JsonNode gerenteGeneral = bandeja("gerente_general");
        assertThat(gerenteGeneral.size()).isEqualTo(1);
        assertThat(gerenteGeneral.get(0).get("rol").asText())
            .isEqualTo("GERENTE");

        JsonNode jefeRiesgos = bandeja("jefe_riesgos");
        assertThat(jefeRiesgos.size()).isEqualTo(1);
        assertThat(jefeRiesgos.get(0).get("rol").asText())
            .isEqualTo("RIESGOS");
    }

    @Test
    @DisplayName("Un tipo sensible de diez millones solo abre Riesgos")
    void tipoSensibleSoloAbreRiesgos() throws Exception {
        JsonNode creada = crear("demo", 10_000_000, "SENSIBLE");
        String solicitudId = creada.get("id").asText();

        assertThat(rolesTareas(solicitudId)).containsExactly("RIESGOS");
        assertThat(bandeja("supervisor").size()).isZero();
        assertThat(bandeja("riesgos").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("La bandeja rechaza solicitantes y administradores")
    void bandejaRechazaSolicitantesYAdministradores() throws Exception {
        mockMvc.perform(conToken(get(RUTA_TAREAS), token("demo")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        mockMvc.perform(conToken(get(RUTA_TAREAS), token("admin")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        mockMvc.perform(get(RUTA_TAREAS))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("El creador con rol de aprobador no ve su propia tarea")
    void creadorConRolAprobadorNoVeSuTarea() throws Exception {
        otorgarSupervisorDemo();
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();

        assertThat(contarTareas(solicitudId)).isEqualTo(1);
        assertThat(bandeja("demo").size()).isZero();

        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId), token("demo")))
            .andExpect(status().isOk());
        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId + "/proceso"),
                token("demo")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una tarea asignada solo la ve el asignado")
    void tareaAsignadaSoloLaVeElAsignado() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        asignarTarea(idTareaAprobacion(solicitudId, "SUPERVISOR"), "supervisor");

        assertThat(bandeja("gerente").size()).isZero();

        JsonNode bandejaSupervisor = bandeja("supervisor");
        assertThat(bandejaSupervisor.size()).isEqualTo(1);
        assertThat(bandejaSupervisor.get(0).get("asignadoA").asText())
            .isEqualTo("supervisor");

        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId), token("gerente")))
            .andExpect(status().isForbidden());
        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId + "/proceso"),
                token("gerente")))
            .andExpect(status().isForbidden());
        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId), token("supervisor")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sincronizar dos veces no duplica ni cambia la tarea")
    void sincronizacionEsIdempotente() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        Map<String, Object> antes = tareaAprobacion(solicitudId, "SUPERVISOR");

        sincronizador.sincronizar(
            UUID.fromString(solicitudId),
            UUID.fromString(revisionId),
            procesoId);

        Map<String, Object> despues = tareaAprobacion(solicitudId, "SUPERVISOR");
        assertThat(contarTareas(solicitudId)).isEqualTo(1);
        assertThat(despues.get("tareapro_id"))
            .isEqualTo(antes.get("tareapro_id"));
        assertThat(despues.get("tareapro_identificador_tarea_motor"))
            .isEqualTo(antes.get("tareapro_identificador_tarea_motor"));
        assertThat(despues.get("tareapro_fecha_vencimiento"))
            .isEqualTo(antes.get("tareapro_fecha_vencimiento"));
        assertThat(despues.get("tareapro_actualizado_en"))
            .isEqualTo(antes.get("tareapro_actualizado_en"));
    }

    @Test
    @DisplayName("El aprobador relacionado ve detalle y proceso")
    void aprobadorRelacionadoVeDetalleYProceso() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String tareaId = idTareaAprobacion(solicitudId, "SUPERVISOR");

        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId), token("supervisor")))
            .andExpect(status().isOk());
        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId + "/proceso"),
                token("supervisor")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tareas[0].id").value(tareaId))
            .andExpect(jsonPath("$.tareas[0].fechaVencimiento").isNotEmpty());

        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId), token("riesgos")))
            .andExpect(status().isForbidden());
        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId + "/proceso"),
                token("riesgos")))
            .andExpect(status().isForbidden());
        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId), token("demo2")))
            .andExpect(status().isNotFound());
        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId + "/proceso"),
                token("auditor")))
            .andExpect(status().isForbidden());
        mockMvc.perform(conToken(
                get(RUTA_SOLICITUDES + "/" + solicitudId + "/proceso"),
                token("admin")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("La bandeja se ordena por vencimiento y creación")
    void bandejaSeOrdenaPorVencimiento() throws Exception {
        crear("demo", 25_000_000, "NORMAL");
        crear("demo", 100_000_000, "NORMAL");

        JsonNode bandeja = bandeja("gerente");
        assertThat(bandeja.size()).isEqualTo(3);

        Instant anterior = Instant.MIN;
        for (JsonNode tarea : bandeja) {
            Instant fecha = Instant.parse(
                tarea.get("fechaVencimiento").asText());
            assertThat(fecha).isAfterOrEqualTo(anterior);
            anterior = fecha;
        }
    }

    private JsonNode crear(String usuario, long monto, String tipoOperacion)
        throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoOperacion", tipoOperacion);
        cuerpo.put("monto", monto);
        cuerpo.put("moneda", "PYG");
        cuerpo.put("descripcion", "Pago de prueba I-12a");
        cuerpo.put("justificacion", "Operación autorizada para pruebas");

        MvcResult resultado = mockMvc.perform(conToken(
                post(RUTA_SOLICITUDES), token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(cuerpo)))
            .andExpect(status().isCreated())
            .andReturn();

        return leer(resultado);
    }

    private JsonNode bandeja(String usuario) throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(
                get(RUTA_TAREAS), token(usuario)))
            .andExpect(status().isOk())
            .andReturn();
        return leer(resultado);
    }

    private String token(String usuario) throws Exception {
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

    private JsonNode leer(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder conToken(
        MockHttpServletRequestBuilder peticion,
        String token
    ) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String identificadorProceso(String solicitudId) {
        return jdbcTemplate.queryForObject(
            "SELECT r.revisoli_identificador_proceso FROM revision_solicitud r "
                + "JOIN solicitud_financiera s "
                + "ON s.solifina_revisoli_actual_id = r.revisoli_id "
                + "WHERE s.solifina_id = ?",
            String.class,
            UUID.fromString(solicitudId));
    }

    private Long contarTareas(String solicitudId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tarea_aprobacion t "
                + "JOIN revision_solicitud r "
                + "ON r.revisoli_id = t.tareapro_revisoli_id "
                + "WHERE r.revisoli_solifina_id = ?",
            Long.class,
            UUID.fromString(solicitudId));
    }

    private List<String> rolesTareas(String solicitudId) {
        return jdbcTemplate.queryForList(
            "SELECT t.tareapro_rol_codigo FROM tarea_aprobacion t "
                + "JOIN revision_solicitud r "
                + "ON r.revisoli_id = t.tareapro_revisoli_id "
                + "WHERE r.revisoli_solifina_id = ? "
                + "ORDER BY t.tareapro_rol_codigo",
            String.class,
            UUID.fromString(solicitudId));
    }

    private Map<String, Object> tareaAprobacion(String solicitudId, String rol) {
        return jdbcTemplate.queryForMap(
            "SELECT t.tareapro_id, t.tareapro_identificador_tarea_motor, "
                + "t.tareapro_estado, t.tareapro_fecha_vencimiento, "
                + "t.tareapro_creado_en, t.tareapro_actualizado_en, "
                + "t.tareapro_asignado_usua_id "
                + "FROM tarea_aprobacion t "
                + "JOIN revision_solicitud r "
                + "ON r.revisoli_id = t.tareapro_revisoli_id "
                + "WHERE r.revisoli_solifina_id = ? "
                + "AND t.tareapro_rol_codigo = ?",
            UUID.fromString(solicitudId),
            rol);
    }

    private String idTareaAprobacion(String solicitudId, String rol) {
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

    private void asignarTarea(String tareaId, String usuario) {
        jdbcTemplate.update(
            "UPDATE tarea_aprobacion SET tareapro_asignado_usua_id = "
                + "(SELECT usua_id FROM usuario WHERE usua_usuario = ?) "
                + "WHERE tareapro_id = ?",
            usuario,
            UUID.fromString(tareaId));
    }

    private void otorgarSupervisorDemo() {
        jdbcTemplate.update(
            "INSERT INTO usuario_rol (usurol_usua_id, usurol_rol_codigo) "
                + "SELECT usua_id, 'SUPERVISOR' FROM usuario "
                + "WHERE usua_usuario = 'demo' ON CONFLICT DO NOTHING");
    }

    private void quitarSupervisorDemo() {
        jdbcTemplate.update(
            "DELETE FROM usuario_rol WHERE usurol_usua_id = "
                + "(SELECT usua_id FROM usuario WHERE usua_usuario = 'demo') "
                + "AND usurol_rol_codigo = 'SUPERVISOR'");
    }

    private void activarUsuariosDemo() {
        jdbcTemplate.update(
            "UPDATE usuario SET usua_activo = TRUE "
                + "WHERE usua_usuario IN (" + USUARIOS_DEMO + ")");
    }

    private void limpiarProcesos() {
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
}
