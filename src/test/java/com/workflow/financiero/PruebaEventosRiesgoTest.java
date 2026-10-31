package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Cubre la recepción firmada, el inbox, los duplicados, los conflictos y la
 * aplicación única del riesgo externo al proceso.
 */
class PruebaEventosRiesgoTest extends PruebaProcesoAprobacionBase {

    @Test
    @DisplayName("Un evento normal se aplica una sola vez y no crea aprobaciones")
    void eventoNormalSeAplicaUnaSolaVez() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        int tareas = rolesTareasNegocioDeRevision(revisionId).size();

        aprobarTodas(solicitudId);
        long decisiones = contarDecisionesDeRevision(revisionId);
        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(contarSuscripcionesRiesgo(procesoId)).isEqualTo(1);

        String evento = evento();
        notificarRiesgo(
            solicitudId,
            1,
            "ALTO",
            evento,
            Instant.now(),
            Instant.now().toString(),
            null
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PROCESADO"));

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(estadoIntento(revisionId)).isEqualTo("PENDIENTE");
        assertThat(estadoEventoExterno(evento)).isEqualTo("PROCESADO");
        assertThat(estadoEvaluacionRiesgo(revisionId)).isEqualTo("RECIBIDA");
        assertThat(nivelEvaluacionRiesgo(revisionId)).isEqualTo("ALTO");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EVALUACION_EXTERNA_RESUELTA")).isEqualTo(1);
        assertThat(rolesTareasNegocioDeRevision(revisionId)).hasSize(tareas);
        assertThat(contarDecisionesDeRevision(revisionId))
            .isEqualTo(decisiones);
        assertThat(procesoActivo(procesoId)).isTrue();
        assertThat(contarSuscripcionesEjecucion(procesoId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Un duplicado idéntico no se aplica dos veces")
    void duplicadoIdenticoNoSeAplicaDosVeces() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        aprobarTodas(solicitudId);

        String evento = evento();
        Instant fecha = Instant.now();
        notificarRiesgo(
            solicitudId, 1, "ALTO", evento, fecha,
            Instant.now().toString(), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PROCESADO"));

        notificarRiesgo(
            solicitudId, 1, "ALTO", evento, fecha,
            Instant.now().toString(), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("DUPLICADO"));

        assertThat(contarEventosExternos()).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EVALUACION_EXTERNA_RESUELTA")).isEqualTo(1);
        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
    }

    @Test
    @DisplayName("El mismo identificador con otra huella genera conflicto")
    void mismoIdentificadorConOtraHuellaGeneraConflicto() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        aprobarTodas(solicitudId);

        String evento = evento();
        Instant fecha = Instant.now();
        notificarRiesgo(
            solicitudId, 1, "ALTO", evento, fecha,
            Instant.now().toString(), null)
            .andExpect(status().isOk());

        notificarRiesgo(
            solicitudId, 1, "MEDIO", evento, fecha,
            Instant.now().toString(), null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo")
                .value("EVENTO_EXTERNO_EN_CONFLICTO"));

        assertThat(conflictosEventoExterno(evento)).isEqualTo(1);
        assertThat(contarEventosExternos()).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EVALUACION_EXTERNA_CONFLICTO")).isEqualTo(1);
        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EVALUACION_EXTERNA_RESUELTA")).isEqualTo(1);
    }

    @Test
    @DisplayName("Una firma inválida no persiste ni avanza el proceso")
    void firmaInvalidaNoPersisteNiAvanza() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        aprobarTodas(solicitudId);

        notificarRiesgo(
            solicitudId,
            1,
            "ALTO",
            evento(),
            Instant.now(),
            Instant.now().toString(),
            "00"
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        assertThat(contarEventosExternos()).isZero();
        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(contarSuscripcionesRiesgo(procesoId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una fecha fuera de ventana no persiste el evento")
    void fechaFueraDeVentanaNoPersiste() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        aprobarTodas(solicitudId);

        notificarRiesgo(
            solicitudId,
            1,
            "ALTO",
            evento(),
            Instant.now().minus(Duration.ofHours(1)),
            Instant.now().toString(),
            null
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo")
                .value("EVENTO_EXTERNO_INVALIDO"));

        assertThat(contarEventosExternos()).isZero();
        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
    }

    @Test
    @DisplayName("Un evento temprano queda pendiente y se aplica al reintentar")
    void eventoTempranoQuedaPendiente() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        String evento = evento();
        notificarRiesgo(
            solicitudId, 1, "ALTO", evento,
            Instant.now(), Instant.now().toString(), null)
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        assertThat(estadoEventoExterno(evento)).isEqualTo("PENDIENTE");
        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(contarSuscripcionesRiesgo(procesoId)).isZero();

        aprobarTodas(solicitudId);
        assertThat(contarSuscripcionesRiesgo(procesoId)).isEqualTo(1);

        ejecutarRiesgoExterno();

        assertThat(estadoEventoExterno(evento)).isEqualTo("PROCESADO");
        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EVALUACION_EXTERNA_RESUELTA")).isEqualTo(1);

        ejecutarRiesgoExterno();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EVALUACION_EXTERNA_RESUELTA")).isEqualTo(1);
    }

    @Test
    @DisplayName("Un evento para una revisión inexistente queda pendiente")
    void eventoParaRevisionInexistenteQuedaPendiente() throws Exception {
        String solicitudId = UUID.randomUUID().toString();
        String evento = evento();

        notificarRiesgo(
            solicitudId, 1, "ALTO", evento,
            Instant.now(), Instant.now().toString(), null)
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        assertThat(estadoEventoExterno(evento)).isEqualTo("PENDIENTE");
        UUID solicitudFk = jdbcTemplate.queryForObject(
            "SELECT evenexte_solifina_id FROM evento_externo "
                + "WHERE evenexte_proveedor = ? "
                + "AND evenexte_identificador_evento = ?",
            UUID.class,
            PROVEEDOR_RIESGO,
            evento);
        assertThat(solicitudFk).isNull();

        ejecutarRiesgoExterno();
        assertThat(estadoEventoExterno(evento)).isEqualTo("PENDIENTE");
        assertThat(intentosEventoExterno(evento)).isZero();
    }

    @Test
    @DisplayName("Un evento de una revisión anterior queda obsoleto")
    void eventoDeRevisionAnteriorQuedaObsoleto() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionAnterior = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacion(solicitudId, "SUPERVISOR"),
            "supervisor",
            "Rechazo para habilitar el reenvío"
        ).andExpect(status().isOk());

        MvcResult reenvio = reenviar(
            solicitudId, "demo", 250_000_000, "NORMAL")
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode nueva = leer(reenvio);
        String revisionActual = nueva.get("idRevision").asText();

        assertThat(estadoRevision(revisionAnterior)).isEqualTo("RECHAZADA");
        assertThat(nueva.get("revision").asInt()).isEqualTo(2);

        String evento = evento();
        notificarRiesgo(
            solicitudId, 1, "ALTO", evento,
            Instant.now(), Instant.now().toString(), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("OBSOLETO"));

        assertThat(estadoEventoExterno(evento)).isEqualTo("OBSOLETO");
        assertThat(estadoEvaluacionRiesgo(revisionAnterior))
            .isEqualTo("OBSOLETA");
        assertThat(estadoRevision(revisionActual))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(contarEventosTipoDeRevision(
            revisionAnterior, "EVALUACION_EXTERNA_RESUELTA")).isZero();
    }

    @Test
    @DisplayName("El resultado externo menor no reduce el riesgo interno")
    void resultadoExternoMenorNoReduceElInterno() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        aprobarTodas(solicitudId);

        notificarRiesgo(
            solicitudId, 1, "MEDIO", evento(),
            Instant.now(), Instant.now().toString(), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PROCESADO"));

        assertThat(nivelRevision(revisionId)).isEqualTo("ALTO");
        assertThat(nivelEvaluacionRiesgo(revisionId)).isEqualTo("MEDIO");
        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
    }

    @Test
    @DisplayName("El endpoint rechaza niveles inválidos y campos faltantes")
    void endpointRechazaNivelInvalidoYCamposFaltantes() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        aprobarTodas(solicitudId);

        notificarRiesgo(
            solicitudId, 1, "CRITICO", evento(),
            Instant.now(), Instant.now().toString(), null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        String evento = evento();
        String timestamp = Instant.now().toString();
        String json = "{"
            + "\"proveedor\":\"" + PROVEEDOR_RIESGO + "\","
            + "\"identificadorEvento\":\"" + evento + "\","
            + "\"identificadorSolicitud\":\"" + solicitudId + "\","
            + "\"nivelRiesgo\":\"ALTO\","
            + "\"fechaProveedor\":\"" + Instant.now() + "\","
            + "\"resultado\":\"Sin número de revisión\""
            + "}";
        notificarRiesgoCrudo(json, evento, timestamp, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        assertThat(contarEventosExternos()).isZero();
    }

    @Test
    @DisplayName("La solicitud externa usa una identidad estable y no se repite")
    void solicitudExternaUsaIdentidadEstable() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        ejecutarRiesgoExterno();

        assertThat(proveedorRiesgo.solicitudes()).hasSize(1);
        var solicitud = proveedorRiesgo.ultimaSolicitud();
        assertThat(solicitud.idSolicitud().toString()).isEqualTo(solicitudId);
        assertThat(solicitud.idRevision().toString()).isEqualTo(revisionId);
        assertThat(solicitud.numeroRevision()).isEqualTo(1);
        assertThat(solicitud.nivelInterno().name()).isEqualTo("ALTO");
        assertThat(solicitud.correlacion())
            .isEqualTo(claveEvaluacionRiesgo(revisionId));
        assertThat(estadoEvaluacionRiesgo(revisionId)).isEqualTo("PENDIENTE");
        assertThat(intentosEvaluacionRiesgo(revisionId)).isEqualTo(1);
        assertThat(referenciaEvaluacionRiesgo(revisionId))
            .isEqualTo("RIESGO-" + solicitud.correlacion());

        ejecutarRiesgoExterno();
        assertThat(proveedorRiesgo.solicitudes()).hasSize(1);
    }

    @Test
    @DisplayName("Un fallo de envío se registra y se reintenta con la misma clave")
    void falloDeEnvioSeRegistraYReintenta() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String revisionId = creada.get("idRevision").asText();
        String clave = claveEvaluacionRiesgo(revisionId);

        proveedorRiesgo.fallarProxima();
        ejecutarRiesgoExterno();

        assertThat(estadoEvaluacionRiesgo(revisionId)).isEqualTo("PENDIENTE");
        assertThat(intentosEvaluacionRiesgo(revisionId)).isEqualTo(1);
        assertThat(referenciaEvaluacionRiesgo(revisionId)).isNull();
        assertThat(errorEvaluacionRiesgo(revisionId)).isNotNull();

        proveedorRiesgo.dejarDeFallar();
        ejecutarRiesgoExterno();

        assertThat(referenciaEvaluacionRiesgo(revisionId))
            .isEqualTo("RIESGO-" + clave);
        assertThat(errorEvaluacionRiesgo(revisionId)).isNull();
        assertThat(proveedorRiesgo.solicitudes()).hasSize(1);
        assertThat(proveedorRiesgo.ultimaSolicitud().correlacion())
            .isEqualTo(clave);
    }

    private void aprobarTodas(String solicitudId) throws Exception {
        aprobar(
            idTareaAprobacion(solicitudId, "SUPERVISOR"),
            "supervisor",
            null
        ).andExpect(status().isOk());
        aprobar(
            idTareaAprobacion(solicitudId, "GERENTE"),
            "gerente",
            null
        ).andExpect(status().isOk());
        aprobar(
            idTareaAprobacion(solicitudId, "RIESGOS"),
            "riesgos",
            null
        ).andExpect(status().isOk());
    }

    private String evento() {
        return "evt-" + UUID.randomUUID();
    }
}
