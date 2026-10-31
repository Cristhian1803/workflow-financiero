package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workflow.financiero.aplicacion.servicio.CalculadoraPlanAprobacion;
import com.workflow.financiero.aplicacion.servicio.RegistroActorRolServicio;
import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.aplicacion.servicio.VerificadorAutorizacionEjecucion;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.excepcion.PoliticaAprobacionInvalidaException;
import com.workflow.financiero.dominio.excepcion.RolAprobadorInvalidoException;
import com.workflow.financiero.dominio.excepcion.TransicionTareaInvalidaException;
import com.workflow.financiero.dominio.modelo.DecisionAprobacion;
import com.workflow.financiero.dominio.modelo.Moneda;
import com.workflow.financiero.dominio.modelo.PerfilRiesgo;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.dominio.modelo.TipoDecision;
import com.workflow.financiero.dominio.modelo.TipoOperacion;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.dominio.regla.aprobacion.ContextoAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PlanAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PoliticaAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PoliticaAprobacionPorMonto;
import com.workflow.financiero.dominio.regla.aprobacion.PoliticaAprobacionPorRiesgo;
import com.workflow.financiero.dominio.regla.aprobacion.ResultadoAutorizacion;
import com.workflow.financiero.infraestructura.persistencia.DecisionAprobacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.MonedaRepositorio;
import com.workflow.financiero.infraestructura.persistencia.PerfilRiesgoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TipoOperacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
class PruebaPoliticasAprobacionTest extends PruebaIntegracionBase {

    private static final String ROL_TEMPORAL = "APROBADOR_ESPECIAL";
    private static final String ROL_INACTIVO = "ROL_INACTIVO_APROBACION";

    @Autowired
    CalculadoraPlanAprobacion calculadora;

    @Autowired
    VerificadorAutorizacionEjecucion verificador;

    @Autowired
    RegistroActorRolServicio registroActorRol;

    @Autowired
    ServicioTransicionRevision servicioTransicion;

    @Autowired
    UsuarioRepositorio usuarioRepositorio;

    @Autowired
    SolicitudFinancieraRepositorio solicitudRepositorio;

    @Autowired
    RevisionSolicitudRepositorio revisionRepositorio;

    @Autowired
    TareaAprobacionRepositorio tareaRepositorio;

    @Autowired
    DecisionAprobacionRepositorio decisionRepositorio;

    @Autowired
    MonedaRepositorio monedaRepositorio;

    @Autowired
    TipoOperacionRepositorio tipoOperacionRepositorio;

    @Autowired
    PerfilRiesgoRepositorio perfilRiesgoRepositorio;

    @Autowired
    RolRepositorio rolRepositorio;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiarDatos() {
        truncarDatos();
        eliminarRolesTemporales();
    }

    @AfterEach
    void limpiarDespuesDeLaPrueba() {
        truncarDatos();
        eliminarRolesTemporales();
    }

