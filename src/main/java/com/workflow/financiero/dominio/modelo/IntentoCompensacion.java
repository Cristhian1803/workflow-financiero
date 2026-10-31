package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.estado.EstadoIntentoCompensacion;
import com.workflow.financiero.dominio.temporal.Instantes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Intención durable de compensar una revisión. La clave y la huella de
 * liberación se calculan una sola vez y se conservan en todos los reintentos.
 */
@Entity
@Table(name = "intento_compensacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntentoCompensacion {

    private static final int LONGITUD_TEXTO = 1000;

    @Id
    @Column(name = "intecomp_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intecomp_solifina_id", nullable = false)
    private SolicitudFinanciera solicitud;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intecomp_revisoli_id", nullable = false, unique = true)
    private RevisionSolicitud revision;

    @Column(name = "intecomp_clave", nullable = false, length = 100)
    private String clave;

    @Column(name = "intecomp_huella", nullable = false, length = 128)
    private String huella;

    @Enumerated(EnumType.STRING)
    @Column(name = "intecomp_estado", nullable = false, length = 30)
    private EstadoIntentoCompensacion estado;

    @Column(name = "intecomp_despacho_en_curso", nullable = false)
    private boolean despachoEnCurso;

    @Column(name = "intecomp_motivo", nullable = false, length = LONGITUD_TEXTO)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intecomp_actor_usua_id", nullable = false)
    private Usuario actor;

    @Column(name = "intecomp_referencia", length = 100)
    private String referencia;

    @Column(name = "intecomp_ultimo_error", length = LONGITUD_TEXTO)
    private String ultimoError;

    @Column(name = "intecomp_intentos", nullable = false)
    private int intentos;

    @Column(name = "intecomp_proximo_intento_en")
    private Instant proximoIntentoEn;

    @Column(name = "intecomp_reclamado_en")
    private Instant reclamadoEn;

    @Column(name = "intecomp_reclamado_por", length = 100)
    private String reclamadoPor;

    @Version
    @Column(name = "intecomp_version", nullable = false)
    private Long version;

    @Column(name = "intecomp_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "intecomp_actualizado_en")
    private Instant actualizadoEn;

    public IntentoCompensacion(
        RevisionSolicitud revision,
        String clave,
        String huella,
        boolean despachoEnCurso,
        Usuario actor,
        String motivo
    ) {
        if (revision == null || clave == null || clave.isBlank()
            || huella == null || huella.isBlank() || actor == null) {
            throw new IllegalArgumentException(
                "La revisión, la clave, la huella y el actor son obligatorios");
        }
        this.id = UUID.randomUUID();
        this.solicitud = revision.getSolicitud();
        this.revision = revision;
        this.clave = clave;
        this.huella = huella;
        this.estado = EstadoIntentoCompensacion.PENDIENTE;
        this.despachoEnCurso = despachoEnCurso;
        this.motivo = limitar(motivo);
        this.actor = actor;
        this.intentos = 0;
        this.creadoEn = Instantes.ahora();
    }

    public void reclamar(String trabajador) {
        if (estado != EstadoIntentoCompensacion.PENDIENTE) {
            throw new IllegalStateException(
                "La compensación no está pendiente: " + estado);
        }
        exigirTrabajador(trabajador);
        this.estado = EstadoIntentoCompensacion.LIBERANDO;
        this.intentos++;
        this.reclamadoEn = Instantes.ahora();
        this.reclamadoPor = trabajador;
        this.proximoIntentoEn = null;
        this.actualizadoEn = this.reclamadoEn;
    }

    public void reclamarRecuperacion(String trabajador) {
        if (estado != EstadoIntentoCompensacion.LIBERANDO) {
            throw new IllegalStateException(
                "La compensación no es recuperable: " + estado);
        }
        exigirTrabajador(trabajador);
        this.intentos++;
        this.reclamadoEn = Instantes.ahora();
        this.reclamadoPor = trabajador;
        this.proximoIntentoEn = null;
        this.actualizadoEn = this.reclamadoEn;
    }

    /**
     * Habilita un reintento manual autorizado desde un incidente. No cambia
     * la clave ni la huella de liberación.
     */
    public void reintentarManual(String trabajador) {
        if (estado != EstadoIntentoCompensacion.FALLIDA
            && estado != EstadoIntentoCompensacion.REVISION_MANUAL
            && estado != EstadoIntentoCompensacion.PENDIENTE) {
            throw new IllegalStateException(
                "La compensación no admite reintento manual: " + estado);
        }
        exigirTrabajador(trabajador);
        this.estado = EstadoIntentoCompensacion.LIBERANDO;
        this.intentos++;
        this.reclamadoEn = Instantes.ahora();
        this.reclamadoPor = trabajador;
        this.proximoIntentoEn = null;
        this.actualizadoEn = this.reclamadoEn;
    }

    /**
     * El trabajador de ejecución abortó el despacho antes de producir un
     * efecto. A partir de aquí se puede declarar cancelación sin reserva.
     */
    public void marcarDespachoAbortado(String detalle) {
        if (esTerminal()) {
            return;
        }
        this.despachoEnCurso = false;
        this.ultimoError = limitar(detalle);
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarPendiente(String error, Instant proximoIntento) {
        if (esTerminal()) {
            return;
        }
        this.estado = EstadoIntentoCompensacion.PENDIENTE;
        this.ultimoError = limitar(error);
        this.proximoIntentoEn = proximoIntento;
        this.reclamadoEn = null;
        this.reclamadoPor = null;
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarLiberada(String referencia) {
        if (esTerminal()) {
            return;
        }
        this.estado = EstadoIntentoCompensacion.LIBERADA;
        this.referencia = referencia;
        this.ultimoError = null;
        this.proximoIntentoEn = null;
        this.despachoEnCurso = false;
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarCanceladaSinReserva(String detalle) {
        if (esTerminal()) {
            return;
        }
        this.estado = EstadoIntentoCompensacion.CANCELADA;
        this.ultimoError = limitar(detalle);
        this.proximoIntentoEn = null;
        this.despachoEnCurso = false;
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarRechazada(String detalle) {
        if (esTerminal()) {
            return;
        }
        this.estado = EstadoIntentoCompensacion.RECHAZADA;
        this.ultimoError = limitar(detalle);
        this.proximoIntentoEn = null;
        this.despachoEnCurso = false;
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarFallida(String error) {
        if (esTerminal()) {
            return;
        }
        this.estado = EstadoIntentoCompensacion.FALLIDA;
        this.ultimoError = limitar(error);
        this.proximoIntentoEn = null;
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarRevisionManual(String error) {
        if (esTerminal()) {
            return;
        }
        this.estado = EstadoIntentoCompensacion.REVISION_MANUAL;
        this.ultimoError = limitar(error);
        this.proximoIntentoEn = null;
        this.actualizadoEn = Instantes.ahora();
    }

    public boolean esTerminal() {
        return estado == EstadoIntentoCompensacion.LIBERADA
            || estado == EstadoIntentoCompensacion.CANCELADA
            || estado == EstadoIntentoCompensacion.RECHAZADA;
    }

    public boolean estaActiva() {
        return estado == EstadoIntentoCompensacion.PENDIENTE
            || estado == EstadoIntentoCompensacion.LIBERANDO
            || estado == EstadoIntentoCompensacion.FALLIDA
            || estado == EstadoIntentoCompensacion.REVISION_MANUAL;
    }

    private void exigirTrabajador(String trabajador) {
        if (trabajador == null || trabajador.isBlank()) {
            throw new IllegalArgumentException(
                "El trabajador que reclama es obligatorio");
        }
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
