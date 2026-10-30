package com.workflow.financiero.dominio.evento;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.Usuario;
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
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Evento del historial de una solicitud. Es inmutable: se agrega y no se
 * modifica ni se elimina desde la aplicación.
 */
@Entity
@Table(name = "evento_solicitud")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoSolicitud {

    @Id
    @Column(name = "evensoli_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evensoli_solifina_id", nullable = false)
    private SolicitudFinanciera solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evensoli_revisoli_id", nullable = false)
    private RevisionSolicitud revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "evensoli_tipo", length = 50, nullable = false)
    private TipoEventoSolicitud tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evensoli_actor_usua_id")
    private Usuario actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evensoli_rol_codigo")
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(name = "evensoli_estado_anterior", length = 30)
    private EstadoRevision estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "evensoli_estado_nuevo", length = 30)
    private EstadoRevision estadoNuevo;

    @Column(name = "evensoli_correlacion", length = 64)
    private String correlacion;

    @Column(name = "evensoli_detalle", length = 2000)
    private String detalle;

    @Column(name = "evensoli_creado_en", nullable = false)
    private Instant creadoEn;

    public EventoSolicitud(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        TipoEventoSolicitud tipo,
        Usuario actor,
        Rol rol,
        EstadoRevision estadoAnterior,
        EstadoRevision estadoNuevo,
        String correlacion,
        String detalle
    ) {
        this.id = UUID.randomUUID();
        this.solicitud = solicitud;
        this.revision = revision;
        this.tipo = tipo;
        this.actor = actor;
        this.rol = rol;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.correlacion = correlacion;
        this.detalle = detalle;
        this.creadoEn = Instantes.ahora();
    }
}
