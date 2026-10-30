package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.workflow.financiero.aplicacion.servicio.ServicioValidacionSolicitud;
import com.workflow.financiero.dominio.excepcion.SolicitudInvalidaException;
import com.workflow.financiero.dominio.modelo.Moneda;
import com.workflow.financiero.dominio.modelo.PerfilRiesgo;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.TipoOperacion;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.dominio.regla.validacion.ContextoValidacion;
import com.workflow.financiero.dominio.regla.validacion.ErrorCampo;
import com.workflow.financiero.dominio.regla.validacion.ResultadoValidacion;
import com.workflow.financiero.dominio.regla.validacion.SolicitudAValidar;
import com.workflow.financiero.dominio.regla.validacion.ValidadorPerfilSolicitante;
import com.workflow.financiero.infraestructura.persistencia.MonedaRepositorio;
import com.workflow.financiero.infraestructura.persistencia.PerfilRiesgoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TipoOperacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
class PruebaValidacionesTest extends PruebaIntegracionBase {

    private static final String PERFIL_INACTIVO = "PERFIL_INACTIVO";
    private static final String TIPO_INACTIVO = "TIPO_INACTIVO";
    private static final String MONEDA_NO_PYG = "USD";
    private static final String MONEDA_INACTIVA = "ARS";

    @Autowired
    ServicioValidacionSolicitud servicio;

    @Autowired
    UsuarioRepositorio usuarioRepositorio;

    @Autowired
    TipoOperacionRepositorio tipoOperacionRepositorio;

    @Autowired
    MonedaRepositorio monedaRepositorio;

    @Autowired
    PerfilRiesgoRepositorio perfilRiesgoRepositorio;

    @Autowired
    RolRepositorio rolRepositorio;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiarDatos() {
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera, usuario CASCADE");
        limpiarCatalogosDePrueba();
    }

    @AfterEach
    void limpiarDespuesDeLaPrueba() {
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera, usuario CASCADE");
        limpiarCatalogosDePrueba();
    }

    @Test
    @DisplayName("Acepta una solicitud válida y normaliza los códigos de catálogo")
    void aceptaUnaSolicitudValidaYNormalizaLosCodigos() {
        Usuario usuario = crearUsuario("NORMAL");

        ContextoValidacion contexto = servicio.validar(new SolicitudAValidar(
            usuario.getId(),
            " normal ",
            new BigDecimal("25000000"),
            " pyg ",
            "Pago de demostración",
            "Operación autorizada para pruebas"
        ));

        assertThat(contexto.solicitante().getId()).isEqualTo(usuario.getId());
        assertThat(contexto.perfilSolicitante().getCodigo()).isEqualTo("NORMAL");
        assertThat(contexto.tipoOperacionCodigo()).isEqualTo("NORMAL");
        assertThat(contexto.tipoOperacion().getCodigo()).isEqualTo("NORMAL");
        assertThat(contexto.monedaCodigo()).isEqualTo("PYG");
        assertThat(contexto.moneda().getCodigo()).isEqualTo("PYG");
        assertThat(contexto.monto()).isEqualByComparingTo("25000000");
    }

    @Test
    @DisplayName("Rechaza tipo de operación obligatorio, desconocido e inactivo")
    void rechazaTipoObligatorioDesconocidoEInactivo() {
        Usuario usuario = crearUsuario("NORMAL");
        tipoOperacionRepositorio.saveAndFlush(
            new TipoOperacion(TIPO_INACTIVO, "Tipo inactivo", false, false)
        );

        List<SolicitudAValidar> casos = List.of(
            conTipo(usuario, null),
            conTipo(usuario, " "),
            conTipo(usuario, "NO_EXISTE"),
            conTipo(usuario, TIPO_INACTIVO)
        );

        List<String> mensajesEsperados = List.of(
            "El tipo de operación es obligatorio",
            "El tipo de operación es obligatorio",
            "El tipo de operación no existe",
            "El tipo de operación no está activo"
        );

        for (int i = 0; i < casos.size(); i++) {
            List<ErrorCampo> errores = erroresDe(casos.get(i));
            assertThat(campos(errores)).containsExactly("tipoOperacion");
            assertThat(errores.getFirst().mensaje()).isEqualTo(mensajesEsperados.get(i));
        }
    }

