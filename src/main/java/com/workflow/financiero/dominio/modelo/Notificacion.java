package com.workflow.financiero.dominio.modelo;

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

/**
 * Aviso persistido para un usuario o un rol funcional. El destinatario puede
 * ser directo o colectivo, pero nunca puede faltar.
 */
@Entity
@Table(name = "notificacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notificacion {

    @Id
    @Column(name = "noti_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "noti_tareapro_id")
    private TareaAprobacion tarea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "noti_destinatario_usua_id")
    private Usuario destinatarioUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "noti_destinatario_rol_codigo")
    private Rol destinatarioRol;

    @Enumerated(EnumType.STRING)
    @Column(name = "noti_tipo", length = 30, nullable = false)
    private TipoNotificacion tipo;

    @Column(name = "noti_contenido", length = 1000, nullable = false)
    private String contenido;

    @Enumerated(EnumType.STRING)
    @Column(name = "noti_estado", length = 20, nullable = false)
    private EstadoNotificacion estado;

    @Column(name = "noti_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "noti_leida_en")
    private Instant leidaEn;

    public Notificacion(
        TareaAprobacion tarea,
        Usuario destinatarioUsuario,
        Rol destinatarioRol,
        TipoNotificacion tipo,
        String contenido
    ) {
        if (tipo == null) {
            throw new IllegalArgumentException(
                "El tipo de notificación es obligatorio");
        }
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException(
                "El contenido de la notificación es obligatorio");
        }
        if (destinatarioUsuario == null && destinatarioRol == null) {
            throw new IllegalArgumentException(
                "La notificación debe tener al menos un destinatario");
        }

        this.id = UUID.randomUUID();
        this.tarea = tarea;
        this.destinatarioUsuario = destinatarioUsuario;
        this.destinatarioRol = destinatarioRol;
        this.tipo = tipo;
        this.contenido = contenido;
        this.estado = EstadoNotificacion.PENDIENTE;
        this.creadoEn = Instantes.ahora();
    }
}
