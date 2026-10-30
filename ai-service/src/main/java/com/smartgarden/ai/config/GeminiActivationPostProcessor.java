package com.smartgarden.ai.config;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

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

		return Ordered.LOWEST_PRECEDENCE;
	}

}
