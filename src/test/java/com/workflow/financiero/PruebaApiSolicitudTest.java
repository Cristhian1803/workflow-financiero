package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.financiero.api.error.Correlacion;
import com.workflow.financiero.aplicacion.servicio.ServicioAutenticacion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.infraestructura.seguridad.SembradorUsuariosDemo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class PruebaApiSolicitudTest extends PruebaIntegracionBase {

    private static final String RUTA = "/api/v1/solicitudes";
    private static final String USUARIOS_DEMO =
        "'demo', 'demo2', 'auditor', 'admin'";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SembradorUsuariosDemo sembrador;

    @Autowired
    ServicioAutenticacion servicioAutenticacion;

    @BeforeEach
    void preparar() {
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        sembrador.sembrar();
        activarUsuariosDemo();
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        activarUsuariosDemo();
    }

    @Test
    @DisplayName("Crear 5 millones deja la solicitud en EVALUANDO_RIESGO sin aprobaciones")
    void creaCincoMillonesSinAprobaciones() throws Exception {
        MvcResult resultado = crearResultado("demo", peticion(5_000_000, "NORMAL"));
        JsonNode cuerpo = leer(resultado);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        assertThat(resultado.getResponse().getHeader(HttpHeaders.LOCATION))
            .isEqualTo(RUTA + "/" + cuerpo.get("id").asText());
        assertThat(cuerpo.get("idRevision").asText()).isNotBlank();
        assertThat(cuerpo.get("revision").asInt()).isEqualTo(1);
        assertThat(cuerpo.get("solicitante").asText()).isEqualTo("demo");
        assertThat(cuerpo.get("tipoOperacion").asText()).isEqualTo("NORMAL");
        assertThat(cuerpo.get("monto").asLong()).isEqualTo(5_000_000L);
        assertThat(cuerpo.get("moneda").asText()).isEqualTo("PYG");
        assertThat(cuerpo.get("estado").asText()).isEqualTo("EVALUANDO_RIESGO");
        assertThat(cuerpo.get("nivelRiesgo").asText()).isEqualTo("BAJO");
        assertThat(cuerpo.get("aprobacionesRequeridas")).isEmpty();
        assertThat(cuerpo.get("requiereEvaluacionExterna").asBoolean()).isFalse();
        assertThat(cuerpo.get("fechaCreacion").asText()).isNotBlank();
        assertThat(cuerpo.get("fechaActualizacion").asText()).isNotBlank();

        String solicitudId = cuerpo.get("id").asText();
        assertThat(estadoRevision(solicitudId)).isEqualTo(EstadoRevision.EVALUANDO_RIESGO);
        assertThat(nivelRiesgo(solicitudId)).isEqualTo(NivelRiesgo.BAJO);
        assertThat(identificadorProceso(solicitudId)).isNull();
        assertThat(contarEventos(solicitudId)).isEqualTo(5);
        assertThat(contarTareasAprobacion(solicitudId)).isZero();
    }

    @Test
    @DisplayName("El plan por monto y riesgo coincide en los casos de la API")
    void calculaPlanSegunMontoYRiesgo() throws Exception {
        JsonNode veinticinco = crear("demo", peticion(25_000_000, "NORMAL"));
        assertThat(roles(veinticinco))
            .containsExactly("SUPERVISOR");
        assertThat(veinticinco.get("nivelRiesgo").asText()).isEqualTo("BAJO");

        JsonNode cien = crear("demo", peticion(100_000_000, "NORMAL"));
        assertThat(roles(cien))
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE");
        assertThat(cien.get("nivelRiesgo").asText()).isEqualTo("MEDIO");

        JsonNode doscientosCincuenta = crear(
            "demo", peticion(250_000_000, "NORMAL"));
        assertThat(roles(doscientosCincuenta))
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE", "RIESGOS");
        assertThat(doscientosCincuenta.get("nivelRiesgo").asText())
            .isEqualTo("ALTO");
        assertThat(doscientosCincuenta.get("requiereEvaluacionExterna").asBoolean())
            .isTrue();
        assertThat(contarEventos(doscientosCincuenta.get("id").asText()))
            .isEqualTo(6);
        assertThat(tiposEvento(doscientosCincuenta.get("id").asText()))
            .contains("EVALUACION_EXTERNA_PENDIENTE");
    }

    @Test
    @DisplayName("Un tipo sensible fuerza riesgo alto y la aprobación de Riesgos")
    void calculaPlanParaTipoSensible() throws Exception {
        JsonNode diez = crear("demo", peticion(10_000_000, "SENSIBLE"));
        assertThat(diez.get("nivelRiesgo").asText()).isEqualTo("ALTO");
        assertThat(roles(diez)).containsExactly("RIESGOS");
        assertThat(diez.get("requiereEvaluacionExterna").asBoolean()).isFalse();

        JsonNode cien = crear("demo", peticion(100_000_000, "SENSIBLE"));
        assertThat(cien.get("nivelRiesgo").asText()).isEqualTo("ALTO");
        assertThat(roles(cien))
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE", "RIESGOS");
    }

    @Test
    @DisplayName("La evaluación externa usa el límite de 200 millones")
    void evaluacionExternaUsaElLimite() throws Exception {
        JsonNode limite = crear("demo", peticion(200_000_000, "NORMAL"));
        assertThat(limite.get("requiereEvaluacionExterna").asBoolean()).isFalse();

        JsonNode superior = crear("demo", peticion(200_000_001, "NORMAL"));
        assertThat(superior.get("requiereEvaluacionExterna").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("La creación no inicia Flowable ni abre tareas")
    void noIniciaProcesoNiAbreTareas() throws Exception {
        Long tareasAntes = contarTabla("act_ru_task");
        Long ejecucionesAntes = contarTabla("act_ru_execution");

        JsonNode cuerpo = crear("demo", peticion(25_000_000, "NORMAL"));

        assertThat(contarTabla("act_ru_task")).isEqualTo(tareasAntes);
        assertThat(contarTabla("act_ru_execution")).isEqualTo(ejecucionesAntes);
        assertThat(identificadorProceso(cuerpo.get("id").asText())).isNull();
        assertThat(contarTareasAprobacion(cuerpo.get("id").asText())).isZero();
    }

    @Test
    @DisplayName("Los datos inválidos responden 400 y no persisten filas")
    void rechazaDatosInvalidosSinPersistir() throws Exception {
        List<Map<String, Object>> invalidas = new ArrayList<>();
        invalidas.add(cuerpo(0, "NORMAL", "PYG", "Descripción", "Justificación"));
        invalidas.add(cuerpo(-1, "NORMAL", "PYG", "Descripción", "Justificación"));
        invalidas.add(cuerpo(100.5, "NORMAL", "PYG", "Descripción", "Justificación"));
        invalidas.add(cuerpo(5_000_000, "NORMAL", "USD", "Descripción", "Justificación"));
        invalidas.add(cuerpo(5_000_000, "NO_EXISTE", "PYG", "Descripción", "Justificación"));
        invalidas.add(cuerpo(5_000_000, "NORMAL", "PYG", "", "Justificación"));
        invalidas.add(cuerpo(5_000_000, "NORMAL", "PYG", "Descripción", ""));
        invalidas.add(Map.of());

        for (Map<String, Object> invalida : invalidas) {
            mockMvc.perform(conToken(post(RUTA), token("demo"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalida)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.identificadorCorrelacion").isNotEmpty());

            assertThat(contarSolicitudes()).isZero();
            assertThat(contarEventosTotales()).isZero();
        }
    }

    @Test
    @DisplayName("Un tipo de operación inactivo no se puede usar")
    void rechazaTipoInactivoSinPersistir() throws Exception {
        jdbcTemplate.update(
            "UPDATE tipo_operacion SET tipooper_activo = FALSE "
                + "WHERE tipooper_codigo = 'SENSIBLE'");
        try {
            mockMvc.perform(conToken(post(RUTA), token("demo"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        peticion(5_000_000, "SENSIBLE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("tipoOperacion"));

            assertThat(contarSolicitudes()).isZero();
        } finally {
            jdbcTemplate.update(
                "UPDATE tipo_operacion SET tipooper_activo = TRUE "
                    + "WHERE tipooper_codigo = 'SENSIBLE'");
        }
    }

    @Test
    @DisplayName("El dueño, el administrador y el auditor acceden según su rol")
    void respetaVisibilidadPorDuenioRol() throws Exception {
        JsonNode creada = crear("demo", peticion(25_000_000, "NORMAL"));
        String id = creada.get("id").asText();

        mockMvc.perform(conToken(get(RUTA + "/" + id), token("demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solicitante").value("demo"));

        mockMvc.perform(conToken(get(RUTA + "/" + id), token("demo2")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        mockMvc.perform(conToken(get(RUTA + "/" + id), token("admin")))
            .andExpect(status().isOk());

        mockMvc.perform(conToken(get(RUTA + "/" + id), token("auditor")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("El listado se limita al dueño y el administrador ve todo")
    void listaSegunVisibilidad() throws Exception {
        crear("demo", peticion(5_000_000, "NORMAL"));
        crear("demo", peticion(25_000_000, "NORMAL"));
        crear("demo2", peticion(100_000_000, "NORMAL"));

        mockMvc.perform(conToken(get(RUTA), token("demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(2))
            .andExpect(jsonPath("$.contenido.length()").value(2))
            .andExpect(jsonPath("$.contenido[0].solicitante").value("demo"));

        mockMvc.perform(conToken(get(RUTA), token("demo2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(1))
            .andExpect(jsonPath("$.contenido[0].solicitante").value("demo2"));

        mockMvc.perform(conToken(get(RUTA), token("admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(3));

        mockMvc.perform(conToken(get(RUTA), token("auditor")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("La paginación y el filtro por estado se validan")
    void paginaYFiltraPorEstado() throws Exception {
        crear("demo", peticion(5_000_000, "NORMAL"));
        crear("demo", peticion(25_000_000, "NORMAL"));
        crear("demo", peticion(100_000_000, "NORMAL"));

        mockMvc.perform(conToken(get(RUTA)
                .param("pagina", "0")
                .param("tamano", "2"), token("demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagina").value(0))
            .andExpect(jsonPath("$.tamano").value(2))
            .andExpect(jsonPath("$.totalElementos").value(3))
            .andExpect(jsonPath("$.totalPaginas").value(2))
            .andExpect(jsonPath("$.contenido.length()").value(2));

        mockMvc.perform(conToken(get(RUTA)
                .param("pagina", "1")
                .param("tamano", "2"), token("demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido.length()").value(1));

        mockMvc.perform(conToken(get(RUTA)
                .param("estado", "EVALUANDO_RIESGO"), token("demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(3));

        mockMvc.perform(conToken(get(RUTA)
                .param("estado", "APROBADA"), token("demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(0));

        mockMvc.perform(conToken(get(RUTA)
                .param("pagina", "-1"), token("demo")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("pagina"));

        mockMvc.perform(conToken(get(RUTA)
                .param("tamano", "101"), token("demo")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("tamano"));

        mockMvc.perform(conToken(get(RUTA)
                .param("estado", "NO_EXISTE"), token("demo")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("estado"));
    }

    @Test
    @DisplayName("El historial es visible para dueño, auditor y administrador")
    void historialSegunVisibilidad() throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(post(RUTA), token("demo"))
                .header(Correlacion.CABECERA, "corr-api-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    peticion(25_000_000, "NORMAL"))))
            .andExpect(status().isCreated())
            .andReturn();

        String id = leer(resultado).get("id").asText();

        MvcResult historial = mockMvc.perform(conToken(
                get(RUTA + "/" + id + "/historial"), token("demo")))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsString("$2"))))
            .andReturn();

        JsonNode eventos = leer(historial);
        List<String> tipos = new ArrayList<>();
        eventos.forEach(evento -> tipos.add(evento.get("tipo").asText()));
        assertThat(tipos).contains(
            "SOLICITUD_CREADA",
            "CAMBIO_ESTADO",
            "RIESGO_CLASIFICADO",
            "PLAN_APROBACION_CALCULADO"
        );

        JsonNode creada = null;
        for (JsonNode evento : eventos) {
            if ("SOLICITUD_CREADA".equals(evento.get("tipo").asText())) {
                creada = evento;
                break;
            }
        }
        assertThat(creada).isNotNull();
        assertThat(creada.get("actor").asText()).isEqualTo("demo");
        assertThat(creada.get("rol").asText()).isEqualTo("SOLICITANTE");
        assertThat(creada.get("identificadorCorrelacion").asText())
            .isEqualTo("corr-api-123");

        mockMvc.perform(conToken(
                get(RUTA + "/" + id + "/historial"), token("auditor")))
            .andExpect(status().isOk());
        mockMvc.perform(conToken(
                get(RUTA + "/" + id + "/historial"), token("admin")))
            .andExpect(status().isOk());
        mockMvc.perform(conToken(
                get(RUTA + "/" + id + "/historial"), token("demo2")))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El proceso responde iniciado false y sin tareas")
    void procesoNoIniciado() throws Exception {
        JsonNode creada = crear("demo", peticion(25_000_000, "NORMAL"));
        String id = creada.get("id").asText();

        mockMvc.perform(conToken(get(RUTA + "/" + id + "/proceso"), token("demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idSolicitud").value(id))
            .andExpect(jsonPath("$.revision").value(1))
            .andExpect(jsonPath("$.identificadorProceso").doesNotExist())
            .andExpect(jsonPath("$.iniciado").value(false))
            .andExpect(jsonPath("$.finalizado").value(false))
            .andExpect(jsonPath("$.estadoRevision").value("EVALUANDO_RIESGO"))
            .andExpect(jsonPath("$.tareas").isEmpty());

        mockMvc.perform(conToken(get(RUTA + "/" + id + "/proceso"), token("admin")))
            .andExpect(status().isOk());
        mockMvc.perform(conToken(get(RUTA + "/" + id + "/proceso"), token("demo2")))
            .andExpect(status().isNotFound());
        mockMvc.perform(conToken(get(RUTA + "/" + id + "/proceso"), token("auditor")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin token se responde 401 y un rol no solicitante recibe 403")
    void exigeAutenticacionYRol() throws Exception {
        mockMvc.perform(post(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    peticion(5_000_000, "NORMAL"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
            .andExpect(header().exists(Correlacion.CABECERA));

        mockMvc.perform(get(RUTA))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        mockMvc.perform(conToken(post(RUTA), token("auditor"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    peticion(5_000_000, "NORMAL"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("Un usuario desactivado no puede usar un token ya emitido")
    void rechazaUsuarioDesactivado() throws Exception {
        String token = token("demo");
        jdbcTemplate.update(
            "UPDATE usuario SET usua_activo = FALSE WHERE usua_usuario = 'demo'");
        try {
            mockMvc.perform(conToken(get(RUTA), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        } finally {
            jdbcTemplate.update(
                "UPDATE usuario SET usua_activo = TRUE WHERE usua_usuario = 'demo'");
        }
    }

    @Test
    @DisplayName("Los campos extra del cuerpo no cambian la identidad ni el estado")
    void ignoraCamposNoPermitidos() throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoOperacion", "NORMAL");
        cuerpo.put("monto", 250_000_000);
        cuerpo.put("moneda", "PYG");
        cuerpo.put("descripcion", "Pago de demostración");
        cuerpo.put("justificacion", "Operación autorizada para pruebas");
        cuerpo.put("solicitante", "admin");
        cuerpo.put("estado", "APROBADA");
        cuerpo.put("nivelRiesgo", "BAJO");
        cuerpo.put("roles", List.of("ADMINISTRADOR"));

        JsonNode respuesta = crear("demo", cuerpo);
        assertThat(respuesta.get("solicitante").asText()).isEqualTo("demo");
        assertThat(respuesta.get("estado").asText()).isEqualTo("EVALUANDO_RIESGO");
        assertThat(respuesta.get("nivelRiesgo").asText()).isEqualTo("ALTO");
    }

    @Test
    @DisplayName("La respuesta no expone hashes ni tokens")
    void noExponeDatosSensibles() throws Exception {
        MvcResult resultado = crearResultado("demo", peticion(25_000_000, "NORMAL"));
        String cuerpo = resultado.getResponse().getContentAsString();

        assertThat(cuerpo)
            .doesNotContain("$2")
            .doesNotContain("claveHash")
            .doesNotContain("token");
    }

    @Test
    @DisplayName("OpenAPI publica bearerAuth y las rutas de solicitudes")
    void publicaOpenApiConSeguridad() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type")
                .value("http"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                .value("bearer"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat")
                .value("JWT"))
            .andExpect(jsonPath("$.paths['/api/v1/solicitudes'].post").exists())
            .andExpect(jsonPath("$.paths['/api/v1/solicitudes'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/solicitudes/{id}'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/solicitudes/{id}/historial'].get")
                .exists())
            .andExpect(jsonPath("$.paths['/api/v1/solicitudes/{id}/proceso'].get")
                .exists());
    }

    private JsonNode crear(String usuario, Map<String, Object> cuerpo) throws Exception {
        return leer(crearResultado(usuario, cuerpo));
    }

    private MvcResult crearResultado(
        String usuario,
        Map<String, Object> cuerpo
    ) throws Exception {
        return mockMvc.perform(conToken(post(RUTA), token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private Map<String, Object> peticion(long monto, String tipoOperacion) {
        return cuerpo(monto, tipoOperacion, "PYG",
            "Pago de demostración", "Operación autorizada para pruebas");
    }

    private Map<String, Object> cuerpo(
        Object monto,
        String tipoOperacion,
        String moneda,
        String descripcion,
        String justificacion
    ) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoOperacion", tipoOperacion);
        cuerpo.put("monto", monto);
        cuerpo.put("moneda", moneda);
        cuerpo.put("descripcion", descripcion);
        cuerpo.put("justificacion", justificacion);
        return cuerpo;
    }

    private JsonNode leer(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String token(String usuario) {
        return servicioAutenticacion.iniciarSesion(usuario, "demo").token();
    }

    private MockHttpServletRequestBuilder conToken(
        MockHttpServletRequestBuilder peticion,
        String token
    ) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private List<String> roles(JsonNode cuerpo) {
        List<String> roles = new ArrayList<>();
        cuerpo.get("aprobacionesRequeridas").forEach(rol -> roles.add(rol.asText()));
        return roles;
    }

    private Long contarSolicitudes() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM solicitud_financiera", Long.class);
    }

    private Long contarEventosTotales() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evento_solicitud", Long.class);
    }

    private Long contarEventos(String solicitudId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evento_solicitud WHERE evensoli_solifina_id = ?",
            Long.class,
            UUID.fromString(solicitudId));
    }

    private List<String> tiposEvento(String solicitudId) {
        return jdbcTemplate.queryForList(
            "SELECT evensoli_tipo FROM evento_solicitud "
                + "WHERE evensoli_solifina_id = ?",
            String.class,
            UUID.fromString(solicitudId));
    }

    private EstadoRevision estadoRevision(String solicitudId) {
        String estado = jdbcTemplate.queryForObject(
            "SELECT r.revisoli_estado FROM revision_solicitud r "
                + "JOIN solicitud_financiera s "
                + "ON s.solifina_revisoli_actual_id = r.revisoli_id "
                + "WHERE s.solifina_id = ?",
            String.class,
            UUID.fromString(solicitudId));
        return EstadoRevision.valueOf(estado);
    }

    private NivelRiesgo nivelRiesgo(String solicitudId) {
        String nivel = jdbcTemplate.queryForObject(
            "SELECT r.revisoli_nivel_riesgo FROM revision_solicitud r "
                + "JOIN solicitud_financiera s "
                + "ON s.solifina_revisoli_actual_id = r.revisoli_id "
                + "WHERE s.solifina_id = ?",
            String.class,
            UUID.fromString(solicitudId));
        return NivelRiesgo.valueOf(nivel);
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

    private Long contarTareasAprobacion(String solicitudId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tarea_aprobacion t "
                + "JOIN revision_solicitud r "
                + "ON r.revisoli_id = t.tareapro_revisoli_id "
                + "WHERE r.revisoli_solifina_id = ?",
            Long.class,
            UUID.fromString(solicitudId));
    }

    private Long contarTabla(String tabla) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tabla, Long.class);
    }

    private void activarUsuariosDemo() {
        jdbcTemplate.update(
            "UPDATE usuario SET usua_activo = TRUE "
                + "WHERE usua_usuario IN (" + USUARIOS_DEMO + ")");
    }
}
