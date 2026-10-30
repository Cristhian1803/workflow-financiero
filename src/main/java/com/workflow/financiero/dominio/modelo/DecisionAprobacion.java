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
import org.hibernate.annotations.Immutable;

/**
 * Decisión tomada sobre una tarea de aprobación. La tabla permite una única
 * decisión por tarea y la entidad no se modifica después de crearse.
 */
@Entity
@Table(name = "decision_aprobacion")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecisionAprobacion {

    private static final int LONGITUD_MAXIMA_MOTIVO = 1000;

    @Id
    @Column(name = "deciapro_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deciapro_tareapro_id", nullable = false)
    private TareaAprobacion tarea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deciapro_actor_usua_id", nullable = false)
    private Usuario actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "deciapro_decision", length = 10, nullable = false)
    private TipoDecision decision;

    @Column(name = "deciapro_motivo", length = LONGITUD_MAXIMA_MOTIVO)
    private String motivo;

    @Column(name = "deciapro_delegado", nullable = false)
    private boolean delegado;

    @Column(name = "deciapro_fecha", nullable = false)
    private Instant fecha;

    public DecisionAprobacion(
        TareaAprobacion tarea,
        Usuario actor,
        TipoDecision decision,
        String motivo,
        boolean delegado
    ) {
        if (tarea == null) {
            throw new IllegalArgumentException("La tarea es obligatoria");
        }
        if (actor == null) {
            throw new IllegalArgumentException("El actor es obligatorio");
        }
        if (decision == null) {
            throw new IllegalArgumentException("La decisión es obligatoria");
        }
        if (motivo != null && motivo.length() > LONGITUD_MAXIMA_MOTIVO) {
            throw new IllegalArgumentException(
                "El motivo no puede superar los " + LONGITUD_MAXIMA_MOTIVO + " caracteres");
        }

        this.id = UUID.randomUUID();
        this.tarea = tarea;
        this.actor = actor;
        this.decision = decision;
        this.motivo = motivo;
        this.delegado = delegado;
        this.fecha = Instantes.ahora();
    }
}
