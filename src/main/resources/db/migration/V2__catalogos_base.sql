CREATE TABLE moneda (
    mone_codigo CHAR(3) NOT NULL,
    mone_nombre VARCHAR(50) NOT NULL,
    mone_simbolo VARCHAR(10) NOT NULL,
    mone_decimales SMALLINT NOT NULL,
    mone_activa BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_moneda PRIMARY KEY (mone_codigo),
    CONSTRAINT ck_moneda_decimales CHECK (mone_decimales >= 0)
);

CREATE TABLE tipo_operacion (
    tipooper_codigo VARCHAR(30) NOT NULL,
    tipooper_nombre VARCHAR(100) NOT NULL,
    tipooper_sensible BOOLEAN NOT NULL DEFAULT FALSE,
    tipooper_activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_tipo_operacion PRIMARY KEY (tipooper_codigo)
);

CREATE TABLE perfil_riesgo (
    perfries_codigo VARCHAR(30) NOT NULL,
    perfries_nombre VARCHAR(100) NOT NULL,
    perfries_activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_perfil_riesgo PRIMARY KEY (perfries_codigo)
);

CREATE TABLE rol (
    rol_codigo VARCHAR(30) NOT NULL,
    rol_nombre VARCHAR(100) NOT NULL,
    rol_aprobador BOOLEAN NOT NULL DEFAULT FALSE,
    rol_activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_rol PRIMARY KEY (rol_codigo)
);

INSERT INTO moneda (mone_codigo, mone_nombre, mone_simbolo, mone_decimales, mone_activa)
VALUES ('PYG', 'Guaraní paraguayo', 'Gs.', 0, TRUE);

INSERT INTO tipo_operacion (tipooper_codigo, tipooper_nombre, tipooper_sensible, tipooper_activo)
VALUES
    ('NORMAL', 'Operación normal', FALSE, TRUE),
    ('SENSIBLE', 'Operación sensible', TRUE, TRUE);

INSERT INTO perfil_riesgo (perfries_codigo, perfries_nombre, perfries_activo)
VALUES
    ('NORMAL', 'Perfil normal', TRUE),
    ('ALTO', 'Perfil alto', TRUE);

INSERT INTO rol (rol_codigo, rol_nombre, rol_aprobador, rol_activo)
VALUES
    ('SOLICITANTE', 'Solicitante', FALSE, TRUE),
    ('SUPERVISOR', 'Supervisor', TRUE, TRUE),
    ('GERENTE', 'Gerente', TRUE, TRUE),
    ('GERENTE_GENERAL', 'Gerente general', FALSE, TRUE),
    ('RIESGOS', 'Riesgos', TRUE, TRUE),
    ('ANALISTA_RIESGOS', 'Analista de riesgos', FALSE, TRUE),
    ('JEFE_RIESGOS', 'Jefe de riesgos', FALSE, TRUE),
    ('OPERACIONES', 'Operaciones', FALSE, TRUE),
    ('AUDITOR', 'Auditor', FALSE, TRUE),
    ('ADMINISTRADOR', 'Administrador', FALSE, TRUE);