    @Test
    @DisplayName("Rechaza moneda obligatoria, desconocida, inactiva y distinta de PYG")
    void rechazaMonedaObligatoriaDesconocidaInactivaYNoPyg() {
        Usuario usuario = crearUsuario("NORMAL");
        monedaRepositorio.saveAndFlush(
            new Moneda(MONEDA_NO_PYG, "Dólar", "US$", (short) 2, true)
        );
        monedaRepositorio.saveAndFlush(
            new Moneda(MONEDA_INACTIVA, "Peso argentino", "$", (short) 2, false)
        );

        List<SolicitudAValidar> casos = List.of(
            conMoneda(usuario, null),
            conMoneda(usuario, " "),
            conMoneda(usuario, "XXX"),
            conMoneda(usuario, MONEDA_INACTIVA),
            conMoneda(usuario, MONEDA_NO_PYG)
        );

        List<String> mensajesEsperados = List.of(
            "La moneda es obligatoria",
            "La moneda es obligatoria",
            "La moneda no existe",
            "La moneda no está activa",
            "Solo se acepta la moneda PYG"
        );

        for (int i = 0; i < casos.size(); i++) {
            List<ErrorCampo> errores = erroresDe(casos.get(i));
            assertThat(campos(errores)).containsExactly("moneda");
            assertThat(errores.getFirst().mensaje()).isEqualTo(mensajesEsperados.get(i));
        }
    }

    @Test
    @DisplayName("Rechaza montos inválidos y acepta enteros con o sin escala")
    void rechazaMontosInvalidosYAceptaEnteros() {
        Usuario usuario = crearUsuario("NORMAL");

        List<SolicitudAValidar> invalidos = List.of(
            conMonto(usuario, null),
            conMonto(usuario, "0"),
            conMonto(usuario, "-1"),
            conMonto(usuario, "100.50"),
            conMonto(usuario, "0.01"),
            conMonto(usuario, "10000000000000000000")
        );

        for (SolicitudAValidar solicitud : invalidos) {
            List<ErrorCampo> errores = erroresDe(solicitud);
            assertThat(campos(errores)).containsExactly("monto");
        }

        assertThat(servicio.validar(conMonto(usuario, "1.0")).monto())
            .isEqualByComparingTo("1.0");
        assertThat(servicio.validar(conMonto(usuario, "9999999999999999999")).monto())
            .isEqualByComparingTo("9999999999999999999");
    }

    @Test
    @DisplayName("Rechaza descripción y justificación vacías o demasiado largas")
    void rechazaDescripcionYJustificacionInvalidas() {
        Usuario usuario = crearUsuario("NORMAL");

        List<SolicitudAValidar> descripcionesInvalidas = List.of(
            conDescripcion(usuario, null),
            conDescripcion(usuario, "   "),
            conDescripcion(usuario, "a".repeat(501))
        );
        for (SolicitudAValidar solicitud : descripcionesInvalidas) {
            assertThat(campos(erroresDe(solicitud))).containsExactly("descripcion");
        }

        List<SolicitudAValidar> justificacionesInvalidas = List.of(
            conJustificacion(usuario, null),
            conJustificacion(usuario, "   "),
            conJustificacion(usuario, "b".repeat(1001))
        );
        for (SolicitudAValidar solicitud : justificacionesInvalidas) {
            assertThat(campos(erroresDe(solicitud))).containsExactly("justificacion");
        }

        assertThat(servicio.validar(
            conDescripcion(usuario, "a".repeat(500))
        ).descripcion()).hasSize(500);
        assertThat(servicio.validar(
            conJustificacion(usuario, "b".repeat(1000))
        ).justificacion()).hasSize(1000);
    }

