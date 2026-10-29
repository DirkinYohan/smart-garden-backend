package com.smartgarden.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Google GenAI client used by Spring AI (replaces the auto-configured one, which is {@code @ConditionalOnMissingBean}).
 * <p>
 * The SDK retries 429 / 503 answers on its own with exponential backoff. On the free tier that means several model
 * calls per analysis and, while Gemini is overloaded, a timeout that hides the real cause. Here it makes exactly one
 * attempt, so a 503 is reported immediately as {@code UNAVAILABLE} and the rule-based fallback is used.
 * Only created when the chat model is enabled (GeminiActivationPostProcessor disables it without an API key).
 */
@Configuration(proxyBeanMethods = false)
public class GeminiClientConfig {

	@Bean
	@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai", matchIfMissing = true)
	Client geminiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey, AiProperties props) {
		HttpOptions options = HttpOptions.builder()
			.retryOptions(HttpRetryOptions.builder().attempts(1).build())
			.timeout((int) props.geminiTimeout().toMillis())
			.build();
		return Client.builder().apiKey(apiKey).httpOptions(options).build();
	}

}
