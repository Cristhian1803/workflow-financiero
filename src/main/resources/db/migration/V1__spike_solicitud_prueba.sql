CREATE TABLE solicitud_prueba (
    id UUID PRIMARY KEY,
    estado VARCHAR(50) NOT NULL,
    monto BIGINT NOT NULL,
    moneda VARCHAR(3) NOT NULL,
    identificador_instancia_proceso VARCHAR(64),
    resultado_simulado VARCHAR(200),
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE evento_solicitud_prueba (
    id UUID PRIMARY KEY,
    solicitud_id UUID NOT NULL REFERENCES solicitud_prueba (id),
    tipo VARCHAR(50) NOT NULL,
    detalle VARCHAR(500),
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evento_solicitud_prueba_solicitud ON evento_solicitud_prueba (solicitud_id);
