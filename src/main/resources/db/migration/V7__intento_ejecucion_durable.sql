ALTER TABLE intento_ejecucion
    ADD COLUMN inteejec_intentos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN inteejec_reclamado_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN inteejec_reclamado_por VARCHAR(100),
    ADD COLUMN inteejec_ultimo_error VARCHAR(1000),
    ADD COLUMN inteejec_proximo_intento_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN inteejec_reserva_referencia VARCHAR(100),
    ADD COLUMN inteejec_confirmacion_referencia VARCHAR(100);

ALTER TABLE intento_ejecucion
    ADD CONSTRAINT ck_intento_ejecucion_estado CHECK (
        inteejec_estado IN (
            'PENDIENTE',
            'ENVIANDO',
            'CONFIRMADA',
            'RESULTADO_DESCONOCIDO',
            'FALLIDA',
            'COMPENSANDO',
            'COMPENSADA'
        )
    );

CREATE INDEX idx_intento_reclamacion
    ON intento_ejecucion (inteejec_estado, inteejec_creado_en);
