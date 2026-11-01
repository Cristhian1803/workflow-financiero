package com.workflow.financiero;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reloj de prueba que permite fijar o avanzar el instante sin esperar ni
 * modificar la base de datos.
 */
final class RelojMutable extends Clock {

    private final AtomicReference<Instant> instante;
    private final ZoneId zona;

    RelojMutable(Instant instante, ZoneId zona) {
        this(new AtomicReference<>(instante), zona);
    }

    private RelojMutable(AtomicReference<Instant> instante, ZoneId zona) {
        this.instante = instante;
        this.zona = zona;
    }

    void fijar(Instant nuevo) {
        instante.set(nuevo);
    }

    void avanzar(Duration duracion) {
        instante.updateAndGet(actual -> actual.plus(duracion));
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new RelojMutable(instante, zone);
    }

    @Override
    public Instant instant() {
        return instante.get();
    }
}
