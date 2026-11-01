package com.workflow.financiero.infraestructura.simulador;

import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.EstadoOperacionFinanciera;
import com.workflow.financiero.dominio.temporal.Instantes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro observable del proveedor financiero simulado. Es una tabla de
 * demostración y no representa la intención financiera de la aplicación.
 */
@Entity
@Table(name = "operacion_simulada")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperacionSimulada {

    @Id
    @Column(name = "opersimu_id", nullable = false)
    private UUID id;

    @Column(name = "opersimu_clave", nullable = false, length = 100)
    private String clave;

    @Column(name = "opersimu_huella", nullable = false, length = 128)
    private String huella;

    @Enumerated(EnumType.STRING)
    @Column(name = "opersimu_accion", nullable = false, length = 20)
    private AccionFinanciera accion;

    @Enumerated(EnumType.STRING)
    @Column(name = "opersimu_estado", nullable = false, length = 20)
    private EstadoOperacionFinanciera estado;

    @Column(name = "opersimu_referencia", nullable = false, length = 100)
    private String referencia;

    @Column(name = "opersimu_solifina_id", nullable = false)
    private UUID solicitudId;

    @Column(name = "opersimu_revisoli_id", nullable = false)
    private UUID revisionId;

    @Column(name = "opersimu_monto", nullable = false, precision = 19, scale = 0)
    private BigDecimal monto;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "opersimu_mone_codigo", nullable = false, length = 3)
    private String moneda;

    @Column(name = "opersimu_tipooper_codigo", nullable = false, length = 30)
    private String tipoOperacion;

    @Version
    @Column(name = "opersimu_version", nullable = false)
    private Long version;

    @Column(name = "opersimu_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "opersimu_actualizado_en")
    private Instant actualizadoEn;

    public OperacionSimulada(
        UUID id,
        String clave,
        String huella,
        AccionFinanciera accion,
        EstadoOperacionFinanciera estado,
        String referencia,
        UUID solicitudId,
        UUID revisionId,
        BigDecimal monto,
        String moneda,
        String tipoOperacion,
        Instant creadoEn
    ) {
        this.id = id;
        this.clave = clave;
        this.huella = huella;
        this.accion = accion;
        this.estado = estado;
        this.referencia = referencia;
        this.solicitudId = solicitudId;
        this.revisionId = revisionId;
        this.monto = monto;
        this.moneda = moneda;
        this.tipoOperacion = tipoOperacion;
        this.creadoEn = creadoEn;
    }

    public void confirmar() {
        if (estado != EstadoOperacionFinanciera.RESERVADA) {
            throw new IllegalStateException(
                "La reserva no está en estado RESERVADA");
        }
        estado = EstadoOperacionFinanciera.CONFIRMADA;
        actualizadoEn = Instantes.ahora();
    }

    public void liberar() {
        if (estado != EstadoOperacionFinanciera.RESERVADA) {
            throw new IllegalStateException(
                "La reserva no está en estado RESERVADA");
        }
        estado = EstadoOperacionFinanciera.LIBERADA;
        actualizadoEn = Instantes.ahora();
    }
}
