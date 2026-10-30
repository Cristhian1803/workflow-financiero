package com.workflow.financiero.aplicacion.puerto;

import java.util.List;
import java.util.Map;

/**
 * Operaciones del motor de procesos que necesita la aplicación. La API y los
 * servicios no dependen de las clases concretas de Flowable.
 */
public interface PuertoProceso {

    String iniciar(String claveProceso, String claveNegocio, Map<String, Object> variables);

    void completarTarea(String idTareaMotor, Map<String, Object> variables);

    void asignarTarea(String idTareaMotor, String usuario);

    List<TareaMotor> tareasActivas(String idProceso);

    List<String> actividadesActivas(String idProceso);

    boolean estaActivo(String idProceso);

    boolean estaFinalizado(String idProceso);

    void enviarMensaje(String nombreMensaje, String idProceso, Map<String, Object> variables);
}
