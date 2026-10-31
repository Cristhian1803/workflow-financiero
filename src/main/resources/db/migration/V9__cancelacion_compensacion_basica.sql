ALTER TABLE intento_ejecucion
    DROP CONSTRAINT ck_intento_ejecucion_estado;

ALTER TABLE intento_ejecucion
    ADD CONSTRAINT ck_intento_ejecucion_estado CHECK (
        inteejec_estado IN (
            'PENDIENTE',
            'ENVIANDO',
            'CONFIRMADA',
            'RESULTADO_DESCONOCIDO',
            'FALLIDA',
            'COMPENSANDO',
            'COMPENSADA',
            'CANCELADA'
        )
    );

ALTER TABLE intento_ejecucion
    ADD COLUMN inteejec_cancelacion_motivo VARCHAR(1000),
    ADD COLUMN inteejec_cancelacion_actor_usua_id UUID,
    ADD COLUMN inteejec_cancelacion_solicitada_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN inteejec_liberacion_referencia VARCHAR(100);

ALTER TABLE intento_ejecucion
    ADD CONSTRAINT fk_intento_cancelacion_actor
    FOREIGN KEY (inteejec_cancelacion_actor_usua_id)
    REFERENCES usuario (usua_id) ON DELETE RESTRICT;
