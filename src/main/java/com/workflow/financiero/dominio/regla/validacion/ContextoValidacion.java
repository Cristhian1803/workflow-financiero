package com.workflow.financiero.dominio.regla.validacion;

import com.workflow.financiero.dominio.modelo.Moneda;
import com.workflow.financiero.dominio.modelo.PerfilRiesgo;
import com.workflow.financiero.dominio.modelo.TipoOperacion;
import com.workflow.financiero.dominio.modelo.Usuario;
import java.math.BigDecimal;

/**
 * Datos ya resueltos contra los catálogos y el usuario autenticado. Los
 * códigos se guardan normalizados para que los validadores puedan distinguir
 * entre un dato ausente, desconocido o inactivo.
 */
public record ContextoValidacion(
    Usuario solicitante,
    PerfilRiesgo perfilSolicitante,
    String tipoOperacionCodigo,
    TipoOperacion tipoOperacion,
    BigDecimal monto,
    String monedaCodigo,
    Moneda moneda,
    String descripcion,
    String justificacion
) {
}
