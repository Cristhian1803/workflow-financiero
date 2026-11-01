package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesEjecucion;
import com.workflow.financiero.aplicacion.excepcion.ConflictoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorDefinitivoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorRecuperableProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.OperacionNoEncontradaProveedorFinancieroException;
import com.workflow.financiero.aplicacion.puerto.PuertoProveedorFinanciero;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ClavesOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.EstadoOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.HuellaOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import com.workflow.financiero.dominio.excepcion.CancelacionNoPermitidaException;
import com.workflow.financiero.dominio.excepcion.EjecucionNoPermitidaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.temporal.Instantes;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Ejecuta una pasada de compensación. Primero consulta la confirmación para no
 * liberar una reserva potencialmente confirmada; después consulta la reserva
 * y libera con la misma clave en todos los reintentos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioCompensacion {

    private final ServicioIntencionCompensacion servicioIntencion;
    private final ServicioIntencionEjecucion servicioEjecucion;
    private final ServicioIntencionCancelacion servicioCancelacion;
    private final ServicioIncidente servicioIncidente;
    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final PuertoProveedorFinanciero proveedor;
    private final PropiedadesEjecucion propiedades;

    public ResultadoCompensacion ejecutar(
        PreparacionCancelacion preparacion
    ) {
        return ejecutar(preparacion, ContextoCompensacion.automatico());
    }

    public ResultadoCompensacion ejecutar(
        TrabajoCompensacion trabajo,
        ContextoCompensacion contexto
    ) {
        return ejecutar(
            PreparacionCancelacion.desde(trabajo, contexto), contexto);
    }

    private ResultadoCompensacion ejecutar(
        PreparacionCancelacion preparacion,
        ContextoCompensacion contexto
    ) {
        try {
            return compensar(preparacion, contexto);
        } catch (ErrorRecuperableProveedorFinancieroException error) {
            return pendiente(preparacion, contexto, error.getMessage());
        } catch (ConflictoProveedorFinancieroException error) {
            return manual(preparacion, contexto, error.getMessage());
        } catch (ErrorDefinitivoProveedorFinancieroException error) {
            return fallo(preparacion, contexto, error.getMessage());
        } catch (CancelacionNoPermitidaException
            | EjecucionNoPermitidaException error) {
            return manual(preparacion, contexto, error.getMessage());
        } catch (RuntimeException error) {
            log.error(
                "No se pudo completar la compensación de la revisión {}: {}",
                preparacion.idRevision(),
                error.getMessage()
            );
            return pendiente(preparacion, contexto, error.getMessage());
        }
    }

    private ResultadoCompensacion compensar(
        PreparacionCancelacion preparacion,
        ContextoCompensacion contexto
    ) {
        ResultadoOperacionFinanciera confirmacion = consultarOpcional(
            claveConfirmacion(preparacion));
        if (confirmacion != null) {
            validarConfirmacion(preparacion, confirmacion);
            ResultadoOperacionFinanciera reserva = consultarOpcional(
                claveReserva(preparacion));
            if (reserva == null
                || reserva.estado() != EstadoOperacionFinanciera.CONFIRMADA) {
                return manual(
                    preparacion,
                    contexto,
                    "La confirmación existe pero la reserva no está confirmada"
                );
            }
            return confirmarEjecucion(preparacion, reserva, confirmacion);
        }

        ResultadoOperacionFinanciera reserva = consultarOpcional(
            claveReserva(preparacion));
        if (reserva == null) {
            if (preparacion.despachoEnCurso()) {
                return pendiente(
                    preparacion,
                    contexto,
                    "El despacho de la confirmación todavía puede estar en curso"
                );
            }
            return cancelarSinReserva(preparacion);
        }

        return switch (reserva.estado()) {
            case LIBERADA -> confirmarLiberacionExistente(
                preparacion, contexto);
            case RESERVADA -> liberar(preparacion, contexto);
            case CONFIRMADA -> manual(
                preparacion,
                contexto,
                "La reserva figura confirmada pero no existe la confirmación"
            );
            default -> manual(
                preparacion,
                contexto,
                "La reserva no está en un estado compensable"
            );
        };
    }

    private ResultadoCompensacion liberar(
        PreparacionCancelacion preparacion,
        ContextoCompensacion contexto
    ) {
        OperacionFinanciera liberacion = OperacionFinanciera.crear(
            preparacion.idSolicitud(),
            preparacion.idRevision(),
            AccionFinanciera.LIBERACION,
            preparacion.monto(),
            preparacion.moneda(),
            preparacion.tipoOperacion()
        );
        try {
            ResultadoOperacionFinanciera resultado =
                proveedor.liberar(liberacion);
            validarLiberacion(preparacion, resultado);
            return confirmarLiberacion(preparacion, resultado);
        } catch (ErrorRecuperableProveedorFinancieroException error) {
            return recuperarLiberacion(preparacion, contexto, error);
        } catch (ConflictoProveedorFinancieroException error) {
            return reconciliarConflicto(preparacion, contexto, error);
        } catch (ErrorDefinitivoProveedorFinancieroException error) {
            return fallo(preparacion, contexto, error.getMessage());
        }
    }

    private ResultadoCompensacion recuperarLiberacion(
        PreparacionCancelacion preparacion,
        ContextoCompensacion contexto,
        RuntimeException error
    ) {
        try {
            ResultadoOperacionFinanciera liberacion = consultarOpcional(
                claveLiberacion(preparacion));
            if (liberacion != null) {
                validarLiberacion(preparacion, liberacion);
                return confirmarLiberacion(preparacion, liberacion);
            }
            return pendiente(
                preparacion,
                contexto,
                "La liberación no se pudo confirmar: " + error.getMessage()
            );
        } catch (ErrorRecuperableProveedorFinancieroException consulta) {
            return pendiente(
                preparacion,
                contexto,
                "La consulta de liberación no está disponible: "
                    + consulta.getMessage()
            );
        } catch (ConflictoProveedorFinancieroException conflicto) {
            return manual(preparacion, contexto, conflicto.getMessage());
        }
    }

    private ResultadoCompensacion reconciliarConflicto(
        PreparacionCancelacion preparacion,
        ContextoCompensacion contexto,
        RuntimeException error
    ) {
        try {
            ResultadoOperacionFinanciera confirmacion = consultarOpcional(
                claveConfirmacion(preparacion));
            if (confirmacion != null) {
                validarConfirmacion(preparacion, confirmacion);
                ResultadoOperacionFinanciera reserva = consultarOpcional(
                    claveReserva(preparacion));
                if (reserva != null
                    && reserva.estado()
                        == EstadoOperacionFinanciera.CONFIRMADA) {
                    return confirmarEjecucion(
                        preparacion, reserva, confirmacion);
                }
                return manual(
                    preparacion,
                    contexto,
                    "Conflicto de liberación con confirmación inconsistente: "
                        + error.getMessage()
                );
            }

            ResultadoOperacionFinanciera liberacion = consultarOpcional(
                claveLiberacion(preparacion));
            if (liberacion != null) {
                validarLiberacion(preparacion, liberacion);
                return confirmarLiberacion(preparacion, liberacion);
            }
            return pendiente(
                preparacion,
                contexto,
                "El conflicto de liberación todavía no se puede resolver: "
                    + error.getMessage()
            );
        } catch (ErrorRecuperableProveedorFinancieroException consulta) {
            return pendiente(preparacion, contexto, consulta.getMessage());
        } catch (ConflictoProveedorFinancieroException conflicto) {
            return manual(preparacion, contexto, conflicto.getMessage());
        }
    }

    private ResultadoCompensacion confirmarLiberacionExistente(
        PreparacionCancelacion preparacion,
        ContextoCompensacion contexto
    ) {
        ResultadoOperacionFinanciera liberacion = consultarOpcional(
            claveLiberacion(preparacion));
        if (liberacion == null) {
            return pendiente(
                preparacion,
                contexto,
                "La reserva figura liberada pero no existe la liberación"
            );
        }
        validarLiberacion(preparacion, liberacion);
        return confirmarLiberacion(preparacion, liberacion);
    }

    private ResultadoCompensacion confirmarEjecucion(
        PreparacionCancelacion preparacion,
        ResultadoOperacionFinanciera reserva,
        ResultadoOperacionFinanciera confirmacion
    ) {
        TrabajoEjecucion trabajo = servicioEjecucion.obtenerTrabajo(
            preparacion.idIntento());
        servicioEjecucion.registrarConfirmacion(
            trabajo,
            reserva,
            confirmacion,
            "Ejecución confirmada durante la cancelación"
        );
        return ResultadoCompensacion.RECHAZADA;
    }

    private ResultadoCompensacion confirmarLiberacion(
        PreparacionCancelacion preparacion,
        ResultadoOperacionFinanciera liberacion
    ) {
        servicioCancelacion.confirmarCompensacion(preparacion, liberacion);
        return ResultadoCompensacion.LIBERADA;
    }

    private ResultadoCompensacion cancelarSinReserva(
        PreparacionCancelacion preparacion
    ) {
        servicioCancelacion.confirmarCompensacion(preparacion, null);
        return ResultadoCompensacion.CANCELADA_SIN_RESERVA;
    }

    private ResultadoCompensacion pendiente(
        PreparacionCancelacion preparacion,
        ContextoCompensacion contexto,
        String error
    ) {
        ResultadoCompensacion terminal = resultadoTerminal(
            preparacion.idCompensacion());
        if (terminal != null) {
            return terminal;
        }
        if (contexto.manual()
            || preparacion.intentos() >= propiedades.maxIntentos()) {
            return manual(preparacion, contexto, error);
        }

        servicioIntencion.marcarPendiente(
            preparacion.idCompensacion(),
            error,
            Instantes.ahora().plus(propiedades.reintentoEspera())
        );
        servicioCancelacion.marcarCompensacionPendiente(
            preparacion, error);
        return ResultadoCompensacion.PENDIENTE;
    }

    private ResultadoCompensacion fallo(
        PreparacionCancelacion preparacion,
        ContextoCompensacion contexto,
        String error
    ) {
        ResultadoCompensacion terminal = resultadoTerminal(
            preparacion.idCompensacion());
        if (terminal != null) {
            return terminal;
        }
        servicioIntencion.marcarFallida(
            preparacion.idCompensacion(), error);
        servicioCancelacion.marcarCompensacionFallida(
            preparacion, error);
        crearIncidente(preparacion, error, false);
        return ResultadoCompensacion.FALLIDA;
    }

    private ResultadoCompensacion manual(
        PreparacionCancelacion preparacion,
        ContextoCompensacion contexto,
        String error
    ) {
        ResultadoCompensacion terminal = resultadoTerminal(
            preparacion.idCompensacion());
        if (terminal != null) {
            return terminal;
        }
        servicioIntencion.marcarRevisionManual(
            preparacion.idCompensacion(), error);
        servicioCancelacion.marcarCompensacionFallida(
            preparacion, error);
        crearIncidente(preparacion, error, true);
        if (contexto.incidenteId() != null) {
            servicioIncidente.registrarResultado(
                contexto.incidenteId(),
                error,
                true,
                contexto.correlacion()
            );
        }
        return ResultadoCompensacion.REVISION_MANUAL;
    }

    private ResultadoCompensacion resultadoTerminal(UUID idCompensacion) {
        if (idCompensacion == null) {
            return null;
        }
        return switch (servicioIntencion.obtener(idCompensacion).getEstado()) {
            case LIBERADA -> ResultadoCompensacion.LIBERADA;
            case CANCELADA -> ResultadoCompensacion.CANCELADA_SIN_RESERVA;
            case RECHAZADA -> ResultadoCompensacion.RECHAZADA;
            case PENDIENTE, LIBERANDO, FALLIDA, REVISION_MANUAL -> null;
        };
    }

    private void crearIncidente(
        PreparacionCancelacion preparacion,
        String error,
        boolean reintentable
    ) {
        SolicitudFinanciera solicitud = solicitudRepositorio
            .findById(preparacion.idSolicitud())
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la solicitud " + preparacion.idSolicitud()));
        RevisionSolicitud revision = revisionRepositorio
            .buscarDetallePorId(preparacion.idRevision())
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la revisión " + preparacion.idRevision()));
        servicioIncidente.crearCompensacion(
            solicitud,
            revision,
            preparacion.idCompensacion(),
            error,
            reintentable,
            preparacion.correlacion()
        );
    }

    private ResultadoOperacionFinanciera consultarOpcional(String clave) {
        try {
            return proveedor.consultar(clave);
        } catch (OperacionNoEncontradaProveedorFinancieroException noExiste) {
            return null;
        }
    }

    private void validarConfirmacion(
        PreparacionCancelacion preparacion,
        ResultadoOperacionFinanciera confirmacion
    ) {
        String claveEsperada = claveConfirmacion(preparacion);
        String huellaEsperada = huella(
            preparacion, AccionFinanciera.CONFIRMACION);
        if (confirmacion.accion() != AccionFinanciera.CONFIRMACION
            || !claveEsperada.equals(confirmacion.clave())
            || !huellaEsperada.equals(confirmacion.huella())
            || confirmacion.estado()
                != EstadoOperacionFinanciera.CONFIRMADA) {
            throw new ConflictoProveedorFinancieroException(
                "La confirmación no corresponde a la intención o no es efectiva");
        }
    }

    private void validarLiberacion(
        PreparacionCancelacion preparacion,
        ResultadoOperacionFinanciera liberacion
    ) {
        String claveEsperada = claveLiberacion(preparacion);
        String huellaEsperada = huella(
            preparacion, AccionFinanciera.LIBERACION);
        if (liberacion.accion() != AccionFinanciera.LIBERACION
            || !claveEsperada.equals(liberacion.clave())
            || !huellaEsperada.equals(liberacion.huella())
            || liberacion.estado() != EstadoOperacionFinanciera.LIBERADA) {
            throw new ConflictoProveedorFinancieroException(
                "La liberación no corresponde a la intención o no quedó confirmada");
        }
    }

    private String huella(
        PreparacionCancelacion preparacion,
        AccionFinanciera accion
    ) {
        return HuellaOperacionFinanciera.calcular(
            accion,
            preparacion.idSolicitud(),
            preparacion.idRevision(),
            preparacion.monto(),
            preparacion.moneda(),
            preparacion.tipoOperacion()
        );
    }

    private String claveConfirmacion(PreparacionCancelacion preparacion) {
        return ClavesOperacionFinanciera.confirmacion(
            preparacion.idSolicitud(), preparacion.idRevision());
    }

    private String claveReserva(PreparacionCancelacion preparacion) {
        return ClavesOperacionFinanciera.reserva(
            preparacion.idSolicitud(), preparacion.idRevision());
    }

    private String claveLiberacion(PreparacionCancelacion preparacion) {
        return ClavesOperacionFinanciera.liberacion(
            preparacion.idSolicitud(), preparacion.idRevision());
    }
}
