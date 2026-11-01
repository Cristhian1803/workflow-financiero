CREATE TABLE tarea_aprobacion (
    tareapro_id UUID NOT NULL,
    tareapro_revisoli_id UUID NOT NULL,
    tareapro_rol_codigo VARCHAR(30) NOT NULL,
    tareapro_identificador_tarea_motor VARCHAR(64),
    tareapro_asignado_usua_id UUID,
    tareapro_estado VARCHAR(20) NOT NULL,
    tareapro_fecha_vencimiento TIMESTAMP WITH TIME ZONE,
    tareapro_version BIGINT NOT NULL DEFAULT 0,
    tareapro_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    tareapro_actualizado_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_tarea_aprobacion PRIMARY KEY (tareapro_id),
    CONSTRAINT uq_tarea_revision_rol UNIQUE (tareapro_revisoli_id, tareapro_rol_codigo),
    CONSTRAINT uq_tarea_motor UNIQUE (tareapro_identificador_tarea_motor),
    CONSTRAINT fk_tarea_revision FOREIGN KEY (tareapro_revisoli_id)
        REFERENCES revision_solicitud (revisoli_id) ON DELETE RESTRICT,
    CONSTRAINT fk_tarea_rol FOREIGN KEY (tareapro_rol_codigo)
        REFERENCES rol (rol_codigo) ON DELETE RESTRICT,
    CONSTRAINT fk_tarea_asignado FOREIGN KEY (tareapro_asignado_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT,
    CONSTRAINT ck_tarea_estado CHECK (
        tareapro_estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA')
    )
);

CREATE TABLE decision_aprobacion (
    deciapro_id UUID NOT NULL,
    deciapro_tareapro_id UUID NOT NULL,
    deciapro_actor_usua_id UUID NOT NULL,
    deciapro_decision VARCHAR(10) NOT NULL,
    deciapro_motivo VARCHAR(1000),
    deciapro_delegado BOOLEAN NOT NULL DEFAULT FALSE,
    deciapro_fecha TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_decision_aprobacion PRIMARY KEY (deciapro_id),
    CONSTRAINT uq_decision_tarea UNIQUE (deciapro_tareapro_id),
    CONSTRAINT fk_decision_tarea FOREIGN KEY (deciapro_tareapro_id)
        REFERENCES tarea_aprobacion (tareapro_id) ON DELETE RESTRICT,
    CONSTRAINT fk_decision_actor FOREIGN KEY (deciapro_actor_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT,
    CONSTRAINT ck_decision_tipo CHECK (
        deciapro_decision IN ('APROBADA', 'RECHAZADA')
    )
);

CREATE TABLE evaluacion_riesgo (
    evalries_id UUID NOT NULL,
    evalries_revisoli_id UUID NOT NULL,
    evalries_origen VARCHAR(20) NOT NULL,
    evalries_nivel VARCHAR(10) NOT NULL,
    evalries_resultado VARCHAR(1000),
    evalries_correlacion VARCHAR(64),
    evalries_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_evaluacion_riesgo PRIMARY KEY (evalries_id),
    CONSTRAINT fk_evaluacion_revision FOREIGN KEY (evalries_revisoli_id)
        REFERENCES revision_solicitud (revisoli_id) ON DELETE RESTRICT,
    CONSTRAINT ck_evaluacion_origen CHECK (
        evalries_origen IN ('INTERNO', 'EXTERNO', 'MANUAL')
    ),
    CONSTRAINT ck_evaluacion_nivel CHECK (
        evalries_nivel IN ('BAJO', 'MEDIO', 'ALTO')
    )
);

CREATE TABLE intento_ejecucion (
    inteejec_id UUID NOT NULL,
    inteejec_revisoli_id UUID NOT NULL,
    inteejec_clave VARCHAR(100) NOT NULL,
    inteejec_huella VARCHAR(128) NOT NULL,
    inteejec_estado VARCHAR(30) NOT NULL,
    inteejec_referencia_externa VARCHAR(100),
    inteejec_version BIGINT NOT NULL DEFAULT 0,
    inteejec_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    inteejec_actualizado_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_intento_ejecucion PRIMARY KEY (inteejec_id),
    CONSTRAINT uq_intento_revision UNIQUE (inteejec_revisoli_id),
    CONSTRAINT uq_intento_clave UNIQUE (inteejec_clave),
    CONSTRAINT fk_intento_revision FOREIGN KEY (inteejec_revisoli_id)
        REFERENCES revision_solicitud (revisoli_id) ON DELETE RESTRICT
);

CREATE TABLE evento_externo (
    evenexte_id UUID NOT NULL,
    evenexte_proveedor VARCHAR(50) NOT NULL,
    evenexte_identificador_evento VARCHAR(100) NOT NULL,
    evenexte_solifina_id UUID,
    evenexte_revisoli_id UUID,
    evenexte_huella VARCHAR(128) NOT NULL,
    evenexte_estado VARCHAR(30) NOT NULL,
    evenexte_recibido_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_evento_externo PRIMARY KEY (evenexte_id),
    CONSTRAINT uq_evento_externo UNIQUE (evenexte_proveedor, evenexte_identificador_evento),
    CONSTRAINT fk_evento_externo_solicitud FOREIGN KEY (evenexte_solifina_id)
        REFERENCES solicitud_financiera (solifina_id) ON DELETE RESTRICT,
    CONSTRAINT fk_evento_externo_revision FOREIGN KEY (evenexte_revisoli_id)
        REFERENCES revision_solicitud (revisoli_id) ON DELETE RESTRICT,
    CONSTRAINT fk_evento_externo_revision_solicitud
        FOREIGN KEY (evenexte_solifina_id, evenexte_revisoli_id)
        REFERENCES revision_solicitud (revisoli_solifina_id, revisoli_id)
        ON DELETE RESTRICT
);

CREATE TABLE notificacion (
    noti_id UUID NOT NULL,
    noti_tareapro_id UUID,
    noti_destinatario_usua_id UUID,
    noti_destinatario_rol_codigo VARCHAR(30),
    noti_tipo VARCHAR(30) NOT NULL,
    noti_contenido VARCHAR(1000) NOT NULL,
    noti_estado VARCHAR(20) NOT NULL,
    noti_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    noti_leida_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_notificacion PRIMARY KEY (noti_id),
    CONSTRAINT fk_notificacion_tarea FOREIGN KEY (noti_tareapro_id)
        REFERENCES tarea_aprobacion (tareapro_id) ON DELETE RESTRICT,
    CONSTRAINT fk_notificacion_usuario FOREIGN KEY (noti_destinatario_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT,
    CONSTRAINT fk_notificacion_rol FOREIGN KEY (noti_destinatario_rol_codigo)
        REFERENCES rol (rol_codigo) ON DELETE RESTRICT,
    CONSTRAINT ck_notificacion_estado CHECK (
        noti_estado IN ('PENDIENTE', 'LEIDA')
    ),
    CONSTRAINT ck_notificacion_destinatario CHECK (
        noti_destinatario_usua_id IS NOT NULL
        OR noti_destinatario_rol_codigo IS NOT NULL
    )
);

CREATE TABLE registro_idempotencia (
    regiidem_id UUID NOT NULL,
    regiidem_usua_id UUID NOT NULL,
    regiidem_operacion VARCHAR(50) NOT NULL,
    regiidem_clave VARCHAR(100) NOT NULL,
    regiidem_huella VARCHAR(128) NOT NULL,
    regiidem_codigo_respuesta INTEGER,
    regiidem_cuerpo_respuesta VARCHAR(4000),
    regiidem_expira_en TIMESTAMP WITH TIME ZONE NOT NULL,
    regiidem_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_registro_idempotencia PRIMARY KEY (regiidem_id),
    CONSTRAINT uq_idempotencia_clave
        UNIQUE (regiidem_usua_id, regiidem_operacion, regiidem_clave),
    CONSTRAINT fk_idempotencia_usuario FOREIGN KEY (regiidem_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT
);

CREATE INDEX idx_tarea_estado ON tarea_aprobacion (tareapro_estado);
CREATE INDEX idx_tarea_asignado ON tarea_aprobacion (tareapro_asignado_usua_id);
CREATE INDEX idx_decision_actor ON decision_aprobacion (deciapro_actor_usua_id);
CREATE INDEX idx_evaluacion_revision ON evaluacion_riesgo (evalries_revisoli_id);
CREATE INDEX idx_intento_estado ON intento_ejecucion (inteejec_estado);
CREATE INDEX idx_evento_externo_solicitud ON evento_externo (evenexte_solifina_id);
CREATE INDEX idx_evento_externo_revision ON evento_externo (evenexte_revisoli_id);
CREATE INDEX idx_evento_externo_recibido ON evento_externo (evenexte_recibido_en);
CREATE INDEX idx_notificacion_usuario ON notificacion (noti_destinatario_usua_id);
CREATE INDEX idx_notificacion_rol ON notificacion (noti_destinatario_rol_codigo);
CREATE INDEX idx_notificacion_estado ON notificacion (noti_estado);
CREATE INDEX idx_idempotencia_expira ON registro_idempotencia (regiidem_expira_en);

CREATE UNIQUE INDEX uq_notificacion_usuario
    ON notificacion (noti_tareapro_id, noti_tipo, noti_destinatario_usua_id)
    WHERE noti_destinatario_usua_id IS NOT NULL;

CREATE UNIQUE INDEX uq_notificacion_rol
    ON notificacion (noti_tareapro_id, noti_tipo, noti_destinatario_rol_codigo)
    WHERE noti_destinatario_rol_codigo IS NOT NULL;
