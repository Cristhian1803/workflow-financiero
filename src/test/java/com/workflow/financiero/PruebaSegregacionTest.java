package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.financiero.aplicacion.servicio.ServicioInicioProceso;
import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.modelo.Moneda;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TipoOperacion;
import com.workflow.financiero.infraestructura.persistencia.MonedaRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TipoOperacionRepositorio;
import com.workflow.financiero.infraestructura.seguridad.SembradorUsuariosDemo;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
@AutoConfigureMockMvc
class PruebaSegregacionTest extends PruebaIntegracionBase {

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
    HistoryService historyService;

    @Autowired
    SolicitudFinancieraRepositorio solicitudRepositorio;

    @Autowired
    RevisionSolicitudRepositorio revisionRepositorio;

    @Autowired
    TipoOperacionRepositorio tipoOperacionRepositorio;

    @Autowired
    MonedaRepositorio monedaRepositorio;

    @Autowired
    ServicioTransicionRevision servicioTransicion;

    @Autowired
    ServicioInicioProceso servicioInicioProceso;

    @BeforeEach
    void preparar() {
        limpiarProcesos();
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        sembrador.sembrar();
        activarUsuariosDemo();
        quitarRol("supervisor", "GERENTE");
    }

    @AfterEach
    void limpiar() {
        quitarRol("supervisor", "GERENTE");
        limpiarProcesos();
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        activarUsuariosDemo();
    }

    @Test
    @DisplayName("Un actor con dos roles no aprueba dos roles funcionales distintos")
    void mismoActorNoApruebaDosRoles() throws Exception {
        otorgarRol("supervisor", "GERENTE");
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String tareaSupervisor = idTarea(solicitudId, "SUPERVISOR");
        String tareaGerente = idTarea(solicitudId, "GERENTE");

        aprobar(tareaSupervisor, "supervisor", null)
            .andExpect(status().isOk());

        aprobar(tareaGerente, "supervisor", null)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("SEGREGACION_FUNCIONES"));

