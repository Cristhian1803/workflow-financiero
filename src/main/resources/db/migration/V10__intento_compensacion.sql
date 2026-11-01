CREATE TABLE intento_compensacion (
    intecomp_id UUID NOT NULL,
    intecomp_solifina_id UUID NOT NULL,
    intecomp_revisoli_id UUID NOT NULL,
    intecomp_clave VARCHAR(100) NOT NULL,
    intecomp_huella VARCHAR(128) NOT NULL,
    intecomp_estado VARCHAR(30) NOT NULL,
    intecomp_despacho_en_curso BOOLEAN NOT NULL DEFAULT FALSE,
    intecomp_motivo VARCHAR(1000) NOT NULL,
    intecomp_actor_usua_id UUID NOT NULL,
    intecomp_referencia VARCHAR(100),
    intecomp_ultimo_error VARCHAR(1000),
    intecomp_intentos INTEGER NOT NULL DEFAULT 0,
    intecomp_proximo_intento_en TIMESTAMP WITH TIME ZONE,
    intecomp_reclamado_en TIMESTAMP WITH TIME ZONE,
    intecomp_reclamado_por VARCHAR(100),
    intecomp_version BIGINT NOT NULL DEFAULT 0,
    intecomp_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    intecomp_actualizado_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_intento_compensacion PRIMARY KEY (intecomp_id),
    CONSTRAINT uq_intento_compensacion_revision
        UNIQUE (intecomp_revisoli_id),
    CONSTRAINT uq_intento_compensacion_clave UNIQUE (intecomp_clave),
    CONSTRAINT ck_intento_compensacion_estado CHECK (
        intecomp_estado IN (
            'PENDIENTE',
            'LIBERANDO',
            'LIBERADA',
            'CANCELADA',
            'RECHAZADA',
            'FALLIDA',
            'REVISION_MANUAL'
        )
    ),
    CONSTRAINT fk_intento_compensacion_solicitud
        FOREIGN KEY (intecomp_solifina_id)
        REFERENCES solicitud_financiera (solifina_id) ON DELETE RESTRICT,
    CONSTRAINT fk_intento_compensacion_revision
        FOREIGN KEY (intecomp_revisoli_id)
        REFERENCES revision_solicitud (revisoli_id) ON DELETE RESTRICT,
    CONSTRAINT fk_intento_compensacion_actor
        FOREIGN KEY (intecomp_actor_usua_id)
        REFERENCES usuario (usua_id) ON DELETE RESTRICT
);

CREATE INDEX idx_intento_compensacion_reclamable
    ON intento_compensacion (intecomp_estado, intecomp_proximo_intento_en);
