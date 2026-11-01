package com.workflow.financiero.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Catálogo de monedas. En esta versión solo se utiliza PYG.
 */
@Entity
@Table(name = "moneda")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Moneda {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "mone_codigo", length = 3, nullable = false)
    private String codigo;

    @Column(name = "mone_nombre", length = 50, nullable = false)
    private String nombre;

    @Column(name = "mone_simbolo", length = 10, nullable = false)
    private String simbolo;

    @Column(name = "mone_decimales", nullable = false)
    private Short decimales;

    @Column(name = "mone_activa", nullable = false)
    private boolean activa;

    public Moneda(String codigo, String nombre, String simbolo, Short decimales, boolean activa) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.simbolo = simbolo;
        this.decimales = decimales;
        this.activa = activa;
    }
}