        assertThat(estadoTarea(tareaGerente)).isEqualTo("PENDIENTE");
        assertThat(contarRegistros(solicitudId, "supervisor")).isEqualTo(1);
        assertThat(rolRegistrado(solicitudId, "supervisor"))
            .isEqualTo("SUPERVISOR");
    }

    @Test
    @DisplayName("Un delegado no aprueba dos roles funcionales distintos")
    void delegadoNoApruebaDosRoles() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String tareaSupervisor = idTarea(solicitudId, "SUPERVISOR");
        String tareaGerente = idTarea(solicitudId, "GERENTE");

        JsonNode primera = leer(aprobar(
            tareaSupervisor, "gerente", null)
            .andExpect(status().isOk())
            .andReturn());

        assertThat(primera.get("delegado").asBoolean()).isTrue();
        assertThat(rolRegistrado(solicitudId, "gerente"))
            .isEqualTo("SUPERVISOR");

        aprobar(tareaGerente, "gerente", null)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("SEGREGACION_FUNCIONES"));

        assertThat(estadoTarea(tareaGerente)).isEqualTo("PENDIENTE");
        assertThat(contarRegistros(solicitudId, "gerente")).isEqualTo(1);
    }

    @Test
    @DisplayName("El mismo actor repite el mismo rol en otra revisión")
    void mismoActorRepiteElMismoRolEnOtraRevision() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String primeraTarea = idTarea(solicitudId, "SUPERVISOR");

        aprobar(primeraTarea, "supervisor", null)
            .andExpect(status().isOk());
        assertThat(rolRegistrado(solicitudId, "supervisor"))
            .isEqualTo("SUPERVISOR");

        String revision2 = crearRevisionAdicional(solicitudId, 25_000_000);
        String segundaTarea = idTareaDeRevision(revision2, "SUPERVISOR");

        aprobar(segundaTarea, "supervisor", null)
            .andExpect(status().isOk());

        assertThat(estadoRevision(revision2)).isEqualTo("APROBADA");
        assertThat(contarRegistros(solicitudId, "supervisor")).isEqualTo(1);
    }

    @Test
    @DisplayName("Un rechazo no bloquea una aprobación posterior")
    void rechazoNoBloqueaAprobacionPosterior() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String tareaSupervisor = idTarea(solicitudId, "SUPERVISOR");

        rechazar(tareaSupervisor, "supervisor", "No corresponde")
            .andExpect(status().isOk());
        assertThat(contarRegistros(solicitudId, "supervisor")).isZero();

        String revision2 = crearRevisionAdicional(solicitudId, 25_000_000);
        String segundaTarea = idTareaDeRevision(revision2, "SUPERVISOR");

        aprobar(segundaTarea, "supervisor", null)
            .andExpect(status().isOk());

        assertThat(rolRegistrado(solicitudId, "supervisor"))
            .isEqualTo("SUPERVISOR");
    }

    @Test
    @DisplayName("Un actor no aprueba otro rol en una revisión posterior")
    void actorNoApruebaOtroRolEnRevisionPosterior() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String tareaSupervisor = idTarea(solicitudId, "SUPERVISOR");

        aprobar(tareaSupervisor, "supervisor", null)
            .andExpect(status().isOk());

        otorgarRol("supervisor", "GERENTE");
        String revision2 = crearRevisionAdicional(solicitudId, 100_000_000);
        String tareaGerente = idTareaDeRevision(revision2, "GERENTE");

        aprobar(tareaGerente, "supervisor", null)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("SEGREGACION_FUNCIONES"));

        assertThat(estadoTarea(tareaGerente)).isEqualTo("PENDIENTE");
        assertThat(contarRegistros(solicitudId, "supervisor")).isEqualTo(1);
    }

    @Test
    @DisplayName("El mismo actor repite el mismo rol tras un reenvío real")
    void mismoActorRepiteElMismoRolTrasReenvio() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();

        aprobar(idTarea(solicitudId, "SUPERVISOR"), "supervisor", null)
            .andExpect(status().isOk());
        rechazar(idTarea(solicitudId, "GERENTE"), "gerente", "No corresponde")
            .andExpect(status().isOk());

        JsonNode respuesta = leer(reenviar(
                solicitudId, "demo", 100_000_000, "NORMAL")
            .andExpect(status().isCreated())
            .andReturn());
        String revision2 = respuesta.get("idRevision").asText();

        aprobar(idTareaDeRevision(revision2, "SUPERVISOR"), "supervisor", null)
            .andExpect(status().isOk());

        assertThat(rolRegistrado(solicitudId, "supervisor"))
            .isEqualTo("SUPERVISOR");
        assertThat(contarRegistros(solicitudId, "supervisor")).isEqualTo(1);
    }

    @Test
    @DisplayName("El mismo actor no aprueba otro rol tras un reenvío real")
    void mismoActorNoApruebaOtroRolTrasReenvio() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();

        aprobar(idTarea(solicitudId, "SUPERVISOR"), "supervisor", null)
            .andExpect(status().isOk());
        rechazar(idTarea(solicitudId, "GERENTE"), "gerente", "No corresponde")
            .andExpect(status().isOk());

        JsonNode respuesta = leer(reenviar(
                solicitudId, "demo", 100_000_000, "NORMAL")
            .andExpect(status().isCreated())
            .andReturn());
        String revision2 = respuesta.get("idRevision").asText();

        otorgarRol("supervisor", "GERENTE");

        aprobar(idTareaDeRevision(revision2, "GERENTE"), "supervisor", null)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("SEGREGACION_FUNCIONES"));

        assertThat(estadoTarea(idTareaDeRevision(revision2, "GERENTE")))
            .isEqualTo("PENDIENTE");
        assertThat(rolRegistrado(solicitudId, "supervisor"))
            .isEqualTo("SUPERVISOR");
        assertThat(contarRegistros(solicitudId, "supervisor")).isEqualTo(1);
    }

    @Test
    @DisplayName("Un rechazo previo no bloquea aprobar otro rol tras un reenvío")
    void rechazoPrevioNoBloqueaOtroRolTrasReenvio() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();

        rechazar(idTarea(solicitudId, "SUPERVISOR"), "supervisor", "No corresponde")
            .andExpect(status().isOk());

        JsonNode respuesta = leer(reenviar(
                solicitudId, "demo", 100_000_000, "NORMAL")
            .andExpect(status().isCreated())
            .andReturn());
        String revision2 = respuesta.get("idRevision").asText();

        otorgarRol("supervisor", "GERENTE");

        aprobar(idTareaDeRevision(revision2, "GERENTE"), "supervisor", null)
            .andExpect(status().isOk());

        assertThat(rolRegistrado(solicitudId, "supervisor"))
            .isEqualTo("GERENTE");
        assertThat(contarRegistros(solicitudId, "supervisor")).isEqualTo(1);
    }

    private JsonNode crear(String usuario, long monto, String tipoOperacion)
        throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoOperacion", tipoOperacion);
        cuerpo.put("monto", monto);
        cuerpo.put("moneda", "PYG");
        cuerpo.put("descripcion", "Pago de prueba I-12b");
        cuerpo.put("justificacion", "Operación autorizada para pruebas");

        MvcResult resultado = mockMvc.perform(conToken(
                post(RUTA_SOLICITUDES), token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(cuerpo)))
            .andExpect(status().isCreated())
            .andReturn();

        return leer(resultado);
    }

    private ResultActions aprobar(
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

    private ResultActions rechazar(
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

    private ResultActions reenviar(
        String solicitudId,
        String usuario,
        long monto,
        String tipoOperacion
    ) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoOperacion", tipoOperacion);
        cuerpo.put("monto", monto);
        cuerpo.put("moneda", "PYG");
        cuerpo.put("descripcion", "Pago corregido");
        cuerpo.put("justificacion", "Se ajusta el monto a la documentación");

        return mockMvc.perform(conToken(
                post(RUTA_SOLICITUDES + "/" + solicitudId + "/reenvios"),
                token(usuario))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(cuerpo)));
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

    private String crearRevisionAdicional(String solicitudId, long monto) {
        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarDetallePorId(UUID.fromString(solicitudId))
            .orElseThrow();
        RevisionSolicitud actual = solicitud.getRevisionActual();
        TipoOperacion tipo = tipoOperacionRepositorio.findById("NORMAL")
            .orElseThrow();
        Moneda moneda = monedaRepositorio.findById("PYG").orElseThrow();

        RevisionSolicitud revision = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                solicitud,
                actual.getNumero() + 1,
                tipo,
                BigDecimal.valueOf(monto),
                moneda,
                "Pago de prueba I-12b",
                "Operación autorizada para pruebas"
            )
        );
        solicitud.establecerRevisionActual(revision);
        solicitudRepositorio.saveAndFlush(solicitud);

        servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-revision", "Validación")
        );

        RevisionSolicitud enValidacion = revisionRepositorio
            .buscarDetallePorId(revision.getId())
            .orElseThrow();
        enValidacion.registrarNivelRiesgo(NivelRiesgo.BAJO);
        revisionRepositorio.saveAndFlush(enValidacion);

        servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.EVALUANDO_RIESGO,
            new ContextoTransicion(null, null, "corr-revision", "Riesgo")
        );
        servicioInicioProceso.iniciar(
            solicitud.getId(),
            revision.getId(),
            "corr-revision"
        );

        return revision.getId().toString();
    }

    private String idTarea(String solicitudId, String rol) {
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

    private String idTareaDeRevision(String revisionId, String rol) {
        return jdbcTemplate.queryForObject(
            "SELECT tareapro_id::text FROM tarea_aprobacion "
                + "WHERE tareapro_revisoli_id = ? "
                + "AND tareapro_rol_codigo = ?",
            String.class,
            UUID.fromString(revisionId),
            rol);
    }

    private String estadoTarea(String tareaId) {
        return jdbcTemplate.queryForObject(
            "SELECT tareapro_estado FROM tarea_aprobacion WHERE tareapro_id = ?",
            String.class,
            UUID.fromString(tareaId));
    }

    private String estadoRevision(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT revisoli_estado FROM revision_solicitud WHERE revisoli_id = ?",
            String.class,
            UUID.fromString(revisionId));
    }

    private String rolRegistrado(String solicitudId, String usuario) {
        return jdbcTemplate.queryForObject(
            "SELECT regiactorol_rol_codigo FROM registro_actor_rol "
                + "WHERE regiactorol_solifina_id = ? "
                + "AND regiactorol_usua_id = "
                + "(SELECT usua_id FROM usuario WHERE usua_usuario = ?)",
            String.class,
            UUID.fromString(solicitudId),
            usuario);
    }

    private Long contarRegistros(String solicitudId, String usuario) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM registro_actor_rol "
                + "WHERE regiactorol_solifina_id = ? "
                + "AND regiactorol_usua_id = "
                + "(SELECT usua_id FROM usuario WHERE usua_usuario = ?)",
            Long.class,
            UUID.fromString(solicitudId),
            usuario);
    }

    private void otorgarRol(String usuario, String rol) {
        jdbcTemplate.update(
            "INSERT INTO usuario_rol (usurol_usua_id, usurol_rol_codigo) "
                + "SELECT usua_id, ? FROM usuario WHERE usua_usuario = ? "
                + "ON CONFLICT DO NOTHING",
            rol,
            usuario);
    }

    private void quitarRol(String usuario, String rol) {
        jdbcTemplate.update(
            "DELETE FROM usuario_rol WHERE usurol_usua_id = "
                + "(SELECT usua_id FROM usuario WHERE usua_usuario = ?) "
                + "AND usurol_rol_codigo = ?",
            usuario,
            rol);
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
