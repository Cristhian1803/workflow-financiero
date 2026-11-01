package com.workflow.financiero.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Catálogo de perfiles de riesgo asignados a los usuarios.
 */
@Entity
@Table(name = "perfil_riesgo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerfilRiesgo {

    @Id
    @Column(name = "perfries_codigo", length = 30, nullable = false)
    private String codigo;

    @Column(name = "perfries_nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "perfries_activo", nullable = false)
    private boolean activo;

    public PerfilRiesgo(String codigo, String nombre, boolean activo) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.activo = activo;
    }
}
