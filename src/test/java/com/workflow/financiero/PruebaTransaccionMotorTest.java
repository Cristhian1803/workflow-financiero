package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workflow.financiero.aplicacion.servicio.ServicioTransaccionMotorPrueba;
import com.workflow.financiero.dominio.excepcion.ErrorSimuladoPrueba;
import com.workflow.financiero.dominio.modelo.SolicitudPrueba;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PruebaTransaccionMotorTest extends PruebaIntegracionBase {

    @Autowired
    ServicioTransaccionMotorPrueba servicio;

    @Test
    @DisplayName("Al iniciar la solicitud se confirman la entidad y la instancia del proceso")
    void commitAlIniciarSolicitudYProceso() {
        UUID solicitudId = UUID.randomUUID();
        UUID creada = servicio.iniciarSolicitudYProceso(solicitudId, false);

        assertThat(creada).isEqualTo(solicitudId);
        SolicitudPrueba solicitud = servicio.buscarSolicitud(solicitudId).orElseThrow();
        assertThat(solicitud.getIdentificadorInstanciaProceso()).isNotBlank();
        assertThat(servicio.contarInstanciasActivasPorSolicitud(solicitudId)).isEqualTo(1);
        assertThat(servicio.contarTareasActivasPorSolicitud(solicitudId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Si falla el inicio, no queda ni la solicitud ni la instancia")
    void rollbackAlIniciarSolicitudYProceso() {
        UUID solicitudId = UUID.randomUUID();
        assertThatThrownBy(() -> servicio.iniciarSolicitudYProceso(solicitudId, true))
            .isInstanceOf(ErrorSimuladoPrueba.class);

        assertThat(servicio.buscarSolicitud(solicitudId)).isEmpty();
        assertThat(servicio.contarInstanciasActivasPorSolicitud(solicitudId)).isZero();
    }

    @Test
    @DisplayName("Al completar la tarea se confirman el evento y el avance del proceso")
    void commitAlCompletarTarea() {
        UUID solicitudId = UUID.randomUUID();
        servicio.iniciarSolicitudYProceso(solicitudId, false);

        servicio.registrarEventoYCompletarTarea(solicitudId, false);

        SolicitudPrueba solicitud = servicio.buscarSolicitud(solicitudId).orElseThrow();
        assertThat(solicitud.getEstado()).isEqualTo("TAREA_COMPLETADA");
        assertThat(servicio.contarEventos(solicitudId)).isEqualTo(1);
        assertThat(servicio.contarInstanciasActivasPorSolicitud(solicitudId)).isZero();
        assertThat(servicio.contarTareasActivasPorSolicitud(solicitudId)).isZero();
    }

    @Test
    @DisplayName("Si falla al completar, la tarea y el evento permanecen sin cambios")
    void rollbackAlCompletarTarea() {
        UUID solicitudId = UUID.randomUUID();
        servicio.iniciarSolicitudYProceso(solicitudId, false);

        assertThatThrownBy(() -> servicio.registrarEventoYCompletarTarea(solicitudId, true))
            .isInstanceOf(ErrorSimuladoPrueba.class);

        SolicitudPrueba solicitud = servicio.buscarSolicitud(solicitudId).orElseThrow();
        assertThat(solicitud.getEstado()).isEqualTo("INICIADA");
        assertThat(servicio.contarEventos(solicitudId)).isZero();
        assertThat(servicio.contarTareasActivasPorSolicitud(solicitudId)).isEqualTo(1);
    }
}
