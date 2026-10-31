package com.workflow.financiero;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.financiero.api.error.FiltroFirmaRiesgoExterno;
import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.aplicacion.servicio.ServicioIncidente;
import com.workflow.financiero.aplicacion.servicio.ServicioIntencionEjecucion;
import com.workflow.financiero.aplicacion.simulador.ConfiguracionFallosSimulador;
import com.workflow.financiero.infraestructura.ejecucion.TrabajadorCompensacion;
import com.workflow.financiero.infraestructura.ejecucion.TrabajadorEjecucion;
import com.workflow.financiero.infraestructura.ejecucion.TrabajadorRecuperacionEjecucion;
import com.workflow.financiero.infraestructura.ejecucion.TrabajadorRiesgoExterno;
import com.workflow.financiero.infraestructura.seguridad.FirmaRiesgoExterno;
import com.workflow.financiero.infraestructura.seguridad.SembradorUsuariosDemo;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.job.api.Job;
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
    "spring.profiles.active=pruebas",
    "ejecucion.trabajador.max-intentos=3",
    "ejecucion.trabajador.reclamacion-vencida=PT0S",
    "ejecucion.trabajador.reintento-espera=PT0S"
})
@AutoConfigureMockMvc
abstract class PruebaProcesoAprobacionBase extends PruebaIntegracionBase {

    static final String RUTA_SOLICITUDES = "/api/v1/solicitudes";
    static final String RUTA_TAREAS = "/api/v1/tareas-aprobacion";
    static final String RUTA_NOTIFICACIONES = "/api/v1/notificaciones";
    static final String RUTA_INCIDENTES = "/api/v1/incidentes";
    static final String RUTA_RIESGO = FiltroFirmaRiesgoExterno.RUTA;
    static final String PROVEEDOR_RIESGO = "proveedor-riesgo-simulado";
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
    ManagementService managementService;

    @Autowired
    TrabajadorEjecucion trabajadorEjecucion;

    @Autowired
    TrabajadorRecuperacionEjecucion trabajadorRecuperacion;

    @Autowired
    TrabajadorCompensacion trabajadorCompensacion;

    @Autowired
    TrabajadorRiesgoExterno trabajadorRiesgo;

    @Autowired
    ProveedorRiesgoSimuladoDirecto proveedorRiesgo;

    @Autowired
    ServicioIntencionEjecucion servicioIntencion;

    @Autowired
    ServicioIncidente servicioIncidente;

    @Autowired
    ConfiguracionFallosSimulador fallos;

    @Autowired
    ProveedorFinancieroSimuladoDirecto proveedorDirecto;

    @Autowired
    PuertoProceso puertoProceso;

    @BeforeEach
    void prepararBase() {
        limpiarProcesos();
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE operacion_simulada");
        jdbcTemplate.execute(
            "TRUNCATE TABLE evento_externo, evaluacion_riesgo");
        proveedorDirecto.reiniciar();
        proveedorRiesgo.reiniciar();
        sembrador.sembrar();
        activarUsuariosDemo();
    }

    @AfterEach
    void limpiarBase() {
        limpiarProcesos();
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE operacion_simulada");
        jdbcTemplate.execute(
            "TRUNCATE TABLE evento_externo, evaluacion_riesgo");
        proveedorDirecto.reiniciar();
        proveedorRiesgo.reiniciar();
        activarUsuariosDemo();
    }

    JsonNode crear(String usuario, long monto, String tipoOperacion)
        throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoOperacion", tipoOperacion);
        cuerpo.put("monto", monto);
        cuerpo.put("moneda", "PYG");
        cuerpo.put("descripcion", "Pago de prueba");
        cuerpo.put("justificacion", "Operación autorizada para pruebas");

        MvcResult resultado = mockMvc.perform(conToken(
                post(RUTA_SOLICITUDES), token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(cuerpo)))
            .andExpect(status().isCreated())
            .andReturn();

