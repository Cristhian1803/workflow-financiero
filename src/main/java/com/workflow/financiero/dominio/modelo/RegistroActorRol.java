package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.temporal.Instantes;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Rol funcional que un actor ya aprobó en una solicitud. La clave impide que
 * el mismo actor apruebe roles distintos dentro de la misma solicitud.
 */
@Entity
@Table(name = "registro_actor_rol")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistroActorRol {

    @EmbeddedId
    private RegistroActorRolId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("solicitudId")
    @JoinColumn(name = "regiactorol_solifina_id", nullable = false)
    private SolicitudFinanciera solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("usuarioId")
    @JoinColumn(name = "regiactorol_usua_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regiactorol_rol_codigo", nullable = false)
    private Rol rol;

    @Column(name = "regiactorol_fecha", nullable = false)
    private Instant fecha;

    public RegistroActorRol(SolicitudFinanciera solicitud, Usuario usuario, Rol rol) {
        this.id = new RegistroActorRolId(solicitud.getId(), usuario.getId());
        this.solicitud = solicitud;
        this.usuario = usuario;
        this.rol = rol;
        this.fecha = Instantes.ahora();
    }
}
