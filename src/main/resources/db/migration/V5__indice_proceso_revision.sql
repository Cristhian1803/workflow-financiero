CREATE UNIQUE INDEX uq_revision_proceso
    ON revision_solicitud (revisoli_identificador_proceso)
    WHERE revisoli_identificador_proceso IS NOT NULL;
