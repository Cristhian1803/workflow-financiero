package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Datos y ciclo de vida de un envío de la solicitud. Cada reenvío crea otra
 * revisión y conserva la anterior.
 */
@Entity
@Table(name = "revision_solicitud")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisionSolicitud {

    @Id
    @Column(name = "revisoli_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revisoli_solifina_id", nullable = false)
    private SolicitudFinanciera solicitud;

    @Column(name = "revisoli_numero", nullable = false)
    private int numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revisoli_tipooper_codigo", nullable = false)
    private TipoOperacion tipoOperacion;

    @Column(name = "revisoli_monto", nullable = false, precision = 19, scale = 0)
    private BigDecimal monto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revisoli_mone_codigo", nullable = false)
    private Moneda moneda;

    @Column(name = "revisoli_descripcion", length = 500, nullable = false)
    private String descripcion;

    @Column(name = "revisoli_justificacion", length = 1000, nullable = false)
    private String justificacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "revisoli_nivel_riesgo", length = 10)
    private NivelRiesgo nivelRiesgo;

    @Enumerated(EnumType.STRING)
    @Column(name = "revisoli_estado", length = 30, nullable = false)
    private EstadoRevision estado;

    @Column(name = "revisoli_identificador_proceso", length = 64)
    private String identificadorProceso;

    @Version
    @Column(name = "revisoli_version", nullable = false)
    private Long version;

    @Column(name = "revisoli_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "revisoli_actualizado_en")
    private Instant actualizadoEn;

    public RevisionSolicitud(
        SolicitudFinanciera solicitud,
        int numero,
        TipoOperacion tipoOperacion,
        BigDecimal monto,
        Moneda moneda,
        String descripcion,
        String justificacion
    ) {
        this.id = UUID.randomUUID();
        this.solicitud = solicitud;
        this.numero = numero;
        this.tipoOperacion = tipoOperacion;
        this.monto = monto;
        this.moneda = moneda;
        this.descripcion = descripcion;
        this.justificacion = justificacion;
        this.estado = EstadoRevision.RECIBIDA;
        this.creadoEn = Instantes.ahora();
    }

    public void cambiarEstado(EstadoRevision nuevoEstado) {
        if (nuevoEstado == null) {
            throw new TransicionEstadoInvalidaException("El estado nuevo es obligatorio");
        }
        if (!estado.puedeTransicionarA(nuevoEstado)) {
            throw new TransicionEstadoInvalidaException(
                "No se puede pasar de " + estado + " a " + nuevoEstado);
        }

        this.estado = nuevoEstado;
        this.actualizadoEn = Instantes.ahora();
    }

    public void registrarNivelRiesgo(NivelRiesgo nivel) {
        if (nivel == null) {
            throw new IllegalArgumentException("El nivel de riesgo es obligatorio");
        }
        if (estado != EstadoRevision.VALIDANDO && estado != EstadoRevision.EVALUANDO_RIESGO) {
            throw new TransicionEstadoInvalidaException(
                "No se puede registrar el riesgo en estado " + estado);
        }

        this.nivelRiesgo = nivel;
        this.actualizadoEn = Instantes.ahora();
    }
}
