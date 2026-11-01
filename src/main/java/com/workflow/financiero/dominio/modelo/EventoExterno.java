package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.estado.EstadoEventoExterno;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.temporal.Instantes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Evento recibido del proveedor de riesgo. Se guarda antes de intentar
 * correlacionarlo; las FKs de solicitud y revisión pueden quedar nulas
 * mientras el evento espera que exista la revisión.
 */
@Entity
@Table(name = "evento_externo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoExterno {

    private static final int LONGITUD_TEXTO = 1000;

    @Id
    @Column(name = "evenexte_id", nullable = false)
    private UUID id;

    @Column(name = "evenexte_proveedor", nullable = false, length = 50)
    private String proveedor;

    @Column(
        name = "evenexte_identificador_evento",
        nullable = false,
        length = 100
    )
    private String identificadorEvento;

    @Column(name = "evenexte_identificador_solicitud")
    private UUID identificadorSolicitud;

    @Column(name = "evenexte_numero_revision")
    private Integer numeroRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evenexte_solifina_id")
    private SolicitudFinanciera solicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evenexte_revisoli_id")
    private RevisionSolicitud revision;

    @Column(name = "evenexte_huella", nullable = false, length = 128)
    private String huella;

    @Enumerated(EnumType.STRING)
    @Column(name = "evenexte_estado", nullable = false, length = 30)
    private EstadoEventoExterno estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "evenexte_nivel_riesgo", length = 10)
    private NivelRiesgo nivelRiesgo;

    @Column(name = "evenexte_resultado", length = LONGITUD_TEXTO)
    private String resultado;

    @Column(name = "evenexte_fecha_proveedor")
    private Instant fechaProveedor;

    @Column(name = "evenexte_correlacion", length = 64)
    private String correlacion;

    @Column(name = "evenexte_intentos", nullable = false)
    private int intentos;

    @Column(name = "evenexte_proximo_intento_en")
    private Instant proximoIntentoEn;

    @Column(name = "evenexte_error", length = LONGITUD_TEXTO)
    private String error;

    @Column(name = "evenexte_procesado_en")
    private Instant procesadoEn;

    @Column(name = "evenexte_conflictos", nullable = false)
    private int conflictos;

    @Version
    @Column(name = "evenexte_version", nullable = false)
    private Long version;

    @Column(name = "evenexte_recibido_en", nullable = false)
    private Instant recibidoEn;

    @Column(name = "evenexte_actualizado_en")
    private Instant actualizadoEn;

    public EventoExterno(
        String proveedor,
        String identificadorEvento,
        UUID identificadorSolicitud,
        Integer numeroRevision,
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        String huella,
        NivelRiesgo nivelRiesgo,
        String resultado,
        Instant fechaProveedor,
        String correlacion
    ) {
        if (proveedor == null || proveedor.isBlank()
            || identificadorEvento == null || identificadorEvento.isBlank()
            || huella == null || huella.isBlank()) {
            throw new IllegalArgumentException(
                "El proveedor, el identificador y la huella son obligatorios");
        }
        this.id = UUID.randomUUID();
        this.proveedor = proveedor;
        this.identificadorEvento = identificadorEvento;
        this.identificadorSolicitud = identificadorSolicitud;
        this.numeroRevision = numeroRevision;
        this.solicitud = solicitud;
        this.revision = revision;
        this.huella = huella;
        this.estado = EstadoEventoExterno.PENDIENTE;
        this.nivelRiesgo = nivelRiesgo;
        this.resultado = limitar(resultado);
        this.fechaProveedor = fechaProveedor;
        this.correlacion = correlacion;
        this.intentos = 0;
        this.conflictos = 0;
        this.recibidoEn = Instantes.ahora();
        this.proximoIntentoEn = this.recibidoEn;
    }

    public void resolverSolicitud(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision
    ) {
        if (solicitud != null) {
            this.solicitud = solicitud;
        }
        if (revision != null) {
            this.revision = revision;
        }
    }

    public void marcarProcesado(Instant procesadoEn) {
        if (estado == EstadoEventoExterno.PROCESADO) {
            return;
        }
        this.estado = EstadoEventoExterno.PROCESADO;
        this.procesadoEn = procesadoEn;
        this.error = null;
        this.proximoIntentoEn = null;
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarObsoleto(String detalle, Instant procesadoEn) {
        if (estado == EstadoEventoExterno.PROCESADO) {
            return;
        }
        this.estado = EstadoEventoExterno.OBSOLETO;
        this.procesadoEn = procesadoEn;
        this.error = limitar(detalle);
        this.proximoIntentoEn = null;
        this.actualizadoEn = Instantes.ahora();
    }

    /**
     * Espera normal a que exista la revisión o la suscripción BPMN. No cuenta
     * como intento fallido: el evento puede esperar sin agotar reintentos.
     */
    public void programarEspera(String detalle, Instant proximoIntento) {
        if (estado != EstadoEventoExterno.PENDIENTE) {
            return;
        }
        this.error = limitar(detalle);
        this.proximoIntentoEn = proximoIntento;
        this.actualizadoEn = Instantes.ahora();
    }

    public void programarReintento(
        String error,
        Instant proximoIntento,
        int maximoIntentos
    ) {
        if (estado != EstadoEventoExterno.PENDIENTE) {
            return;
        }
        this.intentos++;
        this.error = limitar(error);
        this.proximoIntentoEn = intentos >= maximoIntentos
            ? null
            : proximoIntento;
        this.actualizadoEn = Instantes.ahora();
    }

    public void registrarConflicto(String detalle) {
        this.conflictos++;
        this.error = limitar(detalle);
        this.actualizadoEn = Instantes.ahora();
    }

    public boolean estaPendiente() {
        return estado == EstadoEventoExterno.PENDIENTE;
    }

    private String limitar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= LONGITUD_TEXTO
            ? texto
            : texto.substring(0, LONGITUD_TEXTO);
    }
}