    @Test
    @DisplayName("Rechaza un perfil de solicitante ausente o inactivo")
    void rechazaPerfilAusenteOInactivo() {
        SolicitudAValidar solicitanteInexistente = new SolicitudAValidar(
            UUID.randomUUID(),
            "NORMAL",
            new BigDecimal("25000000"),
            "PYG",
            "Pago de demostración",
            "Operación autorizada para pruebas"
        );

        List<ErrorCampo> erroresSinSolicitante = erroresDe(solicitanteInexistente);
        assertThat(campos(erroresSinSolicitante)).containsExactly("solicitante");

        PerfilRiesgo perfilInactivo = perfilRiesgoRepositorio.saveAndFlush(
            new PerfilRiesgo(PERFIL_INACTIVO, "Perfil inactivo", false)
        );
        Usuario usuarioConPerfilInactivo = crearUsuario(perfilInactivo);

        List<ErrorCampo> erroresPerfilInactivo = erroresDe(
            solicitudBase(usuarioConPerfilInactivo)
        );
        assertThat(campos(erroresPerfilInactivo)).containsExactly("perfilRiesgo");
        assertThat(erroresPerfilInactivo.getFirst().mensaje())
            .isEqualTo("El perfil de riesgo del solicitante no está activo");

        ContextoValidacion contexto = new ContextoValidacion(
            usuarioConPerfilInactivo,
            null,
            "NORMAL",
            tipoOperacionRepositorio.findById("NORMAL").orElseThrow(),
            new BigDecimal("25000000"),
            "PYG",
            monedaRepositorio.findById("PYG").orElseThrow(),
            "Pago de demostración",
            "Operación autorizada para pruebas"
        );
        ResultadoValidacion resultado = new ValidadorPerfilSolicitante().validar(contexto);
        assertThat(resultado.esValido()).isFalse();
        assertThat(resultado.errores().getFirst().campo()).isEqualTo("perfilRiesgo");
    }

    @Test
    @DisplayName("Acumula los errores de todos los campos en una sola validación")
    void acumulaErroresDeTodosLosCampos() {
        monedaRepositorio.saveAndFlush(
            new Moneda(MONEDA_NO_PYG, "Dólar", "US$", (short) 2, true)
        );
        PerfilRiesgo perfilInactivo = perfilRiesgoRepositorio.saveAndFlush(
            new PerfilRiesgo(PERFIL_INACTIVO, "Perfil inactivo", false)
        );
        Usuario usuario = crearUsuario(perfilInactivo);

        SolicitudAValidar solicitud = new SolicitudAValidar(
            usuario.getId(),
            "NO_EXISTE",
            BigDecimal.ZERO,
            MONEDA_NO_PYG,
            " ",
            "b".repeat(1001)
        );

        List<ErrorCampo> errores = erroresDe(solicitud);

        assertThat(campos(errores)).containsExactly(
            "tipoOperacion",
            "moneda",
            "monto",
            "descripcion",
            "justificacion",
            "perfilRiesgo"
        );

        SolicitudInvalidaException excepcion = assertThrows(
            SolicitudInvalidaException.class,
            () -> servicio.validar(solicitud)
        );
        assertThat(excepcion.getMessage()).contains("6 errores");
        assertThat(excepcion.getErrores()).hasSize(6);
    }

