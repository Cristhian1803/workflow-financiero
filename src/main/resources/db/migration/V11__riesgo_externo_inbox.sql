ALTER TABLE evaluacion_riesgo
    ALTER COLUMN evalries_nivel DROP NOT NULL;

ALTER TABLE evaluacion_riesgo
    ADD COLUMN evalries_estado VARCHAR(20) NOT NULL DEFAULT 'RECIBIDA',
    ADD COLUMN evalries_clave VARCHAR(100),
    ADD COLUMN evalries_proveedor VARCHAR(50),
    ADD COLUMN evalries_identificador_evento VARCHAR(100),
    ADD COLUMN evalries_referencia_solicitud VARCHAR(100),
    ADD COLUMN evalries_intentos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN evalries_proximo_intento_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN evalries_reclamado_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN evalries_reclamado_por VARCHAR(100),
    ADD COLUMN evalries_error VARCHAR(1000),
    ADD COLUMN evalries_procesado_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN evalries_actualizado_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN evalries_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE evaluacion_riesgo
    ADD CONSTRAINT ck_evaluacion_estado CHECK (
        evalries_estado IN (
            'PENDIENTE',
            'RECIBIDA',
            'VENCIDA',
            'MANUAL',
            'OBSOLETA'
        )
    );

CREATE UNIQUE INDEX uq_evaluacion_externa_vigente
    ON evaluacion_riesgo (evalries_revisoli_id)
    WHERE evalries_origen = 'EXTERNO'
      AND evalries_estado IN ('PENDIENTE', 'RECIBIDA');

CREATE INDEX idx_evaluacion_externa_reclamable
    ON evaluacion_riesgo (evalries_estado, evalries_proximo_intento_en);

UPDATE evento_externo
    SET evenexte_estado = 'PENDIENTE'
    WHERE evenexte_estado = 'RECIBIDO';

ALTER TABLE evento_externo
    ADD COLUMN evenexte_identificador_solicitud UUID,
    ADD COLUMN evenexte_numero_revision INTEGER,
    ADD COLUMN evenexte_nivel_riesgo VARCHAR(10),
    ADD COLUMN evenexte_resultado VARCHAR(1000),
    ADD COLUMN evenexte_fecha_proveedor TIMESTAMP WITH TIME ZONE,
    ADD COLUMN evenexte_correlacion VARCHAR(64),
    ADD COLUMN evenexte_intentos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN evenexte_proximo_intento_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN evenexte_error VARCHAR(1000),
    ADD COLUMN evenexte_procesado_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN evenexte_actualizado_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN evenexte_conflictos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN evenexte_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE evento_externo
    ADD CONSTRAINT ck_evento_externo_estado CHECK (
        evenexte_estado IN (
            'PENDIENTE',
            'PROCESADO',
            'DUPLICADO',
            'CONFLICTO',
            'OBSOLETO'
        )
    );

ALTER TABLE evento_externo
    ADD CONSTRAINT ck_evento_externo_nivel CHECK (
        evenexte_nivel_riesgo IS NULL
        OR evenexte_nivel_riesgo IN ('BAJO', 'MEDIO', 'ALTO')
    );

CREATE INDEX idx_evento_externo_pendiente
    ON evento_externo (evenexte_estado, evenexte_proximo_intento_en);
