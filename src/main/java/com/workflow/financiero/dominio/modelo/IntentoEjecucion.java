package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.estado.EstadoIntentoEjecucion;
import com.workflow.financiero.dominio.temporal.Instantes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Intención financiera durable de una revisión. La clave y la huella se
 * calculan una sola vez y se conservan durante todo el ciclo de reintento y
 * reconciliación.
 */
@Entity
@Table(name = "intento_ejecucion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntentoEjecucion {

    @Id
    @Column(name = "inteejec_id", nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inteejec_revisoli_id", nullable = false, unique = true)
    private RevisionSolicitud revision;

    @Column(name = "inteejec_clave", nullable = false, length = 100)
    private String clave;

    @Column(name = "inteejec_huella", nullable = false, length = 128)
    private String huella;

    @Enumerated(EnumType.STRING)
    @Column(name = "inteejec_estado", nullable = false, length = 30)
    private EstadoIntentoEjecucion estado;

    @Column(name = "inteejec_referencia_externa", length = 100)
    private String referenciaExterna;

    @Column(name = "inteejec_intentos", nullable = false)
    private int intentos;

    @Column(name = "inteejec_reclamado_en")
    private Instant reclamadoEn;

    @Column(name = "inteejec_reclamado_por", length = 100)
    private String reclamadoPor;

    @Column(name = "inteejec_ultimo_error", length = 1000)
    private String ultimoError;

    @Column(name = "inteejec_proximo_intento_en")
    private Instant proximoIntentoEn;

    @Column(name = "inteejec_reserva_referencia", length = 100)
    private String reservaReferencia;

    @Column(name = "inteejec_confirmacion_referencia", length = 100)
    private String confirmacionReferencia;

    @Version
    @Column(name = "inteejec_version", nullable = false)
    private Long version;

    @Column(name = "inteejec_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "inteejec_actualizado_en")
    private Instant actualizadoEn;

    public IntentoEjecucion(
        RevisionSolicitud revision,
        String clave,
        String huella
    ) {
        if (revision == null || clave == null || clave.isBlank()
            || huella == null || huella.isBlank()) {
            throw new IllegalArgumentException(
                "La revisión, la clave y la huella son obligatorias");
        }
        this.id = UUID.randomUUID();
        this.revision = revision;
        this.clave = clave;
        this.huella = huella;
        this.estado = EstadoIntentoEjecucion.PENDIENTE;
        this.intentos = 0;
        this.creadoEn = Instantes.ahora();
    }

    public void reclamar(String trabajador) {
        if (estado != EstadoIntentoEjecucion.PENDIENTE) {
            throw new IllegalStateException(
                "La intención no está pendiente: " + estado);
        }
        if (trabajador == null || trabajador.isBlank()) {
            throw new IllegalArgumentException(
                "El trabajador que reclama es obligatorio");
        }
        this.estado = EstadoIntentoEjecucion.ENVIANDO;
        this.intentos++;
        this.reclamadoEn = Instantes.ahora();
        this.reclamadoPor = trabajador;
        this.actualizadoEn = this.reclamadoEn;
    }

    public void confirmar(
        String reservaReferencia,
        String confirmacionReferencia,
        String referenciaExterna
    ) {
        if (estado != EstadoIntentoEjecucion.ENVIANDO) {
            throw new IllegalStateException(
                "La intención no está en envío: " + estado);
        }
        this.estado = EstadoIntentoEjecucion.CONFIRMADA;
        this.reservaReferencia = reservaReferencia;
        this.confirmacionReferencia = confirmacionReferencia;
        this.referenciaExterna = referenciaExterna;
        this.actualizadoEn = Instantes.ahora();
    }
}