    @Test
    @DisplayName("Una entrada inválida no crea solicitud, revisión ni proceso")
    void unaEntradaInvalidaNoCreaSolicitudRevisionNiProceso() {
        Usuario usuario = crearUsuario("NORMAL");

        assertThat(erroresDe(conTipo(usuario, "NO_EXISTE")))
            .extracting(ErrorCampo::campo)
            .containsExactly("tipoOperacion");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM solicitud_financiera", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM revision_solicitud", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evento_solicitud", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM act_ru_execution", Long.class)).isZero();
    }

    @Test
    @DisplayName("El contrato de entrada no acepta perfil, estado ni riesgo del cliente")
    void elContratoDeEntradaNoAceptaPerfilEstadoNiRiesgo() {
        List<String> componentes = Arrays.stream(SolicitudAValidar.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

        assertThat(componentes).containsExactly(
            "solicitanteId",
            "tipoOperacion",
            "monto",
            "moneda",
            "descripcion",
            "justificacion"
        );
        assertThat(componentes).doesNotContain(
            "perfilRiesgo", "perfil", "estado", "nivelRiesgo", "riesgo"
        );
    }

    private Usuario crearUsuario(String perfilCodigo) {
        return crearUsuario(perfilRiesgoRepositorio.findById(perfilCodigo).orElseThrow());
    }

    private Usuario crearUsuario(PerfilRiesgo perfil) {
        Rol rol = rolRepositorio.findById("SOLICITANTE").orElseThrow();
        return usuarioRepositorio.saveAndFlush(new Usuario(
            UUID.randomUUID(),
            "usuario-" + UUID.randomUUID(),
            "hash-de-prueba",
            "Usuario de prueba",
            perfil,
            Set.of(rol)
        ));
    }

    private SolicitudAValidar solicitudBase(Usuario usuario) {
        return new SolicitudAValidar(
            usuario.getId(),
            "NORMAL",
            new BigDecimal("25000000"),
            "PYG",
            "Pago de demostración",
            "Operación autorizada para pruebas"
        );
    }

    private SolicitudAValidar conTipo(Usuario usuario, String tipo) {
        SolicitudAValidar base = solicitudBase(usuario);
        return new SolicitudAValidar(
            base.solicitanteId(), tipo, base.monto(), base.moneda(),
            base.descripcion(), base.justificacion()
        );
    }

    private SolicitudAValidar conMoneda(Usuario usuario, String moneda) {
        SolicitudAValidar base = solicitudBase(usuario);
        return new SolicitudAValidar(
            base.solicitanteId(), base.tipoOperacion(), base.monto(), moneda,
            base.descripcion(), base.justificacion()
        );
    }

    private SolicitudAValidar conMonto(Usuario usuario, String monto) {
        SolicitudAValidar base = solicitudBase(usuario);
        return new SolicitudAValidar(
            base.solicitanteId(), base.tipoOperacion(),
            monto == null ? null : new BigDecimal(monto), base.moneda(),
            base.descripcion(), base.justificacion()
        );
    }

    private SolicitudAValidar conDescripcion(Usuario usuario, String descripcion) {
        SolicitudAValidar base = solicitudBase(usuario);
        return new SolicitudAValidar(
            base.solicitanteId(), base.tipoOperacion(), base.monto(), base.moneda(),
            descripcion, base.justificacion()
        );
    }

    private SolicitudAValidar conJustificacion(Usuario usuario, String justificacion) {
        SolicitudAValidar base = solicitudBase(usuario);
        return new SolicitudAValidar(
            base.solicitanteId(), base.tipoOperacion(), base.monto(), base.moneda(),
            base.descripcion(), justificacion
        );
    }

    private List<ErrorCampo> erroresDe(SolicitudAValidar solicitud) {
        SolicitudInvalidaException excepcion = assertThrows(
            SolicitudInvalidaException.class,
            () -> servicio.validar(solicitud),
            "se esperaba una solicitud inválida"
        );
        return excepcion.getErrores();
    }

    private List<String> campos(List<ErrorCampo> errores) {
        return errores.stream().map(ErrorCampo::campo).toList();
    }

    private void limpiarCatalogosDePrueba() {
        jdbcTemplate.update(
            "DELETE FROM tipo_operacion WHERE tipooper_codigo = ?", TIPO_INACTIVO);
        jdbcTemplate.update(
            "DELETE FROM moneda WHERE mone_codigo IN (?, ?)",
            MONEDA_NO_PYG, MONEDA_INACTIVA);
        jdbcTemplate.update(
            "DELETE FROM perfil_riesgo WHERE perfries_codigo = ?", PERFIL_INACTIVO);
    }
}
