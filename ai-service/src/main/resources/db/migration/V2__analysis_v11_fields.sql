-- v1.1: richer analyses (headline, health, next action, detailed recommendations, projection).
-- Columns are nullable: analyses created before keep them NULL.
ALTER TABLE analysis ADD COLUMN headline VARCHAR(200);
ALTER TABLE analysis ADD COLUMN health_status VARCHAR(16);
ALTER TABLE analysis ADD COLUMN next_action VARCHAR(24);
-- JSON array of recommendation details
ALTER TABLE analysis ADD COLUMN recommendation_details VARCHAR(4000);
-- JSON of the 24 h projection
ALTER TABLE analysis ADD COLUMN projection VARCHAR(4000);
ALTER TABLE analysis ADD COLUMN fallback_detail VARCHAR(500);

-- fallback_reason becomes a code; keep the old free text as detail.
UPDATE analysis SET fallback_detail = SUBSTRING(fallback_reason, 1, 500) WHERE fallback_reason IS NOT NULL;
UPDATE analysis SET fallback_reason = CASE
        WHEN fallback_reason LIKE '%GEMINI_API_KEY%' THEN 'NO_API_KEY'
        WHEN fallback_reason LIKE '%429%' OR LOWER(fallback_reason) LIKE '%quota%' OR fallback_reason LIKE '%cuota%' THEN 'QUOTA_EXCEEDED'
        WHEN fallback_reason LIKE '%API key not valid%' OR fallback_reason LIKE '% 401%' OR fallback_reason LIKE '% 403%' THEN 'AUTH_ERROR'
        WHEN fallback_reason LIKE '% 404%' OR fallback_reason LIKE '%no longer available%' THEN 'MODEL_NOT_FOUND'
        WHEN fallback_reason LIKE '% 503%' OR fallback_reason LIKE '%high demand%' THEN 'UNAVAILABLE'
        WHEN fallback_reason LIKE '%inválida%' THEN 'INVALID_RESPONSE'
        ELSE 'ERROR'
    END
WHERE fallback_reason IS NOT NULL;

CREATE INDEX idx_analysis_source ON analysis (source);
