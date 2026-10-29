CREATE TABLE usuario (
    usua_id UUID NOT NULL,
    usua_usuario VARCHAR(50) NOT NULL,
    usua_clave_hash VARCHAR(100) NOT NULL,
    usua_nombre VARCHAR(100) NOT NULL,
    usua_perfries_codigo VARCHAR(30) NOT NULL,
    usua_activo BOOLEAN NOT NULL DEFAULT TRUE,
    usua_version BIGINT NOT NULL DEFAULT 0,
    usua_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    usua_actualizado_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_usuario PRIMARY KEY (usua_id),
    CONSTRAINT uq_usuario_usuario UNIQUE (usua_usuario),
    CONSTRAINT fk_usuario_perfil FOREIGN KEY (usua_perfries_codigo)
        REFERENCES perfil_riesgo (perfries_codigo) ON DELETE RESTRICT
);

CREATE TABLE usuario_rol (
    usurol_usua_id UUID NOT NULL,
    usurol_rol_codigo VARCHAR(30) NOT NULL,
    usurol_asignado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_usuario_rol PRIMARY KEY (usurol_usua_id, usurol_rol_codigo),
    CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usurol_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT,
    CONSTRAINT fk_usuario_rol_rol FOREIGN KEY (usurol_rol_codigo)
        REFERENCES rol (rol_codigo) ON DELETE RESTRICT
);

