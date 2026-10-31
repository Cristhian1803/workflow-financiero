package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;

import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ClavesOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.HuellaOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PruebaClavesOperacionFinancieraTest {

    private static final UUID SOLICITUD_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000001");
    private static final UUID REVISION_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000002");

    @Test
    @DisplayName("Las claves son estables y distintas por acción")
    void clavesEstablesYDistintasPorAccion() {
        String reserva = ClavesOperacionFinanciera.reserva(
            SOLICITUD_ID, REVISION_ID);
        String confirmacion = ClavesOperacionFinanciera.confirmacion(
            SOLICITUD_ID, REVISION_ID);
        String liberacion = ClavesOperacionFinanciera.liberacion(
            SOLICITUD_ID, REVISION_ID);

        assertThat(ClavesOperacionFinanciera.reserva(
            SOLICITUD_ID, REVISION_ID)).isEqualTo(reserva);
        assertThat(reserva).startsWith("reserva:");
        assertThat(confirmacion).startsWith("confirmacion:");
        assertThat(liberacion).startsWith("liberacion:");
        assertThat(reserva).isNotEqualTo(confirmacion);
        assertThat(confirmacion).isNotEqualTo(liberacion);
    }

    @Test
    @DisplayName("La huella es estable, hexadecimal y cambia con los parámetros")
    void huellaEstableYCambiaConLosParametros() {
        OperacionFinanciera original = OperacionFinanciera.crear(
            SOLICITUD_ID,
            REVISION_ID,
            AccionFinanciera.RESERVA,
            BigDecimal.valueOf(25_000_000),
            "PYG",
            "NORMAL"
        );
        OperacionFinanciera mismo = OperacionFinanciera.crear(
            SOLICITUD_ID,
            REVISION_ID,
            AccionFinanciera.RESERVA,
            BigDecimal.valueOf(25_000_000),
            "PYG",
            "NORMAL"
        );
        OperacionFinanciera otroMonto = OperacionFinanciera.crear(
            SOLICITUD_ID,
            REVISION_ID,
            AccionFinanciera.RESERVA,
            BigDecimal.valueOf(25_000_001),
            "PYG",
            "NORMAL"
        );
        OperacionFinanciera otraAccion = OperacionFinanciera.crear(
            SOLICITUD_ID,
            REVISION_ID,
            AccionFinanciera.CONFIRMACION,
            BigDecimal.valueOf(25_000_000),
            "PYG",
            "NORMAL"
        );

        assertThat(original.huella()).isEqualTo(mismo.huella());
        assertThat(original.huella()).matches("[0-9a-f]{64}");
        assertThat(original.huella()).isNotEqualTo(otroMonto.huella());
        assertThat(original.huella()).isNotEqualTo(otraAccion.huella());
        assertThat(HuellaOperacionFinanciera.calcular(original))
            .isEqualTo(original.huella());
    }
}
