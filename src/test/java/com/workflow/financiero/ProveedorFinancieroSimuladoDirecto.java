package com.workflow.financiero;

import com.workflow.financiero.aplicacion.excepcion.ConflictoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorDefinitivoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorRecuperableProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.OperacionNoEncontradaProveedorFinancieroException;
import com.workflow.financiero.aplicacion.puerto.PuertoProveedorFinanciero;
import com.workflow.financiero.aplicacion.simulador.ConfiguracionFallosSimulador;
import com.workflow.financiero.aplicacion.simulador.ServicioSimuladorProveedorFinanciero;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    private final ConfiguracionFallosSimulador fallos;
    private final JdbcTemplate jdbcTemplate;

    private final AtomicBoolean primeraLlamada = new AtomicBoolean(true);
    private final AtomicBoolean sinTransaccion = new AtomicBoolean(false);
    private final AtomicReference<String> estadoIntento =
        new AtomicReference<>();
    private final AtomicInteger reservas = new AtomicInteger();
    private final AtomicInteger confirmaciones = new AtomicInteger();
    private final AtomicInteger liberaciones = new AtomicInteger();
    private final AtomicInteger consultas = new AtomicInteger();

    private volatile CountDownLatch pausaReserva;
    private volatile CountDownLatch continuarReserva;
    private volatile CountDownLatch pausaConfirmacion;
    private volatile CountDownLatch continuarConfirmacion;
    private volatile CountDownLatch pausaEfectoConfirmacion;
    private volatile CountDownLatch continuarEfectoConfirmacion;

    @Override
    public ResultadoOperacionFinanciera reservar(OperacionFinanciera operacion) {
        prepararFallo(operacion);
        registrarPrimeraLlamada(operacion);
        reservas.incrementAndGet();
        esperarPausa(pausaReserva, continuarReserva);
        ResultadoOperacionFinanciera resultado = simulador.reservar(operacion);
        perderRespuestaSiCorresponde(operacion);
        return resultado;
    }

    @Override
    public ResultadoOperacionFinanciera confirmar(OperacionFinanciera operacion) {
        prepararFallo(operacion);
        confirmaciones.incrementAndGet();
        esperarPausa(pausaConfirmacion, continuarConfirmacion);
        ResultadoOperacionFinanciera resultado = simulador.confirmar(operacion);
        esperarPausa(
            pausaEfectoConfirmacion, continuarEfectoConfirmacion);
        perderRespuestaSiCorresponde(operacion);
        return resultado;
    }

    @Override
    public ResultadoOperacionFinanciera consultar(String clave) {
        consultas.incrementAndGet();
        if (fallos.consultaNoDisponible(clave)) {
            throw new ErrorRecuperableProveedorFinancieroException(
                "La consulta simulada no está disponible");
        }
        try {
            return simulador.consultar(clave);
        } catch (NoSuchElementException error) {
            throw new OperacionNoEncontradaProveedorFinancieroException(
                "El simulador no conoce la operación " + clave,
                error
            );
        }
    }

    @Override
    public ResultadoOperacionFinanciera liberar(OperacionFinanciera operacion) {
        prepararFallo(operacion);
        liberaciones.incrementAndGet();
        ResultadoOperacionFinanciera resultado = simulador.liberar(operacion);
        perderRespuestaSiCorresponde(operacion);
        return resultado;
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

    public int liberaciones() {
        return liberaciones.get();
    }

    public int consultas() {
        return consultas.get();
    }

    public void reiniciar() {
        primeraLlamada.set(true);
        sinTransaccion.set(false);
        estadoIntento.set(null);
        reservas.set(0);
        confirmaciones.set(0);
        liberaciones.set(0);
        consultas.set(0);
        pausaReserva = null;
        continuarReserva = null;
        pausaConfirmacion = null;
        continuarConfirmacion = null;
        pausaEfectoConfirmacion = null;
        continuarEfectoConfirmacion = null;
        fallos.limpiar();
    }

    public void pausarAntesDeReservar() {
        pausaReserva = new CountDownLatch(1);
        continuarReserva = new CountDownLatch(1);
    }

    public void esperarPausaReserva() throws InterruptedException {
        CountDownLatch pausa = pausaReserva;
        if (pausa != null && !pausa.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                "El trabajador no llegó a la pausa de reserva");
        }
    }

    public void liberarReserva() {
        CountDownLatch continuar = continuarReserva;
        if (continuar != null) {
            continuar.countDown();
        }
    }

    public void pausarAntesDeConfirmar() {
        pausaConfirmacion = new CountDownLatch(1);
        continuarConfirmacion = new CountDownLatch(1);
    }

    public void esperarPausaConfirmacion() throws InterruptedException {
        CountDownLatch pausa = pausaConfirmacion;
        if (pausa != null && !pausa.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                "El trabajador no llegó a la pausa de confirmación");
        }
    }

    public void liberarConfirmacion() {
        CountDownLatch continuar = continuarConfirmacion;
        if (continuar != null) {
            continuar.countDown();
        }
    }

    public void pausarDespuesDeConfirmar() {
        pausaEfectoConfirmacion = new CountDownLatch(1);
        continuarEfectoConfirmacion = new CountDownLatch(1);
    }

    public void esperarEfectoConfirmacion() throws InterruptedException {
        CountDownLatch pausa = pausaEfectoConfirmacion;
        if (pausa != null && !pausa.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                "El trabajador no llegó al efecto de confirmación");
        }
    }

    public void liberarEfectoConfirmacion() {
        CountDownLatch continuar = continuarEfectoConfirmacion;
        if (continuar != null) {
            continuar.countDown();
        }
    }

    private void esperarPausa(
        CountDownLatch pausa,
        CountDownLatch continuar
    ) {
        if (pausa == null || continuar == null) {
            return;
        }
        pausa.countDown();
        try {
            if (!continuar.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                    "La prueba no liberó la pausa del proveedor");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Se interrumpió la pausa del proveedor", error);
        }
    }

    private void prepararFallo(OperacionFinanciera operacion) {
        if (fallos.errorDefinitivo(operacion.clave())) {
            throw new ErrorDefinitivoProveedorFinancieroException(
                "Error definitivo simulado para " + operacion.clave());
        }
        if (fallos.conflictoIdempotencia(operacion.clave())) {
            throw new ConflictoProveedorFinancieroException(
                "Conflicto de idempotencia simulado para " + operacion.clave());
        }
        if (fallos.fallaAntesDelEfecto(
                operacion.clave(), operacion.accion())) {
            throw new ErrorRecuperableProveedorFinancieroException(
                "Fallo simulado antes del efecto para " + operacion.clave());
        }
    }

    private void perderRespuestaSiCorresponde(OperacionFinanciera operacion) {
        if (fallos.pierdeRespuesta(operacion.clave())) {
            throw new ErrorRecuperableProveedorFinancieroException(
                "La respuesta simulada se perdió después del efecto");
        }
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