        return leer(resultado);
    }

    ResultActions reenviar(
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

    ResultActions reintentarIncidenteApi(
        String incidenteId,
        String usuario,
        String motivo
    ) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        return mockMvc.perform(conToken(
                post(RUTA_INCIDENTES + "/" + incidenteId + "/reintentar"),
                token(usuario))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(cuerpo)));
    }

    ResultActions cancelar(
        String solicitudId,
        String usuario,
        String motivo
    ) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        return mockMvc.perform(conToken(
                post(RUTA_SOLICITUDES + "/" + solicitudId + "/cancelar"),
                token(usuario))
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

    JsonNode notificaciones(String usuario) throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(
                get(RUTA_NOTIFICACIONES), token(usuario)))
            .andExpect(status().isOk())
            .andReturn();
        return leer(resultado);
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

    UUID idUsuario(String usuario) {
        return jdbcTemplate.queryForObject(
            "SELECT usua_id FROM usuario WHERE usua_usuario = ?",
            UUID.class,
            usuario
        );
    }

    void otorgarRol(String usuario, String rol) {
        jdbcTemplate.update(
            "INSERT INTO usuario_rol (usurol_usua_id, usurol_rol_codigo) "
                + "SELECT usua_id, ? FROM usuario WHERE usua_usuario = ? "
                + "ON CONFLICT DO NOTHING",
            rol,
            usuario
        );
    }

    void quitarRol(String usuario, String rol) {
        jdbcTemplate.update(
            "DELETE FROM usuario_rol WHERE usurol_usua_id = "
                + "(SELECT usua_id FROM usuario WHERE usua_usuario = ?) "
                + "AND usurol_rol_codigo = ?",
            usuario,
            rol
        );
    }

    void desactivarUsuario(String usuario) {
        jdbcTemplate.update(
            "UPDATE usuario SET usua_activo = FALSE WHERE usua_usuario = ?",
            usuario
        );
    }

    void activarUsuario(String usuario) {
        jdbcTemplate.update(
            "UPDATE usuario SET usua_activo = TRUE WHERE usua_usuario = ?",
            usuario
        );
    }

    String identificadorTareaMotor(String revisionId, String rol) {
        return jdbcTemplate.queryForObject(
            "SELECT tareapro_identificador_tarea_motor FROM tarea_aprobacion "
                + "WHERE tareapro_revisoli_id = ? AND tareapro_rol_codigo = ?",
            String.class,
            UUID.fromString(revisionId),
            rol
        );
    }

    String asignadoTarea(String tareaId) {
        return jdbcTemplate.queryForObject(
            "SELECT u.usua_usuario FROM tarea_aprobacion t "
                + "LEFT JOIN usuario u ON u.usua_id = t.tareapro_asignado_usua_id "
                + "WHERE t.tareapro_id = ?",
            String.class,
            UUID.fromString(tareaId)
        );
    }

    String asignadoMotor(String procesoId, String definicion) {
        Task tarea = tareaMotor(procesoId, definicion);
        return tarea == null ? null : tarea.getAssignee();
    }

    Map<String, Object> decisionDeTarea(String tareaId) {
        return jdbcTemplate.queryForMap(
            "SELECT deciapro_decision, deciapro_motivo, deciapro_delegado, "
                + "deciapro_actor_usua_id "
                + "FROM decision_aprobacion WHERE deciapro_tareapro_id = ?",
            UUID.fromString(tareaId)
        );
    }

    String rolRegistrado(String solicitudId, String usuario) {
        return jdbcTemplate.queryForObject(
            "SELECT regiactorol_rol_codigo FROM registro_actor_rol "
                + "WHERE regiactorol_solifina_id = ? "
                + "AND regiactorol_usua_id = "
                + "(SELECT usua_id FROM usuario WHERE usua_usuario = ?)",
            String.class,
            UUID.fromString(solicitudId),
            usuario
        );
    }

    String idTareaAprobacionDeRevision(String revisionId, String rol) {
        return jdbcTemplate.queryForObject(
            "SELECT tareapro_id::text FROM tarea_aprobacion "
                + "WHERE tareapro_revisoli_id = ? "
                + "AND tareapro_rol_codigo = ?",
            String.class,
            UUID.fromString(revisionId),
            rol);
    }

    String identificadorProcesoDeRevision(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT revisoli_identificador_proceso FROM revision_solicitud "
                + "WHERE revisoli_id = ?",
            String.class,
            UUID.fromString(revisionId));
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

    String nivelRevision(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT revisoli_nivel_riesgo FROM revision_solicitud "
                + "WHERE revisoli_id = ?",
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

    List<String> rolesTareasNegocioDeRevision(String revisionId) {
        return jdbcTemplate.queryForList(
            "SELECT tareapro_rol_codigo FROM tarea_aprobacion "
                + "WHERE tareapro_revisoli_id = ? "
                + "ORDER BY tareapro_rol_codigo",
            String.class,
            UUID.fromString(revisionId));
    }

    List<String> estadosTareasDeRevision(String revisionId) {
        return jdbcTemplate.queryForList(
            "SELECT tareapro_rol_codigo || ':' || tareapro_estado "
                + "FROM tarea_aprobacion "
                + "WHERE tareapro_revisoli_id = ? "
                + "ORDER BY tareapro_rol_codigo",
            String.class,
            UUID.fromString(revisionId));
    }

    List<String> tiposEventoDeRevision(String revisionId) {
        return jdbcTemplate.queryForList(
            "SELECT evensoli_tipo FROM evento_solicitud "
                + "WHERE evensoli_revisoli_id = ? "
                + "ORDER BY evensoli_creado_en, evensoli_id",
            String.class,
            UUID.fromString(revisionId));
    }

    long contarDecisionesDeRevision(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decision_aprobacion d "
                + "JOIN tarea_aprobacion t "
                + "ON t.tareapro_id = d.deciapro_tareapro_id "
                + "WHERE t.tareapro_revisoli_id = ?",
            Long.class,
            UUID.fromString(revisionId));
    }

    long contarEventosTipoDeRevision(String revisionId, String tipo) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evento_solicitud "
                + "WHERE evensoli_revisoli_id = ? AND evensoli_tipo = ?",
            Long.class,
            UUID.fromString(revisionId),
            tipo);
    }

    long contarNotificacionesDeRevisionTotal(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notificacion n "
                + "JOIN tarea_aprobacion t "
                + "ON t.tareapro_id = n.noti_tareapro_id "
                + "WHERE t.tareapro_revisoli_id = ?",
            Long.class,
            UUID.fromString(revisionId));
    }

    long contarNotificacionesTipoDeRevision(String revisionId, String tipo) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notificacion n "
                + "JOIN tarea_aprobacion t "
                + "ON t.tareapro_id = n.noti_tareapro_id "
                + "WHERE t.tareapro_revisoli_id = ? AND n.noti_tipo = ?",
            Long.class,
            UUID.fromString(revisionId),
            tipo);
    }

    long contarRegistrosActorRol(String solicitudId, String usuario) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM registro_actor_rol "
                + "WHERE regiactorol_solifina_id = ? "
                + "AND regiactorol_usua_id = "
                + "(SELECT usua_id FROM usuario WHERE usua_usuario = ?)",
            Long.class,
            UUID.fromString(solicitudId),
            usuario);
    }

    long contarRevisiones(String solicitudId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM revision_solicitud "
                + "WHERE revisoli_solifina_id = ?",
            Long.class,
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

    int ejecutarTrabajador() {
        return trabajadorEjecucion.ejecutarPendientes();
    }

    int ejecutarRecuperacion() {
        return trabajadorRecuperacion.reconciliarPendientes();
    }

    int ejecutarCompensacion() {
        return trabajadorCompensacion.procesarPendientes();
    }

    String estadoCompensacion(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT intecomp_estado FROM intento_compensacion "
                + "WHERE intecomp_revisoli_id = ?",
            String.class,
            UUID.fromString(revisionId));
    }

    String idCompensacion(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT intecomp_id::text FROM intento_compensacion "
                + "WHERE intecomp_revisoli_id = ?",
            String.class,
            UUID.fromString(revisionId));
    }

    long contarCompensaciones(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM intento_compensacion "
                + "WHERE intecomp_revisoli_id = ?",
            Long.class,
            UUID.fromString(revisionId));
    }

    int intentosCompensacion(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT intecomp_intentos FROM intento_compensacion "
                + "WHERE intecomp_revisoli_id = ?",
            Integer.class,
            UUID.fromString(revisionId));
    }

    boolean despachoEnCursoCompensacion(String revisionId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT intecomp_despacho_en_curso FROM intento_compensacion "
                + "WHERE intecomp_revisoli_id = ?",
            Boolean.class,
            UUID.fromString(revisionId)));
    }

    long contarIncidentes() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM incidente", Long.class);
    }

    String idIncidente(String revisionId) {
        return idIncidente(revisionId, "EJECUCION");
    }

    String idIncidente(String revisionId, String tipo) {
        return jdbcTemplate.queryForObject(
            "SELECT inci_id::text FROM incidente "
                + "WHERE inci_revisoli_id = ? AND inci_tipo = ?",
            String.class,
            UUID.fromString(revisionId),
            tipo);
    }

    String estadoIncidente(String revisionId) {
        return estadoIncidente(revisionId, "EJECUCION");
    }

    String estadoIncidente(String revisionId, String tipo) {
        return jdbcTemplate.queryForObject(
            "SELECT inci_estado FROM incidente "
                + "WHERE inci_revisoli_id = ? AND inci_tipo = ?",
            String.class,
            UUID.fromString(revisionId),
            tipo);
    }

    boolean incidenteReintentable(String revisionId) {
        return incidenteReintentable(revisionId, "EJECUCION");
    }

    boolean incidenteReintentable(String revisionId, String tipo) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT inci_reintentable FROM incidente "
                + "WHERE inci_revisoli_id = ? AND inci_tipo = ?",
            Boolean.class,
            UUID.fromString(revisionId),
            tipo));
    }

    String motivoIncidente(String revisionId) {
        return motivoIncidente(revisionId, "EJECUCION");
    }

    String motivoIncidente(String revisionId, String tipo) {
        return jdbcTemplate.queryForObject(
            "SELECT inci_motivo FROM incidente "
                + "WHERE inci_revisoli_id = ? AND inci_tipo = ?",
            String.class,
            UUID.fromString(revisionId),
            tipo);
    }

    String ultimoResultadoIncidente(String revisionId) {
        return ultimoResultadoIncidente(revisionId, "EJECUCION");
    }

    String ultimoResultadoIncidente(String revisionId, String tipo) {
        return jdbcTemplate.queryForObject(
            "SELECT inci_ultimo_resultado FROM incidente "
                + "WHERE inci_revisoli_id = ? AND inci_tipo = ?",
            String.class,
            UUID.fromString(revisionId),
            tipo);
    }

    int reintentosIncidente(String revisionId) {
        return reintentosIncidente(revisionId, "EJECUCION");
    }

    int reintentosIncidente(String revisionId, String tipo) {
        Integer valor = jdbcTemplate.queryForObject(
            "SELECT inci_reintentos FROM incidente "
                + "WHERE inci_revisoli_id = ? AND inci_tipo = ?",
            Integer.class,
            UUID.fromString(revisionId),
            tipo);
        return valor == null ? 0 : valor;
    }

    String actorIncidente(String revisionId) {
        return actorIncidente(revisionId, "EJECUCION");
    }

    String actorIncidente(String revisionId, String tipo) {
        return jdbcTemplate.queryForObject(
            "SELECT u.usua_usuario FROM incidente i "
                + "LEFT JOIN usuario u ON u.usua_id = i.inci_actor_usua_id "
                + "WHERE i.inci_revisoli_id = ? AND i.inci_tipo = ?",
            String.class,
            UUID.fromString(revisionId),
            tipo);
    }

    long contarIntentos(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM intento_ejecucion "
                + "WHERE inteejec_revisoli_id = ?",
            Long.class,
            UUID.fromString(revisionId));
    }

    String estadoOperacionSimulada(String clave) {
        return jdbcTemplate.queryForObject(
            "SELECT opersimu_estado FROM operacion_simulada "
                + "WHERE opersimu_clave = ?",
            String.class,
            clave);
    }

    long contarOperacionesSimuladas(String accion) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM operacion_simulada "
                + "WHERE opersimu_accion = ?",
            Long.class,
            accion);
    }

    String estadoIntento(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT inteejec_estado FROM intento_ejecucion "
                + "WHERE inteejec_revisoli_id = ?",
            String.class,
            UUID.fromString(revisionId));
    }

    String claveIntento(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT inteejec_clave FROM intento_ejecucion "
                + "WHERE inteejec_revisoli_id = ?",
            String.class,
            UUID.fromString(revisionId));
    }

    String huellaIntento(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT inteejec_huella FROM intento_ejecucion "
                + "WHERE inteejec_revisoli_id = ?",
            String.class,
            UUID.fromString(revisionId));
    }

    String referenciaConfirmacion(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT inteejec_confirmacion_referencia FROM intento_ejecucion "
                + "WHERE inteejec_revisoli_id = ?",
            String.class,
            UUID.fromString(revisionId));
    }

    long contarSuscripcionesEjecucion(String procesoId) {
        return runtimeService.createExecutionQuery()
            .processInstanceId(procesoId)
            .messageEventSubscriptionName(
                ServicioIntencionEjecucion.MENSAJE_EJECUCION_FINALIZADA)
            .count();
    }

    Job buscarTimer(String procesoId, String actividad) {
        return managementService.createTimerJobQuery()
            .processInstanceId(procesoId)
            .elementId(actividad)
            .singleResult();
    }

    void ejecutarTimer(String procesoId, String actividad) {
        Job timer = buscarTimer(procesoId, actividad);
        if (timer == null) {
            throw new IllegalStateException(
                "No existe el timer " + actividad + " del proceso " + procesoId);
        }
        Job ejecutable = managementService.moveTimerToExecutableJob(
            timer.getId());
        managementService.executeJob(ejecutable.getId());
    }

    int ejecutarRiesgoExterno() {
        return trabajadorRiesgo.procesarPendientes();
    }

    ResultActions notificarRiesgo(JsonNode creada, String nivel)
        throws Exception {
        return notificarRiesgo(
            creada.get("id").asText(),
            creada.get("revision").asInt(),
            nivel,
            "evt-" + UUID.randomUUID(),
            Instant.now(),
            Instant.now().toString(),
            null
        );
    }

    ResultActions notificarRiesgo(
        String solicitudId,
        int numeroRevision,
        String nivel,
        String identificadorEvento,
        Instant fechaProveedor,
        String timestamp,
        String firma
    ) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("proveedor", PROVEEDOR_RIESGO);
        cuerpo.put("identificadorEvento", identificadorEvento);
        cuerpo.put("identificadorSolicitud", solicitudId);
        cuerpo.put("numeroRevision", numeroRevision);
        cuerpo.put("nivelRiesgo", nivel);
        cuerpo.put("fechaProveedor", fechaProveedor);
        cuerpo.put("resultado", "Evaluación externa de prueba");

        String json = objectMapper.writeValueAsString(cuerpo);
        String firmaFinal = firma == null
            ? FirmaRiesgoExterno.firmar(
                SECRETO_RIESGO,
                PROVEEDOR_RIESGO,
                identificadorEvento,
                timestamp,
                json.getBytes(StandardCharsets.UTF_8))
            : firma;

        return mockMvc.perform(post(RUTA_RIESGO)
            .header(
                FiltroFirmaRiesgoExterno.CABECERA_PROVEEDOR,
                PROVEEDOR_RIESGO)
            .header(
                FiltroFirmaRiesgoExterno.CABECERA_EVENTO,
                identificadorEvento)
            .header(
                FiltroFirmaRiesgoExterno.CABECERA_TIMESTAMP,
                timestamp)
            .header(
                FiltroFirmaRiesgoExterno.CABECERA_FIRMA,
                firmaFinal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.getBytes(StandardCharsets.UTF_8)));
    }

    ResultActions notificarRiesgoCrudo(
        String json,
        String identificadorEvento,
        String timestamp,
        String firma
    ) throws Exception {
        String firmaFinal = firma == null
            ? FirmaRiesgoExterno.firmar(
                SECRETO_RIESGO,
                PROVEEDOR_RIESGO,
                identificadorEvento,
                timestamp,
                json.getBytes(StandardCharsets.UTF_8))
            : firma;
        return mockMvc.perform(post(RUTA_RIESGO)
            .header(
                FiltroFirmaRiesgoExterno.CABECERA_PROVEEDOR,
                PROVEEDOR_RIESGO)
            .header(
                FiltroFirmaRiesgoExterno.CABECERA_EVENTO,
                identificadorEvento)
            .header(
                FiltroFirmaRiesgoExterno.CABECERA_TIMESTAMP,
                timestamp)
            .header(
                FiltroFirmaRiesgoExterno.CABECERA_FIRMA,
                firmaFinal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.getBytes(StandardCharsets.UTF_8)));
    }

    String estadoEventoExterno(String identificadorEvento) {
        return jdbcTemplate.queryForObject(
            "SELECT evenexte_estado FROM evento_externo "
                + "WHERE evenexte_proveedor = ? "
                + "AND evenexte_identificador_evento = ?",
            String.class,
            PROVEEDOR_RIESGO,
            identificadorEvento);
    }

    String estadoEvaluacionRiesgo(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT evalries_estado FROM evaluacion_riesgo "
                + "WHERE evalries_revisoli_id = ? "
                + "AND evalries_origen = 'EXTERNO'",
            String.class,
            UUID.fromString(revisionId));
    }

    String nivelEvaluacionRiesgo(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT evalries_nivel FROM evaluacion_riesgo "
                + "WHERE evalries_revisoli_id = ? "
                + "AND evalries_origen = 'EXTERNO'",
            String.class,
            UUID.fromString(revisionId));
    }

    String claveEvaluacionRiesgo(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT evalries_clave FROM evaluacion_riesgo "
                + "WHERE evalries_revisoli_id = ? "
                + "AND evalries_origen = 'EXTERNO'",
            String.class,
            UUID.fromString(revisionId));
    }

    int intentosEvaluacionRiesgo(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT evalries_intentos FROM evaluacion_riesgo "
                + "WHERE evalries_revisoli_id = ? "
                + "AND evalries_origen = 'EXTERNO'",
            Integer.class,
            UUID.fromString(revisionId));
    }

    int intentosEventoExterno(String identificadorEvento) {
        return jdbcTemplate.queryForObject(
            "SELECT evenexte_intentos FROM evento_externo "
                + "WHERE evenexte_proveedor = ? "
                + "AND evenexte_identificador_evento = ?",
            Integer.class,
            PROVEEDOR_RIESGO,
            identificadorEvento);
    }

    String referenciaEvaluacionRiesgo(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT evalries_referencia_solicitud FROM evaluacion_riesgo "
                + "WHERE evalries_revisoli_id = ? "
                + "AND evalries_origen = 'EXTERNO'",
            String.class,
            UUID.fromString(revisionId));
    }

    String errorEvaluacionRiesgo(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT evalries_error FROM evaluacion_riesgo "
                + "WHERE evalries_revisoli_id = ? "
                + "AND evalries_origen = 'EXTERNO'",
            String.class,
            UUID.fromString(revisionId));
    }

    int conflictosEventoExterno(String identificadorEvento) {
        return jdbcTemplate.queryForObject(
            "SELECT evenexte_conflictos FROM evento_externo "
                + "WHERE evenexte_proveedor = ? "
                + "AND evenexte_identificador_evento = ?",
            Integer.class,
            PROVEEDOR_RIESGO,
            identificadorEvento);
    }

    long contarEventosExternos() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evento_externo", Long.class);
    }

    long contarEvaluacionesExternas(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evaluacion_riesgo "
                + "WHERE evalries_revisoli_id = ? "
                + "AND evalries_origen = 'EXTERNO'",
            Long.class,
            UUID.fromString(revisionId));
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
