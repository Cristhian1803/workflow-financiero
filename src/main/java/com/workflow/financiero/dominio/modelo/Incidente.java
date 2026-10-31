package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.estado.EstadoIncidente;
import com.workflow.financiero.dominio.estado.TipoIncidente;
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
 * Incidente operativo asociado a una causa concreta: una intención de
 * ejecución, una compensación o un evento externo. La referencia y el tipo
 * identifican la causa para no duplicar el incidente.
 */
@Entity
@Table(name = "incidente")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Incidente {

    private static final int LONGITUD_TEXTO = 1000;

    @Id
    @Column(name = "inci_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inci_solifina_id", nullable = false)
    private SolicitudFinanciera solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inci_revisoli_id", nullable = false)
    private RevisionSolicitud revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "inci_tipo", nullable = false, length = 30)
    private TipoIncidente tipo;

    @Column(name = "inci_referencia", nullable = false)
    private UUID referencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "inci_estado", nullable = false, length = 20)
    private EstadoIncidente estado;

    @Column(name = "inci_motivo", nullable = false, length = 1000)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inci_actor_usua_id")
    private Usuario actor;

    @Column(name = "inci_reintentable", nullable = false)
    private boolean reintentable;

    @Column(name = "inci_reintentos", nullable = false)
    private int reintentos;

    @Column(name = "inci_ultimo_resultado", length = 1000)
    private String ultimoResultado;

    @Version
    @Column(name = "inci_version", nullable = false)
    private Long version;

    @Column(name = "inci_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "inci_actualizado_en")
    private Instant actualizadoEn;

    public Incidente(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        TipoIncidente tipo,
        UUID referencia,
        String motivo,
        boolean reintentable
    ) {
        if (solicitud == null || revision == null || tipo == null
            || referencia == null || motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException(
                "La solicitud, la revisión, el tipo, la referencia y el motivo son obligatorios");
        }
        if (!revision.getSolicitud().getId().equals(solicitud.getId())) {
            throw new IllegalArgumentException(
                "La revisión no pertenece a la solicitud del incidente");
        }
        this.id = UUID.randomUUID();
        this.solicitud = solicitud;
        this.revision = revision;
        this.tipo = tipo;
        this.referencia = referencia;
        this.estado = EstadoIncidente.ABIERTO;
        this.motivo = limitar(motivo);
        this.reintentable = reintentable;
        this.reintentos = 0;
        this.creadoEn = Instantes.ahora();
    }

    public void actualizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        this.motivo = limitar(motivo);
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarReintentando(Usuario actor, String motivo) {
        if (estado != EstadoIncidente.ABIERTO) {
            throw new IllegalStateException(
                "El incidente no está abierto: " + estado);
        }
        if (!reintentable) {
            throw new IllegalStateException(
                "El incidente no admite reintento");
        }
        this.estado = EstadoIncidente.REINTENTANDO;
        this.actor = actor;
        this.reintentos++;
        this.motivo = limitar(motivo);
        this.actualizadoEn = Instantes.ahora();
    }

    public void registrarResultado(String resultado) {
        this.ultimoResultado = limitar(resultado);
        this.actualizadoEn = Instantes.ahora();
    }

    public void reabrir(String resultado) {
        this.estado = EstadoIncidente.ABIERTO;
        this.ultimoResultado = limitar(resultado);
        this.actualizadoEn = Instantes.ahora();
    }

    public void resolver(Usuario actor, String resultado) {
        if (estado == EstadoIncidente.RESUELTO
            || estado == EstadoIncidente.CERRADO) {
            return;
        }
        this.estado = EstadoIncidente.RESUELTO;
        if (actor != null) {
            this.actor = actor;
        }
        this.ultimoResultado = limitar(resultado);
        this.actualizadoEn = Instantes.ahora();
    }

    public void cerrar(Usuario actor, String motivo) {
        if (estado == EstadoIncidente.CERRADO) {
            return;
        }
        this.estado = EstadoIncidente.CERRADO;
        this.actor = actor;
        this.motivo = limitar(motivo);
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarNoReintentable(String motivo) {
        this.reintentable = false;
        this.motivo = limitar(motivo);
        this.actualizadoEn = Instantes.ahora();
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
