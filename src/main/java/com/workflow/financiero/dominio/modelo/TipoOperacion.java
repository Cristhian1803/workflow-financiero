package com.workflow.financiero.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Catálogo de tipos de operación.
 */
@Entity
@Table(name = "tipo_operacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoOperacion {

    @Id
    @Column(name = "tipooper_codigo", length = 30, nullable = false)
    private String codigo;

    @Column(name = "tipooper_nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "tipooper_sensible", nullable = false)
    private boolean sensible;

    @Column(name = "tipooper_activo", nullable = false)
    private boolean activo;

    public TipoOperacion(String codigo, String nombre, boolean sensible, boolean activo) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.sensible = sensible;
        this.activo = activo;
    }
}
