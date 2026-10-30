package com.workflow.financiero.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Catálogo de roles de seguridad y de aprobación funcional.
 */
@Entity
@Table(name = "rol")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rol {

    @Id
    @Column(name = "rol_codigo", length = 30, nullable = false)
    private String codigo;

    @Column(name = "rol_nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "rol_aprobador", nullable = false)
    private boolean aprobador;

    @Column(name = "rol_activo", nullable = false)
    private boolean activo;

    public Rol(String codigo, String nombre, boolean aprobador, boolean activo) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.aprobador = aprobador;
        this.activo = activo;
    }
}
