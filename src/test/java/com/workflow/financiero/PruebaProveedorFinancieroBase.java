package com.workflow.financiero;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.financiero.api.contrato.proveedor.OperacionProveedorFinancieroPeticion;
import com.workflow.financiero.aplicacion.simulador.ConfiguracionFallosSimulador;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base común de las pruebas del proveedor financiero simulado.
 */
@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
@AutoConfigureMockMvc
abstract class PruebaProveedorFinancieroBase extends PruebaIntegracionBase {

    static final String RUTA = "/api/v1/integraciones/proveedor-financiero";
    static final String CABECERA = "X-Clave-Integracion";
    static final String SECRETO = UUID.randomUUID().toString();
    static final UUID SOLICITUD_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000001");
    static final UUID REVISION_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000002");
    static final BigDecimal MONTO = BigDecimal.valueOf(25_000_000);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ConfiguracionFallosSimulador fallos;

    @DynamicPropertySource
    static void propiedadesProveedor(DynamicPropertyRegistry registro) {
        registro.add("integracion.proveedor-financiero.secreto", () -> SECRETO);
        registro.add(
            "integracion.proveedor-financiero.url-base",
            () -> "http://localhost:8080" + RUTA
        );
    }

    OperacionFinanciera operacion(AccionFinanciera accion) {
        return operacion(accion, MONTO);
    }

    OperacionFinanciera operacion(AccionFinanciera accion, BigDecimal monto) {
        return OperacionFinanciera.crear(
            SOLICITUD_ID,
            REVISION_ID,
            accion,
            monto,
            "PYG",
            "NORMAL"
        );
    }

    OperacionProveedorFinancieroPeticion peticion(OperacionFinanciera operacion) {
        return new OperacionProveedorFinancieroPeticion(
            operacion.solicitudId(),
            operacion.revisionId(),
            operacion.accion(),
            operacion.clave(),
            operacion.huella(),
            operacion.monto(),
            operacion.moneda(),
            operacion.tipoOperacion()
        );
    }

    ResultActions reservar(OperacionFinanciera operacion) throws Exception {
        return postConSecreto("/reservas", peticion(operacion));
    }

    ResultActions confirmar(OperacionFinanciera operacion) throws Exception {
        return postConSecreto("/confirmaciones", peticion(operacion));
    }

    ResultActions liberar(OperacionFinanciera operacion) throws Exception {
        return postConSecreto("/liberaciones", peticion(operacion));
    }

    ResultActions consultar(String clave) throws Exception {
        return mockMvc.perform(get(RUTA + "/operaciones/{clave}", clave)
            .header(CABECERA, SECRETO));
    }

    ResultActions postConSecreto(String ruta, Object cuerpo) throws Exception {
        return mockMvc.perform(post(RUTA + ruta)
            .header(CABECERA, SECRETO)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(cuerpo)));
    }

    JsonNode leer(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    long contar(AccionFinanciera accion) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM operacion_simulada "
                + "WHERE opersimu_accion = ?",
            Long.class,
            accion.name()
        );
    }

    String estado(String clave) {
        return jdbcTemplate.queryForObject(
            "SELECT opersimu_estado FROM operacion_simulada "
                + "WHERE opersimu_clave = ?",
            String.class,
            clave
        );
    }

    String referencia(String clave) {
        return jdbcTemplate.queryForObject(
            "SELECT opersimu_referencia FROM operacion_simulada "
                + "WHERE opersimu_clave = ?",
            String.class,
            clave
        );
    }

    BigDecimal monto(String clave) {
        return jdbcTemplate.queryForObject(
            "SELECT opersimu_monto FROM operacion_simulada "
                + "WHERE opersimu_clave = ?",
            BigDecimal.class,
            clave
        );
    }

    void limpiarSimulador() {
        jdbcTemplate.execute("TRUNCATE TABLE operacion_simulada");
        fallos.limpiar();
    }
}
