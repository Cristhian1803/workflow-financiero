package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.estado.EstadoTareaRevisionRiesgo;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.excepcion.RevisionRiesgoYaResueltaException;
import com.workflow.financiero.dominio.excepcion.TransicionTareaInvalidaException;
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
 * Tarea humana que resuelve el riesgo cuando la evaluación externa no llega.
 * Solo existe una tarea por revisión y no sustituye ninguna aprobación.
 */
@Entity
@Table(name = "tarea_revision_riesgo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TareaRevisionRiesgo {

    private static final int LONGITUD_TEXTO = 1000;

    @Id
    @Column(name = "tarereviries_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tarereviries_revisoli_id", nullable = false)
    private RevisionSolicitud revision;

    @Column(name = "tarereviries_identificador_tarea_motor", length = 64)
    private String identificadorTareaMotor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tarereviries_estado", length = 20, nullable = false)
    private EstadoTareaRevisionRiesgo estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tarereviries_nivel", length = 10)
    private NivelRiesgo nivel;

    @Column(name = "tarereviries_motivo", length = LONGITUD_TEXTO)
    private String motivo;

    @Column(name = "tarereviries_evidencia", length = LONGITUD_TEXTO)
    private String evidencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarereviries_actor_usua_id")
    private Usuario actor;

    @Version
    @Column(name = "tarereviries_version", nullable = false)
    private Long version;

    @Column(name = "tarereviries_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "tarereviries_actualizado_en")
    private Instant actualizadoEn;

    @Column(name = "tarereviries_resuelto_en")
    private Instant resueltoEn;

    public TareaRevisionRiesgo(RevisionSolicitud revision) {
        if (revision == null) {
            throw new IllegalArgumentException("La revisión es obligatoria");
        }
        this.id = UUID.randomUUID();
        this.revision = revision;
        this.estado = EstadoTareaRevisionRiesgo.PENDIENTE;
        this.creadoEn = Instantes.ahora();
    }

    public void asignarIdentificadorMotor(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(
                "El identificador de la tarea del motor es obligatorio");
        }
        if (estado != EstadoTareaRevisionRiesgo.PENDIENTE) {
            throw new TransicionTareaInvalidaException(
                "No se puede asociar una tarea en estado " + estado);
        }
        if (this.identificadorTareaMotor != null
            && !this.identificadorTareaMotor.equals(identificador)) {
            throw new IllegalStateException(
                "La tarea ya tiene otro identificador de motor");
        }
        this.identificadorTareaMotor = identificador;
        this.actualizadoEn = Instantes.ahora();
    }

    public void resolver(
        Usuario actor,
        NivelRiesgo nivel,
        String motivo,
        String evidencia
    ) {
        if (estado != EstadoTareaRevisionRiesgo.PENDIENTE) {
            throw new RevisionRiesgoYaResueltaException(
                "La tarea de revisión ya está en estado " + estado);
        }
        if (actor == null || nivel == null
            || motivo == null || motivo.isBlank()
            || evidencia == null || evidencia.isBlank()) {
            throw new IllegalArgumentException(
                "El actor, el nivel, el motivo y la evidencia son obligatorios");
        }
        this.estado = EstadoTareaRevisionRiesgo.RESUELTA;
        this.actor = actor;
        this.nivel = nivel;
        this.motivo = limitar(motivo.trim());
        this.evidencia = limitar(evidencia.trim());
        this.resueltoEn = Instantes.ahora();
        this.actualizadoEn = this.resueltoEn;
    }

    public void cancelar(String detalle) {
        if (estado != EstadoTareaRevisionRiesgo.PENDIENTE) {
            return;
        }
        this.estado = EstadoTareaRevisionRiesgo.CANCELADA;
        this.motivo = limitar(detalle);
        this.actualizadoEn = Instantes.ahora();
    }

    public boolean estaPendiente() {
        return estado == EstadoTareaRevisionRiesgo.PENDIENTE;
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
