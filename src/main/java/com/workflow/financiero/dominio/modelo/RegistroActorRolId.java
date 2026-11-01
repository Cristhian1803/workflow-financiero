package com.workflow.financiero.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Clave compuesta de la asociación entre una solicitud y el actor que ya
 * aprobó un rol funcional en ella.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RegistroActorRolId implements Serializable {

    @Column(name = "regiactorol_solifina_id", nullable = false)
    private UUID solicitudId;

    @Column(name = "regiactorol_usua_id", nullable = false)
    private UUID usuarioId;
}
