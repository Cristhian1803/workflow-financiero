package com.workflow.financiero.dominio.modelo;

import com.workflow.financiero.dominio.temporal.Instantes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Usuario del sistema, con su perfil de riesgo y sus roles de seguridad.
 */
@Entity
@Table(name = "usuario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario {

    @Id
    @Column(name = "usua_id", nullable = false)
    private UUID id;

    @Column(name = "usua_usuario", length = 50, nullable = false)
    private String usuario;

    @Column(name = "usua_clave_hash", length = 100, nullable = false)
    private String claveHash;

    @Column(name = "usua_nombre", length = 100, nullable = false)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usua_perfries_codigo", nullable = false)
    private PerfilRiesgo perfilRiesgo;

    @Column(name = "usua_activo", nullable = false)
    private boolean activo;

    @Version
    @Column(name = "usua_version", nullable = false)
    private Long version;

    @Column(name = "usua_creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "usua_actualizado_en")
    private Instant actualizadoEn;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "usuario_rol",
        joinColumns = @JoinColumn(name = "usurol_usua_id"),
        inverseJoinColumns = @JoinColumn(name = "usurol_rol_codigo")
    )
    private Set<Rol> roles = new HashSet<>();

    public Usuario(
        UUID id,
        String usuario,
        String claveHash,
        String nombre,
        PerfilRiesgo perfilRiesgo,
        Set<Rol> roles
    ) {
        this.id = id;
        this.usuario = usuario;
        this.claveHash = claveHash;
        this.nombre = nombre;
        this.perfilRiesgo = perfilRiesgo;
        this.activo = true;
        this.creadoEn = Instantes.ahora();
        this.roles.addAll(roles);
    }
}
