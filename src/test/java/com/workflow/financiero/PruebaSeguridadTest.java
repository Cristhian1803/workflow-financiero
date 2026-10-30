package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.financiero.api.contrato.autenticacion.IniciarSesionPeticion;
import com.workflow.financiero.api.error.Correlacion;
import com.workflow.financiero.infraestructura.seguridad.SembradorUsuariosDemo;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
@AutoConfigureMockMvc
class PruebaSeguridadTest extends PruebaIntegracionBase {

    private static final String RUTA_LOGIN = "/api/v1/autenticacion/iniciar-sesion";
    private static final String RUTA_YO = "/api/v1/autenticacion/yo";
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
    JwtEncoder jwtEncoder;

    @Autowired
    JwtDecoder jwtDecoder;

    @BeforeEach
    void prepararUsuariosDemo() {
        sembrador.sembrar();
        activarUsuariosDemo();
    }

    @AfterEach
    void restaurarUsuariosDemo() {
        activarUsuariosDemo();
    }

    @Test
    @DisplayName("El login correcto emite un token con roles y expiración")
    void iniciaSesionYEmiteTokenConRoles() throws Exception {
        MvcResult resultado = mockMvc.perform(post(RUTA_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(peticion("demo", "demo")))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.tipo").value("Bearer"))
            .andExpect(jsonPath("$.expiraEn").isNotEmpty())
            .andExpect(content().string(not(containsString("$2a$"))))
            .andExpect(content().string(not(containsString("claveHash"))))
            .andReturn();

        JsonNode cuerpo = objectMapper.readTree(
            resultado.getResponse().getContentAsString());
        String token = cuerpo.get("token").asText();
        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("demo");
        assertThat(jwt.getClaimAsStringList("roles"))
            .containsExactly("SOLICITANTE");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(EMISOR_JWT);
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
        assertThat(Instant.parse(cuerpo.get("expiraEn").asText()))
            .isEqualTo(jwt.getExpiresAt());
    }

    @Test
    @DisplayName("Una contraseña incorrecta produce un 401 genérico")
    void rechazaClaveIncorrecta() throws Exception {
        mockMvc.perform(post(RUTA_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(peticion("demo", "incorrecta")))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
            .andExpect(jsonPath("$.detail").value("Usuario o clave inválidos"))
            .andExpect(jsonPath("$.identificadorCorrelacion").isNotEmpty());
    }

    @Test
    @DisplayName("Un usuario inexistente produce el mismo 401")
    void rechazaUsuarioInexistente() throws Exception {
        mockMvc.perform(post(RUTA_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(peticion("no-existe", "demo")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
            .andExpect(jsonPath("$.detail").value("Usuario o clave inválidos"));
    }

    @Test
    @DisplayName("Un usuario inactivo no puede iniciar sesión")
    void rechazaUsuarioInactivo() throws Exception {
        jdbcTemplate.update(
            "UPDATE usuario SET usua_activo = FALSE WHERE usua_usuario = ?",
            "auditor");

        mockMvc.perform(post(RUTA_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(peticion("auditor", "demo")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    @DisplayName("Sin token se responde 401 con correlación")
    void rechazaTokenAusente() throws Exception {
        mockMvc.perform(get(RUTA_YO))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().exists(Correlacion.CABECERA))
            .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
            .andExpect(jsonPath("$.identificadorCorrelacion").isNotEmpty());
    }

    @Test
    @DisplayName("Un token alterado es rechazado")
    void rechazaTokenAlterado() throws Exception {
        String token = iniciarSesion("demo", "demo");
        char ultimo = token.charAt(token.length() - 1);
        char reemplazo = ultimo == 'a' ? 'b' : 'a';
        String alterado = token.substring(0, token.length() - 1) + reemplazo;

        mockMvc.perform(conToken(get(RUTA_YO), alterado))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("Un token vencido es rechazado")
    void rechazaTokenVencido() throws Exception {
        Instant ahora = Instant.now();
        String token = firmar(
            ahora.minus(10, ChronoUnit.MINUTES),
            ahora.minus(5, ChronoUnit.MINUTES),
            EMISOR_JWT
        );

        mockMvc.perform(conToken(get(RUTA_YO), token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("Un token con emisor incorrecto es rechazado")
    void rechazaTokenConEmisorIncorrecto() throws Exception {
        Instant ahora = Instant.now();
        String token = firmar(
            ahora,
            ahora.plus(10, ChronoUnit.MINUTES),
            "otro-emisor"
        );

        mockMvc.perform(conToken(get(RUTA_YO), token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("El usuario actual devuelve identidad, roles y perfil sin datos internos")
    void devuelveUsuarioActual() throws Exception {
        String token = iniciarSesion("demo", "demo");

        mockMvc.perform(conToken(get(RUTA_YO), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.usuario").value("demo"))
            .andExpect(jsonPath("$.nombre").value("Usuario Demostración"))
            .andExpect(jsonPath("$.roles[0]").value("SOLICITANTE"))
            .andExpect(jsonPath("$.perfilRiesgo").value("NORMAL"))
            .andExpect(jsonPath("$.claveHash").doesNotExist())
            .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    @DisplayName("Se conserva la correlación enviada por el cliente")
    void conservaCorrelacionDelCliente() throws Exception {
        mockMvc.perform(get(RUTA_YO)
                .header(Correlacion.CABECERA, "corr-prueba-123"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(Correlacion.CABECERA, "corr-prueba-123"))
            .andExpect(jsonPath("$.identificadorCorrelacion")
                .value("corr-prueba-123"));
    }

    @Test
    @DisplayName("Se genera una correlación cuando el cliente no la envía")
    void generaCorrelacionCuandoFalta() throws Exception {
        MvcResult resultado = mockMvc.perform(get(RUTA_YO))
            .andExpect(status().isUnauthorized())
            .andReturn();

        String correlacion = resultado.getResponse()
            .getHeader(Correlacion.CABECERA);
        JsonNode cuerpo = objectMapper.readTree(
            resultado.getResponse().getContentAsString());

        assertThat(correlacion).matches("[0-9a-f-]{36}");
        assertThat(cuerpo.get("identificadorCorrelacion").asText())
            .isEqualTo(correlacion);
    }

    @Test
    @DisplayName("La salud es pública")
    void saludEsPublica() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("La documentación OpenAPI es pública en pruebas")
    void documentacionEsPublicaEnPruebas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("La información de Actuator exige administrador")
    void informacionExigeAdministrador() throws Exception {
        String tokenDemo = iniciarSesion("demo", "demo");

        mockMvc.perform(conToken(get("/actuator/info"), tokenDemo))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
            .andExpect(jsonPath("$.identificadorCorrelacion").isNotEmpty());

        String tokenAdmin = iniciarSesion("admin", "demo");
        mockMvc.perform(conToken(get("/actuator/info"), tokenAdmin))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un cuerpo de login inválido devuelve errores de campo")
    void rechazaCuerpoInvalido() throws Exception {
        mockMvc.perform(post(RUTA_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
            .andExpect(jsonPath("$.errores.length()").value(2))
            .andExpect(jsonPath("$.errores[0].campo").value("clave"))
            .andExpect(jsonPath("$.errores[1].campo").value("usuario"))
            .andExpect(jsonPath("$.identificadorCorrelacion").isNotEmpty());
    }

    @Test
    @DisplayName("Un JSON malformado devuelve un error uniforme")
    void rechazaJsonMalformado() throws Exception {
        mockMvc.perform(post(RUTA_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"))
            .andExpect(jsonPath("$.identificadorCorrelacion").isNotEmpty());
    }

    @Test
    @DisplayName("El sembrador es idempotente y guarda claves BCrypt")
    void sembradorEsIdempotenteYUsaBcrypt() {
        Long antes = contarUsuariosDemo();
        String hashAntes = hashDemo();

        sembrador.sembrar();

        Long despues = contarUsuariosDemo();
        String hashDespues = hashDemo();

        assertThat(antes).isEqualTo(4L);
        assertThat(despues).isEqualTo(4L);
        assertThat(hashDespues).isEqualTo(hashAntes);
        assertThat(hashDespues).startsWith("$2");
    }

    @Test
    @DisplayName("El token no aparece en los logs")
    void noRegistraTokenEnLogs() throws Exception {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        raiz.addAppender(appender);

        String token;
        try {
            token = iniciarSesion("demo", "demo");
        } finally {
            raiz.detachAppender(appender);
        }

        assertThat(appender.list)
            .noneMatch(evento -> evento.getFormattedMessage().contains(token));
    }

    private String iniciarSesion(String usuario, String clave) throws Exception {
        MvcResult resultado = mockMvc.perform(post(RUTA_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(peticion(usuario, clave)))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(resultado.getResponse().getContentAsString())
            .get("token")
            .asText();
    }

    private String peticion(String usuario, String clave) throws Exception {
        return objectMapper.writeValueAsString(
            new IniciarSesionPeticion(usuario, clave));
    }

    private String firmar(Instant emitido, Instant expira, String emisor) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(emisor)
            .subject("demo")
            .issuedAt(emitido)
            .expiresAt(expira)
            .claim("roles", List.of("SOLICITANTE"))
            .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(),
            claims
        )).getTokenValue();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder conToken(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
        String token
    ) {
        return peticion.header(
            HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void activarUsuariosDemo() {
        jdbcTemplate.update(
            "UPDATE usuario SET usua_activo = TRUE "
                + "WHERE usua_usuario IN (" + USUARIOS_DEMO + ")");
    }

    private Long contarUsuariosDemo() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM usuario WHERE usua_usuario IN ("
                + USUARIOS_DEMO + ")",
            Long.class);
    }

    private String hashDemo() {
        return jdbcTemplate.queryForObject(
            "SELECT usua_clave_hash FROM usuario WHERE usua_usuario = 'demo'",
            String.class);
    }
}
