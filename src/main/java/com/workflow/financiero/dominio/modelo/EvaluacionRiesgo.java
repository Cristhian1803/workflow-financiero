package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.estado.EstadoEvaluacionRiesgo;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.regla.riesgo.OrigenRiesgo;
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
 * Evaluación de riesgo de una revisión. La evaluación externa se crea como
 * intención PENDIENTE antes de pedirla al proveedor y conserva su identidad
 * durante todos los reintentos.
 */
@Entity
@Table(name = "evaluacion_riesgo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluacionRiesgo {

    private static final int LONGITUD_TEXTO = 1000;

    @Id
    @Column(name = "evalries_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evalries_revisoli_id", nullable = false)
    private RevisionSolicitud revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "evalries_origen", nullable = false, length = 20)
    private OrigenRiesgo origen;

    @Enumerated(EnumType.STRING)
    @Column(name = "evalries_nivel", length = 10)
    private NivelRiesgo nivel;

    @Enumerated(EnumType.STRING)
    @Column(name = "evalries_estado", nullable = false, length = 20)
    private EstadoEvaluacionRiesgo estado;

    @Column(name = "evalries_clave", length = 100)
    private String clave;

    @Column(name = "evalries_proveedor", length = 50)
    private String proveedor;

    @Column(name = "evalries_identificador_evento", length = 100)
    private String identificadorEvento;

    @Column(name = "evalries_referencia_solicitud", length = 100)
    private String referenciaSolicitud;

    @Column(name = "evalries_resultado", length = LONGITUD_TEXTO)
    private String resultado;

    @Column(name = "evalries_correlacion", length = 64)
    private String correlacion;

    @Column(name = "evalries_intentos", nullable = false)
    private int intentos;

    @Column(name = "evalries_proximo_intento_en")
    private Instant proximoIntentoEn;

    @Column(name = "evalries_reclamado_en")
    private Instant reclamadoEn;

    @Column(name = "evalries_reclamado_por", length = 100)
    private String reclamadoPor;

    @Column(name = "evalries_error", length = LONGITUD_TEXTO)
    private String error;

    @Column(name = "evalries_procesado_en")
    private Instant procesadoEn;

    @Version
    @Column(name = "evalries_version", nullable = false)
    private Long version;

    @Column(name = "evalries_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "evalries_actualizado_en")
    private Instant actualizadoEn;

    public EvaluacionRiesgo(
        RevisionSolicitud revision,
        OrigenRiesgo origen,
        EstadoEvaluacionRiesgo estado,
        String clave,
        String correlacion,
        String proveedor
    ) {
        if (revision == null || origen == null || estado == null) {
            throw new IllegalArgumentException(
                "La revisión, el origen y el estado son obligatorios");
        }
        this.id = UUID.randomUUID();
        this.revision = revision;
        this.origen = origen;
        this.estado = estado;
        this.clave = clave;
        this.correlacion = correlacion;
        this.proveedor = proveedor;
        this.intentos = 0;
        this.creadoEn = Instantes.ahora();
    }

    public boolean estaVigente() {
        return estado == EstadoEvaluacionRiesgo.PENDIENTE
            || estado == EstadoEvaluacionRiesgo.RECIBIDA;
    }

    public boolean estaPendiente() {
        return estado == EstadoEvaluacionRiesgo.PENDIENTE;
    }

    public void reclamar(String trabajador) {
        if (estado != EstadoEvaluacionRiesgo.PENDIENTE) {
            throw new IllegalStateException(
                "La evaluación no está pendiente: " + estado);
        }
        if (trabajador == null || trabajador.isBlank()) {
            throw new IllegalArgumentException(
                "El trabajador que reclama es obligatorio");
        }
        this.intentos++;
        this.reclamadoEn = Instantes.ahora();
        this.reclamadoPor = trabajador;
        this.proximoIntentoEn = null;
        this.actualizadoEn = this.reclamadoEn;
    }

    public void registrarEnvio(String referencia) {
        if (estado != EstadoEvaluacionRiesgo.PENDIENTE) {
            return;
        }
        this.referenciaSolicitud = referencia;
        this.error = null;
        this.reclamadoEn = null;
        this.reclamadoPor = null;
        this.proximoIntentoEn = null;
        this.actualizadoEn = Instantes.ahora();
    }

    public void registrarFallo(
        String error,
        Instant proximoIntento,
        int maximoIntentos
    ) {
        if (estado != EstadoEvaluacionRiesgo.PENDIENTE) {
            return;
        }
        this.error = limitar(error);
        this.reclamadoEn = null;
        this.reclamadoPor = null;
        this.proximoIntentoEn = intentos >= maximoIntentos
            ? null
            : proximoIntento;
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarRecibida(
        NivelRiesgo nivel,
        String resultado,
        String identificadorEvento,
        String proveedor,
        Instant procesadoEn
    ) {
        if (estado == EstadoEvaluacionRiesgo.OBSOLETA
            || estado == EstadoEvaluacionRiesgo.VENCIDA
            || estado == EstadoEvaluacionRiesgo.MANUAL) {
            throw new IllegalStateException(
                "La evaluación ya no admite un resultado: " + estado);
        }
        if (nivel == null) {
            throw new IllegalArgumentException(
                "El nivel de riesgo externo es obligatorio");
        }
        this.estado = EstadoEvaluacionRiesgo.RECIBIDA;
        this.nivel = nivel;
        this.resultado = limitar(resultado);
        this.identificadorEvento = identificadorEvento;
        this.proveedor = proveedor;
        this.procesadoEn = procesadoEn;
        this.error = null;
        this.reclamadoEn = null;
        this.reclamadoPor = null;
        this.proximoIntentoEn = null;
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarObsoleta(String detalle) {
        if (estado == EstadoEvaluacionRiesgo.RECIBIDA
            || estado == EstadoEvaluacionRiesgo.OBSOLETA) {
            return;
        }
        this.estado = EstadoEvaluacionRiesgo.OBSOLETA;
        this.error = limitar(detalle);
        this.proximoIntentoEn = null;
        this.reclamadoEn = null;
        this.reclamadoPor = null;
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
