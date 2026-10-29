package com.smartgarden.ai.service;

public class GeminiNotConfiguredException extends RuntimeException {

	public GeminiNotConfiguredException() {
		super("GEMINI_API_KEY no configurada");
	}

}
