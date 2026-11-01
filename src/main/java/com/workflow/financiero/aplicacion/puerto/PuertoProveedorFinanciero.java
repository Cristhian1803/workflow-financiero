package com.workflow.financiero.aplicacion.puerto;

import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;

/**
 * Contrato con el proveedor de ejecución financiera. Un reintento debe
 * conservar la misma clave y huella de la operación original.
 */
public interface PuertoProveedorFinanciero {

    ResultadoOperacionFinanciera reservar(OperacionFinanciera operacion);

    ResultadoOperacionFinanciera confirmar(OperacionFinanciera operacion);

    ResultadoOperacionFinanciera consultar(String clave);

    ResultadoOperacionFinanciera liberar(OperacionFinanciera operacion);
}
