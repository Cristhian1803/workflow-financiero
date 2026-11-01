package com.workflow.financiero.api.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Identificador de correlación de la petición. Se acepta el valor del cliente
 * solo si tiene un formato seguro para cabeceras y logs.
 */
public final class Correlacion {

    public static final String CABECERA = "X-Identificador-Correlacion";
    public static final String ATRIBUTO = "identificadorCorrelacion";
    public static final String CLAVE_MDC = "identificadorCorrelacion";

    private static final Pattern FORMATO_VALIDO =
        Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private Correlacion() {
    }

    public static String resolver(String recibido) {
        if (recibido == null) {
            return UUID.randomUUID().toString();
        }
        String limpio = recibido.trim();
        return FORMATO_VALIDO.matcher(limpio).matches()
            ? limpio
            : UUID.randomUUID().toString();
    }

    public static String obtener(HttpServletRequest peticion) {
        Object atributo = peticion.getAttribute(ATRIBUTO);
        if (atributo instanceof String valor && !valor.isBlank()) {
            return valor;
        }
        String desdeMdc = MDC.get(CLAVE_MDC);
        return desdeMdc == null || desdeMdc.isBlank()
            ? UUID.randomUUID().toString()
            : desdeMdc;
    }
}
