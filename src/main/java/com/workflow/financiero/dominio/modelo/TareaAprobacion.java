package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.excepcion.RolAprobadorInvalidoException;
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
 * Tarea de aprobación de un rol funcional para una revisión. La clave única
 * de la tabla impide más de una tarea por revisión y rol.
 */
@Entity
@Table(name = "tarea_aprobacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TareaAprobacion {

    @Id
    @Column(name = "tareapro_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tareapro_revisoli_id", nullable = false)
    private RevisionSolicitud revision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tareapro_rol_codigo", nullable = false)
    private Rol rol;

    @Column(name = "tareapro_identificador_tarea_motor", length = 64)
    private String identificadorTareaMotor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tareapro_asignado_usua_id")
    private Usuario asignado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tareapro_estado", length = 20, nullable = false)
    private EstadoTareaAprobacion estado;

    @Column(name = "tareapro_fecha_vencimiento")
    private Instant fechaVencimiento;

    @Version
    @Column(name = "tareapro_version", nullable = false)
    private Long version;

    @Column(name = "tareapro_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "tareapro_actualizado_en")
    private Instant actualizadoEn;

    public TareaAprobacion(RevisionSolicitud revision, Rol rol) {
        if (revision == null) {
            throw new IllegalArgumentException("La revisión es obligatoria");
        }
        if (rol == null) {
            throw new IllegalArgumentException("El rol es obligatorio");
        }
        if (!rol.isAprobador() || !rol.isActivo()) {
            throw new RolAprobadorInvalidoException(
                "El rol " + rol.getCodigo()
                    + " no es un rol funcional de aprobación activo");
        }

        this.id = UUID.randomUUID();
        this.revision = revision;
        this.rol = rol;
        this.estado = EstadoTareaAprobacion.PENDIENTE;
        this.creadoEn = Instantes.ahora();
    }

    public void marcarAprobada() {
        cambiarEstado(EstadoTareaAprobacion.APROBADA);
    }

    public void marcarRechazada() {
        cambiarEstado(EstadoTareaAprobacion.RECHAZADA);
    }

    public void cancelar() {
        cambiarEstado(EstadoTareaAprobacion.CANCELADA);
    }

    public void reasignar(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("El usuario asignado es obligatorio");
        }
        if (estado != EstadoTareaAprobacion.PENDIENTE) {
            throw new TransicionTareaInvalidaException(
                "No se puede reasignar una tarea en estado " + estado);
        }

        this.asignado = usuario;
        this.actualizadoEn = Instantes.ahora();
    }

    public void asignarIdentificadorMotor(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(
                "El identificador de la tarea del motor es obligatorio");
        }
        if (estado != EstadoTareaAprobacion.PENDIENTE) {
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

    public void programarVencimiento(Instant fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException(
                "La fecha de vencimiento es obligatoria");
        }
        if (estado != EstadoTareaAprobacion.PENDIENTE) {
            throw new TransicionTareaInvalidaException(
                "No se puede programar una tarea en estado " + estado);
        }

        this.fechaVencimiento = fecha;
        this.actualizadoEn = Instantes.ahora();
    }

    private void cambiarEstado(EstadoTareaAprobacion nuevoEstado) {
        if (estado != EstadoTareaAprobacion.PENDIENTE) {
            throw new TransicionTareaInvalidaException(
                "No se puede pasar la tarea de " + estado + " a " + nuevoEstado);
        }

        this.estado = nuevoEstado;
        this.actualizadoEn = Instantes.ahora();
    }
}
