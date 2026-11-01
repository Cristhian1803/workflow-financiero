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
 * Evento del historial de una solicitud.
 */
@Entity
@Table(name = "evento_solicitud_prueba")
@Getter
@Setter
@NoArgsConstructor
public class EventoSolicitudPrueba {

    @Id
    private UUID id;

    @Column(name = "solicitud_id", nullable = false)
    private UUID solicitudId;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(length = 500)
    private String detalle;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();
}
