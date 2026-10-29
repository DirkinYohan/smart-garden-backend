CREATE TABLE analysis (
    id                          UUID PRIMARY KEY,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    source                      VARCHAR(16)              NOT NULL,
    trigger_type                VARCHAR(16)              NOT NULL,
    recommended_min             INTEGER                  NOT NULL,
    recommended_max             INTEGER                  NOT NULL,
    pause_for_rain              BOOLEAN                  NOT NULL,
    hours_until_next_irrigation DOUBLE PRECISION,
    risk_level                  VARCHAR(16)              NOT NULL,
    summary                     VARCHAR(1000)            NOT NULL,
    -- JSON array of strings
    recommendations             VARCHAR(4000)            NOT NULL,
    applied                     BOOLEAN                  NOT NULL,
    fallback_reason             VARCHAR(500),
    -- JSON summary of the input data (moisture, weather, config)
    input_snapshot              VARCHAR(4000)            NOT NULL
);

CREATE INDEX idx_analysis_created_at ON analysis (created_at);
