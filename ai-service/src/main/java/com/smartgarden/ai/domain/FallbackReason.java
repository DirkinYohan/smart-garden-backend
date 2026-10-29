package com.smartgarden.ai.domain;

/** Why an analysis used the rule-based fallback instead of Gemini. */
public enum FallbackReason {
	NO_API_KEY, QUOTA_EXCEEDED, TIMEOUT, INVALID_RESPONSE, AUTH_ERROR, MODEL_NOT_FOUND, UNAVAILABLE, ERROR
}
