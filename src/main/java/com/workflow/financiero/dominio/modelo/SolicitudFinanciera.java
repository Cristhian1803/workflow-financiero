package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.temporal.Instantes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Identidad estable de una operación financiera. La revisión actual es la
 * fuente de verdad del estado y de los datos vigentes.
 */
@Entity
@Table(name = "solicitud_financiera")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitudFinanciera {

    @Id
    @Column(name = "solifina_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solifina_solicitante_usua_id", nullable = false)
    private Usuario solicitante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solifina_revisoli_actual_id", unique = true)
    private RevisionSolicitud revisionActual;

    @Version
    @Column(name = "solifina_version", nullable = false)
    private Long version;

    @Column(name = "solifina_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "solifina_actualizado_en")
    private Instant actualizadoEn;

    public SolicitudFinanciera(Usuario solicitante) {
        this.id = UUID.randomUUID();
        this.solicitante = solicitante;
        this.creadoEn = Instantes.ahora();
    }

    public void establecerRevisionActual(RevisionSolicitud revision) {
        this.revisionActual = revision;
        this.actualizadoEn = Instantes.ahora();
    }
}
