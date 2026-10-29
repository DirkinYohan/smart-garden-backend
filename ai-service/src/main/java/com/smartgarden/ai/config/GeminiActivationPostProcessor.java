package com.smartgarden.ai.config;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * The Google GenAI auto-configuration fails at startup without an API key. When {@code GEMINI_API_KEY} is empty, the
 * chat model is disabled ({@code spring.ai.model.chat=none}) so the service still starts and every analysis uses the
 * rule-based fallback.
 */
public class GeminiActivationPostProcessor implements EnvironmentPostProcessor, Ordered {

	static final String API_KEY_PROPERTY = "spring.ai.google.genai.api-key";

	static final String CHAT_MODEL_PROPERTY = "spring.ai.model.chat";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String apiKey = environment.getProperty(API_KEY_PROPERTY, "");
		if (apiKey.isBlank() && !environment.containsProperty(CHAT_MODEL_PROPERTY)) {
			environment.getPropertySources()
				.addFirst(new MapPropertySource("geminiActivation", Map.of(CHAT_MODEL_PROPERTY, "none")));
		}
	}

	@Override
	public int getOrder() {
		// After application.yaml has been loaded.
		return Ordered.LOWEST_PRECEDENCE;
	}

}
