package com.workflow.financiero.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Solicitud del flujo de demostracion. Guarda el estado minimo necesario
 * para verificar la transaccion compartida.
 */
@Entity
@Table(name = "solicitud_prueba")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudPrueba {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String estado;

    @Column(nullable = false)
    private Long monto;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(name = "identificador_instancia_proceso", length = 64)
    private String identificadorInstanciaProceso;

    @Column(name = "resultado_simulado", length = 200)
    private String resultadoSimulado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();
}
