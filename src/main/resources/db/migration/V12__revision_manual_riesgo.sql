CREATE TABLE tarea_revision_riesgo (
    tarereviries_id UUID NOT NULL,
    tarereviries_revisoli_id UUID NOT NULL,
    tarereviries_identificador_tarea_motor VARCHAR(64),
    tarereviries_estado VARCHAR(20) NOT NULL,
    tarereviries_nivel VARCHAR(10),
    tarereviries_motivo VARCHAR(1000),
    tarereviries_evidencia VARCHAR(1000),
    tarereviries_actor_usua_id UUID,
    tarereviries_version BIGINT NOT NULL DEFAULT 0,
    tarereviries_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    tarereviries_actualizado_en TIMESTAMP WITH TIME ZONE,
    tarereviries_resuelto_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_tarea_revision_riesgo PRIMARY KEY (tarereviries_id),
    CONSTRAINT uq_tarea_revision_riesgo_revision
        UNIQUE (tarereviries_revisoli_id),
    CONSTRAINT uq_tarea_revision_riesgo_motor
        UNIQUE (tarereviries_identificador_tarea_motor),
    CONSTRAINT fk_tarea_revision_riesgo_revision
        FOREIGN KEY (tarereviries_revisoli_id)
        REFERENCES revision_solicitud (revisoli_id) ON DELETE RESTRICT,
    CONSTRAINT fk_tarea_revision_riesgo_actor
        FOREIGN KEY (tarereviries_actor_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT,
    CONSTRAINT ck_tarea_revision_riesgo_estado CHECK (
        tarereviries_estado IN ('PENDIENTE', 'RESUELTA', 'CANCELADA')
    ),
    CONSTRAINT ck_tarea_revision_riesgo_nivel CHECK (
        tarereviries_nivel IS NULL
        OR tarereviries_nivel IN ('BAJO', 'MEDIO', 'ALTO')
    )
);

CREATE INDEX idx_tarea_revision_riesgo_estado
    ON tarea_revision_riesgo (tarereviries_estado);

CREATE UNIQUE INDEX uq_evaluacion_manual_vigente
    ON evaluacion_riesgo (evalries_revisoli_id)
    WHERE evalries_origen = 'MANUAL'
      AND evalries_estado = 'MANUAL';