    @Test
    @DisplayName("Calcula el plan en los límites de monto con riesgo bajo")
    void calculaElPlanEnLosLimitesDeMonto() {
        assertThat(plan("9999999", NivelRiesgo.BAJO).roles()).isEmpty();
        assertThat(plan("10000000", NivelRiesgo.BAJO).roles()).isEmpty();
        assertThat(plan("10000001", NivelRiesgo.BAJO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR");
        assertThat(plan("49999999", NivelRiesgo.BAJO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR");
        assertThat(plan("50000000", NivelRiesgo.BAJO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR");
        assertThat(plan("50000001", NivelRiesgo.BAJO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE");
        assertThat(plan("199999999", NivelRiesgo.BAJO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE");
        assertThat(plan("200000000", NivelRiesgo.BAJO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE");
        assertThat(plan("200000001", NivelRiesgo.BAJO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE", "RIESGOS");
    }

    @Test
    @DisplayName("Agrega Riesgos cuando el riesgo es alto y no duplica roles")
    void agregaRiesgosCuandoElRiesgoEsAlto() {
        assertThat(plan("5000000", NivelRiesgo.ALTO).roles())
            .containsExactlyInAnyOrder("RIESGOS");
        assertThat(plan("10000000", NivelRiesgo.ALTO).roles())
            .containsExactlyInAnyOrder("RIESGOS");
        assertThat(plan("25000000", NivelRiesgo.ALTO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR", "RIESGOS");
        assertThat(plan("100000000", NivelRiesgo.ALTO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE", "RIESGOS");
        assertThat(plan("250000000", NivelRiesgo.ALTO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE", "RIESGOS");
        assertThat(plan("250000000", NivelRiesgo.ALTO).cantidad()).isEqualTo(3);
        assertThat(plan("5000000", NivelRiesgo.MEDIO).roles()).isEmpty();
        assertThat(plan("25000000", NivelRiesgo.MEDIO).roles())
            .containsExactlyInAnyOrder("SUPERVISOR");
    }

    @Test
    @DisplayName("El plan está vacío para cinco millones con riesgo bajo")
    void planVacioParaCincoMillonesConRiesgoBajo() {
        PlanAprobacion plan = plan("5000000", NivelRiesgo.BAJO);

        assertThat(plan.estaVacio()).isTrue();
        assertThat(plan.cantidad()).isZero();
        assertThat(plan.requiere("SUPERVISOR")).isFalse();
    }

    @Test
    @DisplayName("Una política adicional participa sin modificar el coordinador")
    void unaPoliticaAdicionalParticipaSinModificarElCoordinador() {
        PoliticaAprobacion adicional = contexto -> Set.of("GERENTE");
        CalculadoraPlanAprobacion conAdicional = new CalculadoraPlanAprobacion(
            List.of(
                new PoliticaAprobacionPorMonto(),
                new PoliticaAprobacionPorRiesgo(),
                adicional
            ),
            rolRepositorio
        );

        PlanAprobacion plan = conAdicional.calcular(
            new ContextoAprobacion(new BigDecimal("5000000"), NivelRiesgo.BAJO));

        assertThat(plan.roles()).containsExactlyInAnyOrder("GERENTE");
    }

    @Test
    @DisplayName("Acepta un rol aprobador fuera de los tres roles conocidos")
    void aceptaRolAprobadorFueraDeLosTresConocidos() {
        rolRepositorio.saveAndFlush(
            new Rol(ROL_TEMPORAL, "Aprobador especial", true, true));

        PoliticaAprobacion adicional = contexto -> Set.of(ROL_TEMPORAL);
        CalculadoraPlanAprobacion conAdicional = new CalculadoraPlanAprobacion(
            List.of(new PoliticaAprobacionPorMonto(), adicional),
            rolRepositorio
        );

        PlanAprobacion plan = conAdicional.calcular(
            new ContextoAprobacion(new BigDecimal("5000000"), NivelRiesgo.BAJO));

        assertThat(plan.roles()).containsExactlyInAnyOrder(ROL_TEMPORAL);
    }

    @Test
    @DisplayName("Rechaza un rol inexistente en el catálogo")
    void rechazaRolInexistente() {
        CalculadoraPlanAprobacion calculadora = new CalculadoraPlanAprobacion(
            List.of(contexto -> Set.of("NO_EXISTE")),
            rolRepositorio
        );

        assertThatThrownBy(() -> calculadora.calcular(
            new ContextoAprobacion(new BigDecimal("1000000"), NivelRiesgo.BAJO)))
            .isInstanceOf(PoliticaAprobacionInvalidaException.class)
            .hasMessageContaining("NO_EXISTE");
    }

    @Test
    @DisplayName("Rechaza un rol inactivo")
    void rechazaRolInactivo() {
        rolRepositorio.saveAndFlush(
            new Rol(ROL_INACTIVO, "Rol inactivo", true, false));

        CalculadoraPlanAprobacion calculadora = new CalculadoraPlanAprobacion(
            List.of(contexto -> Set.of(ROL_INACTIVO)),
            rolRepositorio
        );

        assertThatThrownBy(() -> calculadora.calcular(
            new ContextoAprobacion(new BigDecimal("1000000"), NivelRiesgo.BAJO)))
            .isInstanceOf(PoliticaAprobacionInvalidaException.class)
            .hasMessageContaining(ROL_INACTIVO);
    }

    @Test
    @DisplayName("Rechaza un rol que no es aprobador")
    void rechazaRolNoAprobador() {
        CalculadoraPlanAprobacion calculadora = new CalculadoraPlanAprobacion(
            List.of(contexto -> Set.of("AUDITOR")),
            rolRepositorio
        );

        assertThatThrownBy(() -> calculadora.calcular(
            new ContextoAprobacion(new BigDecimal("1000000"), NivelRiesgo.BAJO)))
            .isInstanceOf(PoliticaAprobacionInvalidaException.class)
            .hasMessageContaining("AUDITOR");
    }

    @Test
    @DisplayName("El plan es inmutable y el coordinador elimina duplicados")
    void elPlanEsInmutableYElCoordinadorEliminaDuplicados() {
        PoliticaAprobacion duplicada = contexto -> Set.of("SUPERVISOR");
        CalculadoraPlanAprobacion calculadora = new CalculadoraPlanAprobacion(
            List.of(duplicada, duplicada),
            rolRepositorio
        );

        PlanAprobacion plan = calculadora.calcular(
            new ContextoAprobacion(new BigDecimal("25000000"), NivelRiesgo.BAJO));

        assertThat(plan.roles()).containsExactlyInAnyOrder("SUPERVISOR");
        assertThatThrownBy(() -> plan.roles().add("GERENTE"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("El contexto de aprobación exige monto positivo y riesgo")
    void elContextoExigeMontoPositivoYRiesgo() {
        assertThatThrownBy(() -> new ContextoAprobacion(
            BigDecimal.ZERO, NivelRiesgo.BAJO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextoAprobacion(
            new BigDecimal("1"), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Mapea y recupera tareas y decisiones de aprobación")
    void mapeaYRecuperaTareasYDecisiones() {
        SolicitudCreada creada = crearSolicitud(
            "1000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario actor = crearUsuario("NORMAL");
        Rol rol = rolRepositorio.findById("SUPERVISOR").orElseThrow();

        TareaAprobacion tarea = new TareaAprobacion(creada.revision(), rol);
        tarea.reasignar(actor);
        tarea.marcarAprobada();
        tarea = tareaRepositorio.saveAndFlush(tarea);

        decisionRepositorio.saveAndFlush(new DecisionAprobacion(
            tarea, actor, TipoDecision.APROBADA, "Aprobado", false));

        TareaAprobacion recuperada = tareaRepositorio
            .buscarPorRevision(creada.revision().getId())
            .getFirst();

        assertThat(recuperada.getRol().getCodigo()).isEqualTo("SUPERVISOR");
        assertThat(recuperada.getEstado()).isEqualTo(EstadoTareaAprobacion.APROBADA);
        assertThat(recuperada.getAsignado().getId()).isEqualTo(actor.getId());
        assertThat(recuperada.getCreadoEn()).isNotNull();
        assertThat(recuperada.getActualizadoEn()).isNotNull();
        assertThat(recuperada.getVersion()).isNotNull();

        DecisionAprobacion recuperadaDecision = decisionRepositorio
            .buscarPorTarea(tarea.getId())
            .orElseThrow();

        assertThat(recuperadaDecision.getTarea().getId()).isEqualTo(tarea.getId());
        assertThat(recuperadaDecision.getActor().getId()).isEqualTo(actor.getId());
        assertThat(recuperadaDecision.getDecision()).isEqualTo(TipoDecision.APROBADA);
        assertThat(recuperadaDecision.getMotivo()).isEqualTo("Aprobado");
        assertThat(recuperadaDecision.isDelegado()).isFalse();
        assertThat(recuperadaDecision.getFecha()).isNotNull();
    }

    @Test
    @DisplayName("Impide dos tareas para la misma revisión y rol")
    void impideDosTareasParaLaMismaRevisionYRol() {
        SolicitudCreada creada = crearSolicitud(
            "1000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Rol rol = rolRepositorio.findById("SUPERVISOR").orElseThrow();

        tareaRepositorio.saveAndFlush(new TareaAprobacion(creada.revision(), rol));

        assertThatThrownBy(() -> tareaRepositorio.saveAndFlush(
            new TareaAprobacion(creada.revision(), rol)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Impide dos decisiones para la misma tarea")
    void impideDosDecisionesParaLaMismaTarea() {
        SolicitudCreada creada = crearSolicitud(
            "1000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario actor1 = crearUsuario("NORMAL");
        Usuario actor2 = crearUsuario("NORMAL");
        TareaAprobacion tarea = guardarTareaAprobada(creada, "SUPERVISOR");

        decisionRepositorio.saveAndFlush(new DecisionAprobacion(
            tarea, actor1, TipoDecision.APROBADA, "Aprobado", false));

        assertThatThrownBy(() -> decisionRepositorio.saveAndFlush(
            new DecisionAprobacion(
                tarea, actor2, TipoDecision.APROBADA, "Aprobado", false)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una tarea pendiente puede aprobarse, rechazarse o cancelarse")
    void tareaPendientePuedeAprobarseRechazarseOCancelarse() {
        SolicitudCreada creada = crearSolicitud(
            "1000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Rol rol = rolRepositorio.findById("SUPERVISOR").orElseThrow();

        TareaAprobacion aprobada = new TareaAprobacion(creada.revision(), rol);
        aprobada.marcarAprobada();
        assertThat(aprobada.getEstado()).isEqualTo(EstadoTareaAprobacion.APROBADA);
        assertThat(aprobada.getActualizadoEn()).isNotNull();

        TareaAprobacion rechazada = new TareaAprobacion(creada.revision(), rol);
        rechazada.marcarRechazada();
        assertThat(rechazada.getEstado()).isEqualTo(EstadoTareaAprobacion.RECHAZADA);

        TareaAprobacion cancelada = new TareaAprobacion(creada.revision(), rol);
        cancelada.cancelar();
        assertThat(cancelada.getEstado()).isEqualTo(EstadoTareaAprobacion.CANCELADA);
    }

    @Test
    @DisplayName("Una tarea en estado final no admite cambios ni reasignación")
    void tareaEnEstadoFinalNoAdmiteCambiosNiReasignacion() {
        SolicitudCreada creada = crearSolicitud(
            "1000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Rol rol = rolRepositorio.findById("SUPERVISOR").orElseThrow();
        Usuario actor = crearUsuario("NORMAL");

        TareaAprobacion tarea = new TareaAprobacion(creada.revision(), rol);
        tarea.marcarAprobada();

        assertThatThrownBy(tarea::marcarRechazada)
            .isInstanceOf(TransicionTareaInvalidaException.class);
        assertThatThrownBy(tarea::cancelar)
            .isInstanceOf(TransicionTareaInvalidaException.class);
        assertThatThrownBy(() -> tarea.reasignar(actor))
            .isInstanceOf(TransicionTareaInvalidaException.class);
        assertThat(tarea.getEstado()).isEqualTo(EstadoTareaAprobacion.APROBADA);
    }

    @Test
    @DisplayName("Una tarea pendiente puede reasignarse")
    void tareaPendientePuedeReasignarse() {
        SolicitudCreada creada = crearSolicitud(
            "1000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Rol rol = rolRepositorio.findById("SUPERVISOR").orElseThrow();
        Usuario actor = crearUsuario("NORMAL");

        TareaAprobacion tarea = new TareaAprobacion(creada.revision(), rol);
        tarea.reasignar(actor);

        assertThat(tarea.getAsignado().getId()).isEqualTo(actor.getId());
        assertThat(tarea.getActualizadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Asocia el identificador de motor de forma idempotente")
    void asociaIdentificadorMotorDeFormaIdempotente() {
        SolicitudCreada creada = crearSolicitud(
            "1000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Rol rol = rolRepositorio.findById("SUPERVISOR").orElseThrow();
        TareaAprobacion tarea = new TareaAprobacion(creada.revision(), rol);

        tarea.asignarIdentificadorMotor("motor-1");
        tarea.asignarIdentificadorMotor("motor-1");

        assertThat(tarea.getIdentificadorTareaMotor()).isEqualTo("motor-1");
        assertThat(tarea.getActualizadoEn()).isNotNull();
        assertThatThrownBy(() -> tarea.asignarIdentificadorMotor("motor-2"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("otro identificador");
        assertThatThrownBy(() -> tarea.asignarIdentificadorMotor(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Programa el vencimiento solo mientras la tarea está pendiente")
    void programaVencimientoSoloMientrasEstaPendiente() {
        SolicitudCreada creada = crearSolicitud(
            "1000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Rol rol = rolRepositorio.findById("SUPERVISOR").orElseThrow();
        TareaAprobacion tarea = new TareaAprobacion(creada.revision(), rol);
        Instant vencimiento = Instant.now().plus(Duration.ofHours(48));

        tarea.programarVencimiento(vencimiento);

        assertThat(tarea.getFechaVencimiento()).isEqualTo(vencimiento);
        assertThatThrownBy(() -> tarea.programarVencimiento(null))
            .isInstanceOf(IllegalArgumentException.class);

        tarea.marcarAprobada();

        assertThatThrownBy(() -> tarea.programarVencimiento(vencimiento))
            .isInstanceOf(TransicionTareaInvalidaException.class);
        assertThatThrownBy(() -> tarea.asignarIdentificadorMotor("motor-2"))
            .isInstanceOf(TransicionTareaInvalidaException.class);
    }

    @Test
    @DisplayName("Rechaza una tarea para un rol que no es aprobador")
    void rechazaTareaConRolNoAprobador() {
        SolicitudCreada creada = crearSolicitud(
            "1000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Rol auditor = rolRepositorio.findById("AUDITOR").orElseThrow();

        assertThatThrownBy(() -> new TareaAprobacion(creada.revision(), auditor))
            .isInstanceOf(RolAprobadorInvalidoException.class);
    }

    @Test
    @DisplayName("Autoriza cuando todas las aprobaciones exigidas están completas")
    void verificadorAutorizaConTodasLasAprobaciones() {
        SolicitudCreada creada = crearSolicitud(
            "25000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario supervisor = crearUsuario("NORMAL");

        aprobarRol(creada, "SUPERVISOR", supervisor);

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isTrue();
        assertThat(resultado.motivos()).isEmpty();
    }

    @Test
    @DisplayName("Autoriza sin tareas cuando el plan está vacío")
    void verificadorAutorizaSinTareasCuandoElPlanEsVacio() {
        SolicitudCreada creada = crearSolicitud(
            "5000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isTrue();
        assertThat(resultado.motivos()).isEmpty();
    }

    @Test
    @DisplayName("Autoriza cien millones con riesgo alto y tres actores distintos")
    void verificadorAutorizaOperacionDe100MillonesConRiesgoAlto() {
        SolicitudCreada creada = crearSolicitud(
            "100000000", NivelRiesgo.ALTO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario supervisor = crearUsuario("NORMAL");
        Usuario gerente = crearUsuario("NORMAL");
        Usuario riesgos = crearUsuario("NORMAL");

        aprobarRol(creada, "SUPERVISOR", supervisor);
        aprobarRol(creada, "GERENTE", gerente);
        aprobarRol(creada, "RIESGOS", riesgos);

        assertThat(verificar(creada).autorizado()).isTrue();
    }

    @Test
    @DisplayName("Autoriza doscientos cincuenta millones con tres actores distintos")
    void verificadorAutorizaOperacionDe250MillonesConTresActores() {
        SolicitudCreada creada = crearSolicitud(
            "250000000", NivelRiesgo.ALTO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario supervisor = crearUsuario("NORMAL");
        Usuario gerente = crearUsuario("NORMAL");
        Usuario riesgos = crearUsuario("NORMAL");

        aprobarRol(creada, "SUPERVISOR", supervisor);
        aprobarRol(creada, "GERENTE", gerente);
        aprobarRol(creada, "RIESGOS", riesgos);

        assertThat(verificar(creada).autorizado()).isTrue();
    }

    @Test
    @DisplayName("Rechaza cuando falta una tarea requerida")
    void verificadorRechazaAprobacionFaltante() {
        SolicitudCreada creada = crearSolicitud(
            "25000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos())
            .anyMatch(motivo -> motivo.contains("Falta la tarea")
                && motivo.contains("SUPERVISOR"));
    }

    @Test
    @DisplayName("Rechaza una tarea pendiente")
    void verificadorRechazaTareaPendiente() {
        SolicitudCreada creada = crearSolicitud(
            "25000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        guardarTareaPendiente(creada, "SUPERVISOR");

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos())
            .anyMatch(motivo -> motivo.contains("no está aprobada")
                && motivo.contains("PENDIENTE"));
    }

    @Test
    @DisplayName("Rechaza una tarea rechazada aunque el plan esté completo")
    void verificadorRechazaTareaRechazada() {
        SolicitudCreada creada = crearSolicitud(
            "25000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario actor = crearUsuario("NORMAL");
        TareaAprobacion tarea = guardarTareaRechazada(creada, "SUPERVISOR");
        guardarDecision(tarea, actor, TipoDecision.RECHAZADA);

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos())
            .anyMatch(motivo -> motivo.contains("fue rechazada"));
    }

    @Test
    @DisplayName("Rechaza una tarea aprobada sin decisión")
    void verificadorRechazaTareaAprobadaSinDecision() {
        SolicitudCreada creada = crearSolicitud(
            "25000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        guardarTareaAprobada(creada, "SUPERVISOR");

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos())
            .anyMatch(motivo -> motivo.contains("Falta la decisión"));
    }

    @Test
    @DisplayName("Rechaza una decisión no aprobadora sobre una tarea aprobada")
    void verificadorRechazaDecisionNoAprobadora() {
        SolicitudCreada creada = crearSolicitud(
            "25000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario actor = crearUsuario("NORMAL");
        TareaAprobacion tarea = guardarTareaAprobada(creada, "SUPERVISOR");
        guardarDecision(tarea, actor, TipoDecision.RECHAZADA);

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos())
            .anyMatch(motivo -> motivo.contains("no es aprobatoria"));
    }

    @Test
    @DisplayName("Rechaza una decisión del creador de la solicitud")
    void verificadorRechazaDecisionDelCreador() {
        SolicitudCreada creada = crearSolicitud(
            "25000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        TareaAprobacion tarea = guardarTareaAprobada(creada, "SUPERVISOR");
        guardarDecision(tarea, creada.solicitante(), TipoDecision.APROBADA);
        registroActorRol.registrarAprobacion(
            creada.solicitud().getId(), creada.solicitante().getId(), "SUPERVISOR");

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos())
            .anyMatch(motivo -> motivo.contains("El solicitante no puede aprobar"));
    }

    @Test
    @DisplayName("Rechaza una decisión sin registro del rol funcional")
    void verificadorRechazaActorSinRegistro() {
        SolicitudCreada creada = crearSolicitud(
            "25000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario actor = crearUsuario("NORMAL");
        TareaAprobacion tarea = guardarTareaAprobada(creada, "SUPERVISOR");
        guardarDecision(tarea, actor, TipoDecision.APROBADA);

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos())
            .anyMatch(motivo -> motivo.contains("no tiene un rol funcional registrado"));
    }

    @Test
    @DisplayName("Rechaza un actor que aprobó dos roles funcionales distintos")
    void verificadorRechazaActorConDosRolesDistintos() {
        SolicitudCreada creada = crearSolicitud(
            "100000000", NivelRiesgo.ALTO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario actorSupervisor = crearUsuario("NORMAL");
        Usuario actorRiesgos = crearUsuario("NORMAL");

        TareaAprobacion tareaSupervisor = guardarTareaAprobada(creada, "SUPERVISOR");
        guardarDecision(tareaSupervisor, actorSupervisor, TipoDecision.APROBADA);
        registroActorRol.registrarAprobacion(
            creada.solicitud().getId(), actorSupervisor.getId(), "SUPERVISOR");

        TareaAprobacion tareaGerente = guardarTareaAprobada(creada, "GERENTE");
        guardarDecision(tareaGerente, actorSupervisor, TipoDecision.APROBADA);

        TareaAprobacion tareaRiesgos = guardarTareaAprobada(creada, "RIESGOS");
        guardarDecision(tareaRiesgos, actorRiesgos, TipoDecision.APROBADA);
        registroActorRol.registrarAprobacion(
            creada.solicitud().getId(), actorRiesgos.getId(), "RIESGOS");

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos())
            .anyMatch(motivo -> motivo.contains("ya aprobó el rol SUPERVISOR"));
    }

    @Test
    @DisplayName("Rechaza una revisión que no es la actual")
    void verificadorRechazaRevisionQueNoEsLaActual() {
        SolicitudCreada creada = crearSolicitud(
            "25000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario actor = crearUsuario("NORMAL");
        aprobarRol(creada, "SUPERVISOR", actor);

        RevisionSolicitud nueva = agregarRevision(creada.solicitud(), 2);

        ResultadoAutorizacion resultado = verificador.verificar(
            creada.solicitud().getId(), creada.revision().getId());

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos())
            .anyMatch(motivo -> motivo.contains("no es la actual"));
        assertThat(nueva.getEstado()).isEqualTo(EstadoRevision.RECIBIDA);
    }

    @Test
    @DisplayName("Rechaza una revisión en estado terminal")
    void verificadorRechazaEstadoTerminal() {
        for (EstadoRevision estado : List.of(
            EstadoRevision.RECHAZADA,
            EstadoRevision.EJECUTADA)) {
            SolicitudCreada creada = crearSolicitud(
                "25000000", NivelRiesgo.BAJO, estado);

            ResultadoAutorizacion resultado = verificar(creada);

            assertThat(resultado.autorizado())
                .as("estado %s", estado)
                .isFalse();
            assertThat(resultado.motivos())
                .as("estado %s", estado)
                .anyMatch(motivo -> motivo.contains(estado.name()));
        }

        // La cancelación desde PENDIENTE_APROBACION se habilita en el Paso 4;
        // aquí se cambia el estado solo para ejercitar la rama del verificador.
        SolicitudCreada cancelada = crearSolicitud(
            "25000000", NivelRiesgo.BAJO, EstadoRevision.PENDIENTE_APROBACION);
        jdbcTemplate.update(
            "UPDATE revision_solicitud SET revisoli_estado = 'CANCELADA' "
                + "WHERE revisoli_id = ?",
            cancelada.revision().getId());

        ResultadoAutorizacion resultadoCancelada = verificar(cancelada);
        assertThat(resultadoCancelada.autorizado()).isFalse();
        assertThat(resultadoCancelada.motivos())
            .anyMatch(motivo -> motivo.contains("CANCELADA"));
    }

    @Test
    @DisplayName("Rechaza una revisión sin riesgo clasificado")
    void verificadorRechazaRevisionSinRiesgoClasificado() {
        SolicitudCreada creada = crearSolicitud(
            "25000000", null, EstadoRevision.EVALUANDO_RIESGO);

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos())
            .anyMatch(motivo -> motivo.contains("riesgo clasificado"));
    }

    @Test
    @DisplayName("Rechaza Gerencia y Riesgos aprobados por el mismo actor")
    void verificadorRechazaGerenciaYRiesgosConElMismoActor() {
        SolicitudCreada creada = crearSolicitud(
            "250000000", NivelRiesgo.ALTO, EstadoRevision.PENDIENTE_APROBACION);
        Usuario supervisor = crearUsuario("NORMAL");
        Usuario mismoActor = crearUsuario("NORMAL");

        aprobarRol(creada, "SUPERVISOR", supervisor);

        TareaAprobacion tareaGerente = guardarTareaAprobada(creada, "GERENTE");
        guardarDecision(tareaGerente, mismoActor, TipoDecision.APROBADA);
        registroActorRol.registrarAprobacion(
            creada.solicitud().getId(), mismoActor.getId(), "GERENTE");

        TareaAprobacion tareaRiesgos = guardarTareaAprobada(creada, "RIESGOS");
        guardarDecision(tareaRiesgos, mismoActor, TipoDecision.APROBADA);

        ResultadoAutorizacion resultado = verificar(creada);

        assertThat(resultado.autorizado()).isFalse();
        assertThat(resultado.motivos()).anyMatch(motivo -> motivo.contains(
            "Gerencia y Riesgos deben ser aprobados por actores distintos"));
    }

    private PlanAprobacion plan(String monto, NivelRiesgo riesgo) {
        return calculadora.calcular(
            new ContextoAprobacion(new BigDecimal(monto), riesgo));
    }

    private SolicitudCreada crearSolicitud(
        String monto,
        NivelRiesgo riesgo,
        EstadoRevision estadoDestino
    ) {
        Usuario solicitante = crearUsuario("NORMAL");
        TipoOperacion tipo = tipoOperacionRepositorio.findById("NORMAL").orElseThrow();
        Moneda moneda = monedaRepositorio.findById("PYG").orElseThrow();

        SolicitudFinanciera solicitud = solicitudRepositorio.saveAndFlush(
            new SolicitudFinanciera(solicitante));
        RevisionSolicitud revision = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                solicitud,
                1,
                tipo,
                new BigDecimal(monto),
                moneda,
                "Pago de demostración",
                "Operación autorizada para pruebas"
            )
        );
        solicitud.establecerRevisionActual(revision);
        solicitud = solicitudRepositorio.saveAndFlush(solicitud);

        revision = transicionar(solicitud, revision, EstadoRevision.VALIDANDO);
        if (riesgo != null) {
            revision = revisionRepositorio
                .buscarDetallePorId(revision.getId())
                .orElseThrow();
            revision.registrarNivelRiesgo(riesgo);
            revision = revisionRepositorio.saveAndFlush(revision);
        }

        if (estadoDestino == EstadoRevision.VALIDANDO) {
            return new SolicitudCreada(solicitante, solicitud, revision);
        }

        revision = transicionar(solicitud, revision, EstadoRevision.EVALUANDO_RIESGO);
        if (estadoDestino == EstadoRevision.EVALUANDO_RIESGO) {
            return new SolicitudCreada(solicitante, solicitud, revision);
        }

        revision = transicionar(
            solicitud, revision, EstadoRevision.PENDIENTE_APROBACION);
        if (estadoDestino == EstadoRevision.PENDIENTE_APROBACION) {
            return new SolicitudCreada(solicitante, solicitud, revision);
        }

        if (estadoDestino == EstadoRevision.RECHAZADA) {
            revision = transicionar(solicitud, revision, EstadoRevision.RECHAZADA);
            return new SolicitudCreada(solicitante, solicitud, revision);
        }

        if (estadoDestino == EstadoRevision.EJECUTADA) {
            revision = transicionar(solicitud, revision, EstadoRevision.APROBADA);
            revision = transicionar(solicitud, revision, EstadoRevision.EJECUTANDO);
            revision = transicionar(solicitud, revision, EstadoRevision.EJECUTADA);
            return new SolicitudCreada(solicitante, solicitud, revision);
        }

        throw new IllegalArgumentException(
            "Estado de prueba no soportado: " + estadoDestino);
    }

    private RevisionSolicitud transicionar(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        EstadoRevision destino
    ) {
        return servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            destino,
            new ContextoTransicion(null, null, "corr-" + destino, "Detalle de prueba")
        );
    }

    private RevisionSolicitud agregarRevision(SolicitudFinanciera solicitud, int numero) {
        TipoOperacion tipo = tipoOperacionRepositorio.findById("NORMAL").orElseThrow();
        Moneda moneda = monedaRepositorio.findById("PYG").orElseThrow();

        RevisionSolicitud revision = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                solicitud,
                numero,
                tipo,
                new BigDecimal("25000000"),
                moneda,
                "Pago de demostración",
                "Operación autorizada para pruebas"
            )
        );
        solicitud.establecerRevisionActual(revision);
        solicitudRepositorio.saveAndFlush(solicitud);
        return revision;
    }

    private Usuario crearUsuario(String perfilCodigo) {
        PerfilRiesgo perfil = perfilRiesgoRepositorio.findById(perfilCodigo).orElseThrow();
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

    private TareaAprobacion guardarTareaPendiente(
        SolicitudCreada creada,
        String rolCodigo
    ) {
        Rol rol = rolRepositorio.findById(rolCodigo).orElseThrow();
        return tareaRepositorio.saveAndFlush(
            new TareaAprobacion(creada.revision(), rol));
    }

    private TareaAprobacion guardarTareaAprobada(
        SolicitudCreada creada,
        String rolCodigo
    ) {
        Rol rol = rolRepositorio.findById(rolCodigo).orElseThrow();
        TareaAprobacion tarea = new TareaAprobacion(creada.revision(), rol);
        tarea.marcarAprobada();
        return tareaRepositorio.saveAndFlush(tarea);
    }

    private TareaAprobacion guardarTareaRechazada(
        SolicitudCreada creada,
        String rolCodigo
    ) {
        Rol rol = rolRepositorio.findById(rolCodigo).orElseThrow();
        TareaAprobacion tarea = new TareaAprobacion(creada.revision(), rol);
        tarea.marcarRechazada();
        return tareaRepositorio.saveAndFlush(tarea);
    }

    private DecisionAprobacion guardarDecision(
        TareaAprobacion tarea,
        Usuario actor,
        TipoDecision tipo
    ) {
        return decisionRepositorio.saveAndFlush(
            new DecisionAprobacion(tarea, actor, tipo, "Decisión de prueba", false));
    }

    private void aprobarRol(
        SolicitudCreada creada,
        String rolCodigo,
        Usuario actor
    ) {
        TareaAprobacion tarea = guardarTareaAprobada(creada, rolCodigo);
        guardarDecision(tarea, actor, TipoDecision.APROBADA);
        registroActorRol.registrarAprobacion(
            creada.solicitud().getId(), actor.getId(), rolCodigo);
    }

    private ResultadoAutorizacion verificar(SolicitudCreada creada) {
        return verificador.verificar(
            creada.solicitud().getId(), creada.revision().getId());
    }

    private void truncarDatos() {
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera, usuario CASCADE");
    }

    private void eliminarRolesTemporales() {
        jdbcTemplate.update(
            "DELETE FROM rol WHERE rol_codigo IN (?, ?)",
            ROL_TEMPORAL,
            ROL_INACTIVO
        );
    }

    private record SolicitudCreada(
        Usuario solicitante,
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision
    ) {
    }
}
