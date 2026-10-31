package com.workflow.financiero.infraestructura.externo;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesProveedorFinanciero;
import com.workflow.financiero.aplicacion.excepcion.ConflictoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorDefinitivoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorRecuperableProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.OperacionNoEncontradaProveedorFinancieroException;
import com.workflow.financiero.aplicacion.puerto.PuertoProveedorFinanciero;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ClavesOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.HuellaOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import com.workflow.financiero.infraestructura.externo.contrato.OperacionProveedorFinancieroPeticion;
import com.workflow.financiero.infraestructura.externo.contrato.ResultadoProveedorFinancieroRespuesta;
import java.net.http.HttpClient;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Adaptador HTTP del proveedor financiero. No reintenta automáticamente: el
 * trabajador de ejecución decide la política de recuperación.
 */
@Component
@Slf4j
public class AdaptadorProveedorFinancieroHttp
    implements PuertoProveedorFinanciero {

    private static final String CABECERA_SECRETO = "X-Clave-Integracion";

    private final PropiedadesProveedorFinanciero propiedades;
    private final RestClient restClient;

    public AdaptadorProveedorFinancieroHttp(
        PropiedadesProveedorFinanciero propiedades
    ) {
        this.propiedades = propiedades;
        this.restClient = crearRestClient(propiedades);
    }

    @Override
    public ResultadoOperacionFinanciera reservar(OperacionFinanciera operacion) {
        exigirAccion(operacion, AccionFinanciera.RESERVA);
        return ejecutarPost("/reservas", operacion);
    }

    @Override
    public ResultadoOperacionFinanciera confirmar(OperacionFinanciera operacion) {
        exigirAccion(operacion, AccionFinanciera.CONFIRMACION);
        return ejecutarPost("/confirmaciones", operacion);
    }

    @Override
    public ResultadoOperacionFinanciera liberar(OperacionFinanciera operacion) {
        exigirAccion(operacion, AccionFinanciera.LIBERACION);
        return ejecutarPost("/liberaciones", operacion);
    }

    @Override
    public ResultadoOperacionFinanciera consultar(String clave) {
        validarConfiguracion();
        if (clave == null || clave.isBlank()) {
            throw new IllegalArgumentException("La clave es obligatoria");
        }
        try {
            ResultadoProveedorFinancieroRespuesta respuesta = restClient.get()
                .uri(uriOperacion(clave))
                .header(CABECERA_SECRETO, propiedades.secreto())
                .retrieve()
                .body(ResultadoProveedorFinancieroRespuesta.class);
            return respuestaValida(respuesta);
        } catch (RestClientResponseException error) {
            log.warn(
                "El proveedor financiero respondió {} al consultar una operación",
                error.getStatusCode().value()
            );
            throw traducir(error);
        } catch (ResourceAccessException error) {
            log.warn("No se pudo conectar con el proveedor financiero");
            throw new ErrorRecuperableProveedorFinancieroException(
                "No se pudo conectar con el proveedor financiero",
                error
            );
        } catch (RestClientException error) {
            throw new ErrorDefinitivoProveedorFinancieroException(
                "El proveedor financiero devolvió una respuesta inválida",
                error
            );
        }
    }

    private ResultadoOperacionFinanciera ejecutarPost(
        String ruta,
        OperacionFinanciera operacion
    ) {
        validarConfiguracion();
        validarOperacion(operacion);
        try {
            ResultadoProveedorFinancieroRespuesta respuesta = restClient.post()
                .uri(uri(ruta))
                .header(CABECERA_SECRETO, propiedades.secreto())
                .contentType(MediaType.APPLICATION_JSON)
                .body(OperacionProveedorFinancieroPeticion.desde(operacion))
                .retrieve()
                .body(ResultadoProveedorFinancieroRespuesta.class);
            return respuestaValida(respuesta);
        } catch (RestClientResponseException error) {
            log.warn(
                "El proveedor financiero respondió {} para la operación {}",
                error.getStatusCode().value(),
                operacion.clave()
            );
            throw traducir(error);
        } catch (ResourceAccessException error) {
            log.warn(
                "No se pudo conectar con el proveedor financiero para la operación {}",
                operacion.clave()
            );
            throw new ErrorRecuperableProveedorFinancieroException(
                "No se pudo conectar con el proveedor financiero",
                error
            );
        } catch (RestClientException error) {
            throw new ErrorDefinitivoProveedorFinancieroException(
                "El proveedor financiero devolvió una respuesta inválida",
                error
            );
        }
    }

    private RestClient crearRestClient(
        PropiedadesProveedorFinanciero propiedades
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(propiedades.tiempoConexion())
            .build();
        JdkClientHttpRequestFactory fabrica =
            new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(propiedades.tiempoLectura());
        return RestClient.builder()
            .requestFactory(fabrica)
            .build();
    }

    private URI uri(String ruta) {
        return UriComponentsBuilder.fromUri(propiedades.urlBase())
            .path(ruta)
            .build()
            .toUri();
    }

    private URI uriOperacion(String clave) {
        return UriComponentsBuilder.fromUri(propiedades.urlBase())
            .pathSegment("operaciones", clave)
            .build()
            .encode()
            .toUri();
    }

    private void validarConfiguracion() {
        if (propiedades.secreto() == null
            || propiedades.secreto().isBlank()) {
            throw new IllegalStateException(
                "La clave de integración del proveedor no está configurada");
        }
    }

    private void exigirAccion(
        OperacionFinanciera operacion,
        AccionFinanciera esperada
    ) {
        if (operacion == null) {
            throw new IllegalArgumentException("La operación es obligatoria");
        }
        if (operacion.accion() != esperada) {
            throw new IllegalArgumentException(
                "La acción no corresponde a la operación solicitada");
        }
    }

    private void validarOperacion(OperacionFinanciera operacion) {
        String claveEsperada = ClavesOperacionFinanciera.clave(
            operacion.accion(),
            operacion.solicitudId(),
            operacion.revisionId()
        );
        if (!claveEsperada.equals(operacion.clave())) {
            throw new IllegalArgumentException(
                "La clave no corresponde a la identidad de la operación");
        }
        String huellaEsperada = HuellaOperacionFinanciera.calcular(operacion);
        if (!huellaEsperada.equals(operacion.huella())) {
            throw new IllegalArgumentException(
                "La huella no corresponde a los parámetros de la operación");
        }
    }

    private ResultadoOperacionFinanciera respuestaValida(
        ResultadoProveedorFinancieroRespuesta respuesta
    ) {
        if (respuesta == null) {
            throw new ErrorDefinitivoProveedorFinancieroException(
                "El proveedor financiero devolvió una respuesta vacía");
        }
        return respuesta.aDominio();
    }

    private RuntimeException traducir(RestClientResponseException error) {
        int estado = error.getStatusCode().value();
        if (estado == 404) {
            return new OperacionNoEncontradaProveedorFinancieroException(
                "El proveedor financiero no conoce la operación consultada",
                error
            );
        }
        if (estado == 409) {
            return new ConflictoProveedorFinancieroException(
                "El proveedor financiero informó un conflicto de idempotencia",
                error
            );
        }
        if (estado >= 500) {
            return new ErrorRecuperableProveedorFinancieroException(
                "El proveedor financiero no está disponible",
                error
            );
        }
        return new ErrorDefinitivoProveedorFinancieroException(
            "El proveedor financiero rechazó la operación",
            error
        );
    }
}
