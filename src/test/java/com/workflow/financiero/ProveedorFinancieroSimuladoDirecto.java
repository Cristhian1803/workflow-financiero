package com.workflow.financiero;

import com.workflow.financiero.aplicacion.puerto.PuertoProveedorFinanciero;
import com.workflow.financiero.aplicacion.simulador.ServicioSimuladorProveedorFinanciero;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * En pruebas se usa el simulador directamente para no levantar un servidor
 * HTTP. El contrato HTTP se sigue probando con WireMock y MockMvc.
 */
@Component
@Profile("pruebas")
@Primary
@RequiredArgsConstructor
public class ProveedorFinancieroSimuladoDirecto
    implements PuertoProveedorFinanciero {

    private final ServicioSimuladorProveedorFinanciero simulador;
    private final JdbcTemplate jdbcTemplate;

    private final AtomicBoolean primeraLlamada = new AtomicBoolean(true);
    private final AtomicBoolean sinTransaccion = new AtomicBoolean(false);
    private final AtomicReference<String> estadoIntento =
        new AtomicReference<>();
    private final AtomicInteger reservas = new AtomicInteger();
    private final AtomicInteger confirmaciones = new AtomicInteger();

    @Override
    public ResultadoOperacionFinanciera reservar(OperacionFinanciera operacion) {
        registrarPrimeraLlamada(operacion);
        reservas.incrementAndGet();
        return simulador.reservar(operacion);
    }

    @Override
    public ResultadoOperacionFinanciera confirmar(OperacionFinanciera operacion) {
        confirmaciones.incrementAndGet();
        return simulador.confirmar(operacion);
    }

    @Override
    public ResultadoOperacionFinanciera consultar(String clave) {
        return simulador.consultar(clave);
    }

    @Override
    public ResultadoOperacionFinanciera liberar(OperacionFinanciera operacion) {
        return simulador.liberar(operacion);
    }

    public boolean invocadoSinTransaccion() {
        return sinTransaccion.get();
    }

    public String estadoIntentoEnPrimeraLlamada() {
        return estadoIntento.get();
    }

    public int reservas() {
        return reservas.get();
    }

    public int confirmaciones() {
        return confirmaciones.get();
    }

    public void reiniciar() {
        primeraLlamada.set(true);
        sinTransaccion.set(false);
        estadoIntento.set(null);
        reservas.set(0);
        confirmaciones.set(0);
    }

    private void registrarPrimeraLlamada(OperacionFinanciera operacion) {
        if (!primeraLlamada.compareAndSet(true, false)) {
            return;
        }
        sinTransaccion.set(
            !TransactionSynchronizationManager.isActualTransactionActive());
        estadoIntento.set(jdbcTemplate.queryForObject(
            "SELECT inteejec_estado FROM intento_ejecucion "
                + "WHERE inteejec_revisoli_id = ?",
            String.class,
            operacion.revisionId()
        ));
    }
}
