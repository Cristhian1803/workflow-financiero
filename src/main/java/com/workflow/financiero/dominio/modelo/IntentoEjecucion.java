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
 * Intención financiera durable de una revisión. La clave y la huella se
 * calculan una sola vez y se conservan durante todo el ciclo de reintento y
 * reconciliación.
 */
@Entity
@Table(name = "intento_ejecucion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntentoEjecucion {

    private static final int LONGITUD_ERROR = 1000;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inteejec_cancelacion_actor_usua_id")
    private Usuario cancelacionActor;

    @Column(name = "inteejec_cancelacion_motivo", length = LONGITUD_ERROR)
    private String cancelacionMotivo;

    @Column(name = "inteejec_cancelacion_solicitada_en")
    private Instant cancelacionSolicitadaEn;

    @Column(name = "inteejec_liberacion_referencia", length = 100)
    private String liberacionReferencia;

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
        exigirTrabajador(trabajador);
        this.estado = EstadoIntentoEjecucion.ENVIANDO;
        this.intentos++;
        this.reclamadoEn = Instantes.ahora();
        this.reclamadoPor = trabajador;
        this.actualizadoEn = this.reclamadoEn;
    }

    /**
     * Reclama una intención que quedó en envío o cuyo resultado es incierto.
     * El incremento de intentos permite limitar los reintentos automáticos.
     */
    public void reclamarRecuperacion(String trabajador) {
        if (estado != EstadoIntentoEjecucion.ENVIANDO
            && estado != EstadoIntentoEjecucion.RESULTADO_DESCONOCIDO) {
            throw new IllegalStateException(
                "La intención no es recuperable: " + estado);
        }
        exigirTrabajador(trabajador);
        this.estado = EstadoIntentoEjecucion.ENVIANDO;
        this.intentos++;
        this.reclamadoEn = Instantes.ahora();
        this.reclamadoPor = trabajador;
        this.proximoIntentoEn = null;
        this.actualizadoEn = this.reclamadoEn;
    }

    /**
     * Habilita un reintento manual autorizado desde un incidente. No cambia
     * la clave ni la huella de la intención.
     */
    public void reintentarManual(String trabajador) {
        if (estado != EstadoIntentoEjecucion.FALLIDA
            && estado != EstadoIntentoEjecucion.RESULTADO_DESCONOCIDO
            && estado != EstadoIntentoEjecucion.ENVIANDO) {
            throw new IllegalStateException(
                "La intención no admite reintento manual: " + estado);
        }
        exigirTrabajador(trabajador);
        this.estado = EstadoIntentoEjecucion.ENVIANDO;
        this.intentos++;
        this.reclamadoEn = Instantes.ahora();
        this.reclamadoPor = trabajador;
        this.proximoIntentoEn = null;
        this.actualizadoEn = this.reclamadoEn;
    }

    public void confirmar(
        String reservaReferencia,
        String confirmacionReferencia,
        String referenciaExterna
    ) {
        if (estado != EstadoIntentoEjecucion.ENVIANDO
            && estado != EstadoIntentoEjecucion.RESULTADO_DESCONOCIDO
            && estado != EstadoIntentoEjecucion.COMPENSANDO) {
            throw new IllegalStateException(
                "La intención no está en envío: " + estado);
        }
        this.estado = EstadoIntentoEjecucion.CONFIRMADA;
        this.reservaReferencia = reservaReferencia;
        this.confirmacionReferencia = confirmacionReferencia;
        this.referenciaExterna = referenciaExterna;
        this.ultimoError = null;
        this.proximoIntentoEn = null;
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarResultadoDesconocido(String error) {
        if (estado == EstadoIntentoEjecucion.CONFIRMADA) {
            return;
        }
        if (estado != EstadoIntentoEjecucion.ENVIANDO
            && estado != EstadoIntentoEjecucion.RESULTADO_DESCONOCIDO) {
            throw new IllegalStateException(
                "La intención no puede quedar desconocida: " + estado);
        }
        this.estado = EstadoIntentoEjecucion.RESULTADO_DESCONOCIDO;
        this.ultimoError = limitar(error);
        this.actualizadoEn = Instantes.ahora();
    }

    public void marcarFallida(String error) {
        if (estado == EstadoIntentoEjecucion.CONFIRMADA) {
            return;
        }
        this.estado = EstadoIntentoEjecucion.FALLIDA;
        this.ultimoError = limitar(error);
        this.proximoIntentoEn = null;
        this.actualizadoEn = Instantes.ahora();
    }

    public void programarReintento(Instant proximoIntento, String error) {
        if (estado == EstadoIntentoEjecucion.CONFIRMADA) {
            return;
        }
        this.estado = EstadoIntentoEjecucion.RESULTADO_DESCONOCIDO;
        this.proximoIntentoEn = proximoIntento;
        this.ultimoError = limitar(error);
        this.actualizadoEn = Instantes.ahora();
    }

    public void detenerReintentoAutomatico(String error) {
        if (estado == EstadoIntentoEjecucion.CONFIRMADA) {
            return;
        }
        this.proximoIntentoEn = null;
        this.ultimoError = limitar(error);
        this.actualizadoEn = Instantes.ahora();
    }

    /**
     * Cancela una intención que todavía no fue reclamada por el trabajador.
     * No hubo despacho y, por lo tanto, tampoco efecto financiero.
     */
    public void marcarCancelada(Usuario actor, String motivo) {
        if (estado != EstadoIntentoEjecucion.PENDIENTE) {
            throw new IllegalStateException(
                "La intención no está pendiente: " + estado);
        }
        this.estado = EstadoIntentoEjecucion.CANCELADA;
        this.cancelacionActor = actor;
        this.cancelacionMotivo = limitar(motivo);
        this.cancelacionSolicitadaEn = Instantes.ahora();
        this.actualizadoEn = this.cancelacionSolicitadaEn;
    }

    /**
     * Inicia la compensación de una intención que falló después de reservar.
     * La clave de liberación es estable y se deriva de la revisión.
     */
    public void marcarCompensando(Usuario actor, String motivo) {
        if (estado != EstadoIntentoEjecucion.FALLIDA
            && estado != EstadoIntentoEjecucion.ENVIANDO
            && estado != EstadoIntentoEjecucion.RESULTADO_DESCONOCIDO) {
            throw new IllegalStateException(
                "La intención no se puede compensar: " + estado);
        }
        this.estado = EstadoIntentoEjecucion.COMPENSANDO;
        this.cancelacionActor = actor;
        this.cancelacionMotivo = limitar(motivo);
        this.cancelacionSolicitadaEn = Instantes.ahora();
        this.actualizadoEn = this.cancelacionSolicitadaEn;
    }

    public void marcarCompensada(String referenciaLiberacion) {
        if (estado != EstadoIntentoEjecucion.COMPENSANDO
            && estado != EstadoIntentoEjecucion.CANCELADA) {
            throw new IllegalStateException(
                "La intención no está compensando: " + estado);
        }
        this.estado = EstadoIntentoEjecucion.COMPENSADA;
        this.liberacionReferencia = referenciaLiberacion;
        this.actualizadoEn = Instantes.ahora();
    }

    /**
     * La compensación no encontró reserva que liberar. La intención queda
     * cancelada sin referencia de liberación.
     */
    public void marcarCanceladaSinCompensacion() {
        if (estado != EstadoIntentoEjecucion.COMPENSANDO) {
            throw new IllegalStateException(
                "La intención no está compensando: " + estado);
        }
        this.estado = EstadoIntentoEjecucion.CANCELADA;
        this.actualizadoEn = Instantes.ahora();
    }

    public void registrarErrorCompensacion(String error) {
        this.ultimoError = limitar(error);
        this.actualizadoEn = Instantes.ahora();
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
        return texto.length() <= LONGITUD_ERROR
            ? texto
            : texto.substring(0, LONGITUD_ERROR);
    }
}
