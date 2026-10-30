package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.excepcion.SolicitudInvalidaException;
import com.workflow.financiero.dominio.modelo.Moneda;
import com.workflow.financiero.dominio.modelo.PerfilRiesgo;
import com.workflow.financiero.dominio.modelo.TipoOperacion;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.dominio.regla.validacion.ContextoValidacion;
import com.workflow.financiero.dominio.regla.validacion.ResultadoValidacion;
import com.workflow.financiero.dominio.regla.validacion.SolicitudAValidar;
import com.workflow.financiero.dominio.regla.validacion.ValidadorDescripcion;
import com.workflow.financiero.dominio.regla.validacion.ValidadorJustificacion;
import com.workflow.financiero.dominio.regla.validacion.ValidadorMoneda;
import com.workflow.financiero.dominio.regla.validacion.ValidadorMonto;
import com.workflow.financiero.dominio.regla.validacion.ValidadorPerfilSolicitante;
import com.workflow.financiero.dominio.regla.validacion.ValidadorSolicitud;
import com.workflow.financiero.dominio.regla.validacion.ValidadorSolicitudCompuesto;
import com.workflow.financiero.dominio.regla.validacion.ValidadorTipoOperacion;
import com.workflow.financiero.infraestructura.persistencia.MonedaRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TipoOperacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve los catálogos y el perfil del solicitante, y ejecuta la cadena de
 * validación. No crea ni modifica datos.
 */
@Service
@RequiredArgsConstructor
public class ServicioValidacionSolicitud {

    private final UsuarioRepositorio usuarioRepositorio;
    private final TipoOperacionRepositorio tipoOperacionRepositorio;
    private final MonedaRepositorio monedaRepositorio;

    private final ValidadorSolicitud validador = new ValidadorSolicitudCompuesto(List.of(
        new ValidadorTipoOperacion(),
        new ValidadorMoneda(),
        new ValidadorMonto(),
        new ValidadorDescripcion(),
        new ValidadorJustificacion(),
        new ValidadorPerfilSolicitante()
    ));

    @Transactional(readOnly = true)
    public ContextoValidacion validar(SolicitudAValidar solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud a validar es obligatoria");
        }

        Usuario solicitante = solicitud.solicitanteId() == null
            ? null
            : usuarioRepositorio.findById(solicitud.solicitanteId()).orElse(null);
        PerfilRiesgo perfilSolicitante = solicitante == null
            ? null
            : solicitante.getPerfilRiesgo();

        String tipoOperacionCodigo = normalizarCodigo(solicitud.tipoOperacion());
        TipoOperacion tipoOperacion = tipoOperacionCodigo == null
            ? null
            : tipoOperacionRepositorio.findById(tipoOperacionCodigo).orElse(null);

        String monedaCodigo = normalizarCodigo(solicitud.moneda());
        Moneda moneda = monedaCodigo == null
            ? null
            : monedaRepositorio.findById(monedaCodigo).orElse(null);

        ContextoValidacion contexto = new ContextoValidacion(
            solicitante,
            perfilSolicitante,
            tipoOperacionCodigo,
            tipoOperacion,
            solicitud.monto(),
            monedaCodigo,
            moneda,
            solicitud.descripcion(),
            solicitud.justificacion()
        );

        ResultadoValidacion resultado = validador.validar(contexto);
        if (!resultado.esValido()) {
            throw new SolicitudInvalidaException(resultado.errores());
        }

        return contexto;
    }

    private String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        return codigo.trim().toUpperCase(Locale.ROOT);
    }
}
