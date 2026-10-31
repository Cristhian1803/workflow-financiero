package com.workflow.financiero.aplicacion.simulador;

import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fallos en memoria del simulador. No se persisten porque solo afectan la
 * ejecución de pruebas y demostraciones.
 */
@Component
@Profile({"desarrollo", "pruebas"})
public class ConfiguracionFallosSimulador {

    private final Map<String, ModoFalloSimulador> fallos =
        new ConcurrentHashMap<>();

    public void configurar(String clave, ModoFalloSimulador modo) {
        if (clave == null || clave.isBlank() || modo == null) {
            throw new IllegalArgumentException(
                "La clave y el modo de fallo son obligatorios");
        }
        fallos.put(clave, modo);
    }

    public void limpiar() {
        fallos.clear();
    }

    public boolean fallaAntesDelEfecto(
        String clave,
        AccionFinanciera accion
    ) {
        ModoFalloSimulador modo = fallos.get(clave);
        return modo == ModoFalloSimulador.FALLO_ANTES_DEL_EFECTO
            || (accion == AccionFinanciera.LIBERACION
                && modo == ModoFalloSimulador.LIBERACION_FALLIDA);
    }

    public boolean pierdeRespuesta(String clave) {
        return fallos.get(clave) == ModoFalloSimulador.RESPUESTA_PERDIDA;
    }

    public boolean consultaNoDisponible(String clave) {
        return fallos.get(clave) == ModoFalloSimulador.CONSULTA_NO_DISPONIBLE;
    }

    public boolean errorDefinitivo(String clave) {
        return fallos.get(clave) == ModoFalloSimulador.ERROR_DEFINITIVO;
    }

    public boolean conflictoIdempotencia(String clave) {
        return fallos.get(clave) == ModoFalloSimulador.CONFLICTO_IDEMPOTENCIA;
    }
}
