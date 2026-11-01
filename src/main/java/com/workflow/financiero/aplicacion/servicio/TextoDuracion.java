package com.workflow.financiero.aplicacion.servicio;

import java.time.Duration;

/**
 * Describe una duración con la unidad más grande que no deja fracciones.
 * Se usa en los textos de notificación para no fijar los plazos.
 */
final class TextoDuracion {

    private TextoDuracion() {
    }

    static String describir(Duration duracion) {
        if (duracion.toHours() > 0 && duracion.toMinutesPart() == 0) {
            long horas = duracion.toHours();
            return horas + (horas == 1 ? " hora" : " horas");
        }
        if (duracion.toMinutes() > 0 && duracion.toSecondsPart() == 0) {
            long minutos = duracion.toMinutes();
            return minutos + (minutos == 1 ? " minuto" : " minutos");
        }

        long segundos = duracion.toSeconds();
        return segundos + (segundos == 1 ? " segundo" : " segundos");
    }
}
