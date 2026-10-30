package com.smartgarden.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
