CREATE TABLE operacion_simulada (
    opersimu_id UUID NOT NULL,
    opersimu_clave VARCHAR(100) NOT NULL,
    opersimu_huella VARCHAR(128) NOT NULL,
    opersimu_accion VARCHAR(20) NOT NULL,
    opersimu_estado VARCHAR(20) NOT NULL,
    opersimu_referencia VARCHAR(100) NOT NULL,
    opersimu_solifina_id UUID NOT NULL,
    opersimu_revisoli_id UUID NOT NULL,
    opersimu_monto NUMERIC(19, 0) NOT NULL,
    opersimu_mone_codigo CHAR(3) NOT NULL,
    opersimu_tipooper_codigo VARCHAR(30) NOT NULL,
    opersimu_version BIGINT NOT NULL DEFAULT 0,
    opersimu_creado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    opersimu_actualizado_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_operacion_simulada PRIMARY KEY (opersimu_id),
    CONSTRAINT uq_operacion_simulada_clave UNIQUE (opersimu_clave),
    CONSTRAINT uq_operacion_simulada_intencion_accion
        UNIQUE (opersimu_solifina_id, opersimu_revisoli_id, opersimu_accion),
    CONSTRAINT ck_operacion_simulada_accion CHECK (
        opersimu_accion IN ('RESERVA', 'CONFIRMACION', 'LIBERACION')
    ),
    CONSTRAINT ck_operacion_simulada_estado CHECK (
        opersimu_estado IN ('RESERVADA', 'CONFIRMADA', 'LIBERADA')
    ),
    CONSTRAINT ck_operacion_simulada_monto CHECK (opersimu_monto > 0)
);

CREATE INDEX idx_operacion_simulada_intencion
    ON operacion_simulada (opersimu_solifina_id, opersimu_revisoli_id);
