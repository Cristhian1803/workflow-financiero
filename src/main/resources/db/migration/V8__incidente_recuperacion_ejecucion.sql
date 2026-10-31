CREATE TABLE incidente (
    inci_id UUID NOT NULL,
    inci_solifina_id UUID NOT NULL,
    inci_revisoli_id UUID NOT NULL,
    inci_tipo VARCHAR(30) NOT NULL,
    inci_referencia UUID NOT NULL,
    inci_estado VARCHAR(20) NOT NULL,
    inci_motivo VARCHAR(1000) NOT NULL,
    inci_actor_usua_id UUID,
    inci_reintentable BOOLEAN NOT NULL DEFAULT TRUE,
    inci_reintentos INTEGER NOT NULL DEFAULT 0,
    inci_ultimo_resultado VARCHAR(1000),
    inci_version BIGINT NOT NULL DEFAULT 0,
    inci_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    inci_actualizado_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_incidente PRIMARY KEY (inci_id),
    CONSTRAINT uq_incidente_causa UNIQUE (inci_tipo, inci_referencia),
    CONSTRAINT fk_incidente_solicitud FOREIGN KEY (inci_solifina_id)
        REFERENCES solicitud_financiera (solifina_id) ON DELETE RESTRICT,
    CONSTRAINT fk_incidente_revision FOREIGN KEY (inci_revisoli_id)
        REFERENCES revision_solicitud (revisoli_id) ON DELETE RESTRICT,
    CONSTRAINT fk_incidente_revision_solicitud
        FOREIGN KEY (inci_solifina_id, inci_revisoli_id)
        REFERENCES revision_solicitud (revisoli_solifina_id, revisoli_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_incidente_actor FOREIGN KEY (inci_actor_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT,
    CONSTRAINT ck_incidente_tipo CHECK (
        inci_tipo IN ('EJECUCION', 'COMPENSACION', 'RIESGO_EXTERNO')
    ),
    CONSTRAINT ck_incidente_estado CHECK (
        inci_estado IN ('ABIERTO', 'REINTENTANDO', 'RESUELTO', 'CERRADO')
    )
);

CREATE INDEX idx_incidente_estado ON incidente (inci_estado, inci_creado_en);
CREATE INDEX idx_incidente_revision ON incidente (inci_revisoli_id);
CREATE INDEX idx_incidente_referencia ON incidente (inci_referencia);

CREATE INDEX idx_intento_recuperacion
    ON intento_ejecucion (
        inteejec_estado,
        inteejec_proximo_intento_en,
        inteejec_reclamado_en
    );