CREATE TABLE solicitud_financiera (
    solifina_id UUID NOT NULL,
    solifina_solicitante_usua_id UUID NOT NULL,
    solifina_revisoli_actual_id UUID,
    solifina_version BIGINT NOT NULL DEFAULT 0,
    solifina_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    solifina_actualizado_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_solicitud_financiera PRIMARY KEY (solifina_id),
    CONSTRAINT fk_solicitud_solicitante FOREIGN KEY (solifina_solicitante_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT,
    CONSTRAINT uq_solicitud_revision_actual UNIQUE (solifina_revisoli_actual_id)
);

CREATE TABLE revision_solicitud (
    revisoli_id UUID NOT NULL,
    revisoli_solifina_id UUID NOT NULL,
    revisoli_numero INTEGER NOT NULL,
    revisoli_tipooper_codigo VARCHAR(30) NOT NULL,
    revisoli_monto NUMERIC(19, 0) NOT NULL,
    revisoli_mone_codigo CHAR(3) NOT NULL,
    revisoli_descripcion VARCHAR(500) NOT NULL,
    revisoli_justificacion VARCHAR(1000) NOT NULL,
    revisoli_nivel_riesgo VARCHAR(10),
    revisoli_estado VARCHAR(30) NOT NULL,
    revisoli_identificador_proceso VARCHAR(64),
    revisoli_version BIGINT NOT NULL DEFAULT 0,
    revisoli_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    revisoli_actualizado_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_revision_solicitud PRIMARY KEY (revisoli_id),
    CONSTRAINT fk_revision_solicitud FOREIGN KEY (revisoli_solifina_id)
        REFERENCES solicitud_financiera (solifina_id) ON DELETE RESTRICT,
    CONSTRAINT fk_revision_tipo_operacion FOREIGN KEY (revisoli_tipooper_codigo)
        REFERENCES tipo_operacion (tipooper_codigo) ON DELETE RESTRICT,
    CONSTRAINT fk_revision_moneda FOREIGN KEY (revisoli_mone_codigo)
        REFERENCES moneda (mone_codigo) ON DELETE RESTRICT,
    CONSTRAINT uq_revision_numero UNIQUE (revisoli_solifina_id, revisoli_numero),
    CONSTRAINT uq_revision_solicitud_id UNIQUE (revisoli_solifina_id, revisoli_id),
    CONSTRAINT ck_revision_monto CHECK (revisoli_monto > 0),
    CONSTRAINT ck_revision_nivel_riesgo CHECK (
        revisoli_nivel_riesgo IS NULL
        OR revisoli_nivel_riesgo IN ('BAJO', 'MEDIO', 'ALTO')
    ),
    CONSTRAINT ck_revision_estado CHECK (
        revisoli_estado IN (
            'RECIBIDA', 'VALIDANDO', 'EVALUANDO_RIESGO', 'PENDIENTE_APROBACION',
            'APROBADA', 'EJECUTANDO', 'RESULTADO_DESCONOCIDO', 'COMPENSANDO',
            'REVISION_MANUAL', 'RECHAZADA', 'CANCELADA', 'EJECUTADA'
        )
    )
);

ALTER TABLE solicitud_financiera
    ADD CONSTRAINT fk_solicitud_revision_actual
    FOREIGN KEY (solifina_id, solifina_revisoli_actual_id)
    REFERENCES revision_solicitud (revisoli_solifina_id, revisoli_id)
    ON DELETE RESTRICT;

CREATE TABLE evento_solicitud (
    evensoli_id UUID NOT NULL,
    evensoli_solifina_id UUID NOT NULL,
    evensoli_revisoli_id UUID NOT NULL,
    evensoli_tipo VARCHAR(50) NOT NULL,
    evensoli_actor_usua_id UUID,
    evensoli_rol_codigo VARCHAR(30),
    evensoli_estado_anterior VARCHAR(30),
    evensoli_estado_nuevo VARCHAR(30),
    evensoli_correlacion VARCHAR(64),
    evensoli_detalle VARCHAR(2000),
    evensoli_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_evento_solicitud PRIMARY KEY (evensoli_id),
    CONSTRAINT fk_evento_solicitud FOREIGN KEY (evensoli_solifina_id)
        REFERENCES solicitud_financiera (solifina_id) ON DELETE RESTRICT,
    CONSTRAINT fk_evento_actor FOREIGN KEY (evensoli_actor_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT,
    CONSTRAINT fk_evento_rol FOREIGN KEY (evensoli_rol_codigo)
        REFERENCES rol (rol_codigo) ON DELETE RESTRICT,
    CONSTRAINT fk_evento_revision_solicitud
        FOREIGN KEY (evensoli_solifina_id, evensoli_revisoli_id)
        REFERENCES revision_solicitud (revisoli_solifina_id, revisoli_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_evento_estado_anterior CHECK (
        evensoli_estado_anterior IS NULL
        OR evensoli_estado_anterior IN (
            'RECIBIDA', 'VALIDANDO', 'EVALUANDO_RIESGO', 'PENDIENTE_APROBACION',
            'APROBADA', 'EJECUTANDO', 'RESULTADO_DESCONOCIDO', 'COMPENSANDO',
            'REVISION_MANUAL', 'RECHAZADA', 'CANCELADA', 'EJECUTADA'
        )
    ),
    CONSTRAINT ck_evento_estado_nuevo CHECK (
        evensoli_estado_nuevo IS NULL
        OR evensoli_estado_nuevo IN (
            'RECIBIDA', 'VALIDANDO', 'EVALUANDO_RIESGO', 'PENDIENTE_APROBACION',
            'APROBADA', 'EJECUTANDO', 'RESULTADO_DESCONOCIDO', 'COMPENSANDO',
            'REVISION_MANUAL', 'RECHAZADA', 'CANCELADA', 'EJECUTADA'
        )
    )
);

CREATE TABLE registro_actor_rol (
    regiactorol_solifina_id UUID NOT NULL,
    regiactorol_usua_id UUID NOT NULL,
    regiactorol_rol_codigo VARCHAR(30) NOT NULL,
    regiactorol_fecha TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_registro_actor_rol PRIMARY KEY (regiactorol_solifina_id, regiactorol_usua_id),
    CONSTRAINT fk_registro_actor_rol_solicitud FOREIGN KEY (regiactorol_solifina_id)
        REFERENCES solicitud_financiera (solifina_id) ON DELETE RESTRICT,
    CONSTRAINT fk_registro_actor_rol_usuario FOREIGN KEY (regiactorol_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT,
    CONSTRAINT fk_registro_actor_rol_rol FOREIGN KEY (regiactorol_rol_codigo)
        REFERENCES rol (rol_codigo) ON DELETE RESTRICT
);

CREATE INDEX idx_solicitud_solicitante ON solicitud_financiera (solifina_solicitante_usua_id);
CREATE INDEX idx_revision_solicitud ON revision_solicitud (revisoli_solifina_id);
CREATE INDEX idx_revision_estado ON revision_solicitud (revisoli_estado);
CREATE INDEX idx_usuario_rol_rol ON usuario_rol (usurol_rol_codigo);
CREATE INDEX idx_evento_solicitud ON evento_solicitud (evensoli_solifina_id, evensoli_creado_en);
CREATE INDEX idx_evento_revision ON evento_solicitud (evensoli_revisoli_id, evensoli_creado_en);
CREATE INDEX idx_registro_actor_rol_rol ON registro_actor_rol (regiactorol_rol_codigo);
