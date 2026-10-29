package com.smartgarden.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgarden.ai.domain.FallbackReason;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class FallbackClassifierTest {

	@Test
	void classifiesTheGeminiFailures() {
		assertThat(FallbackClassifier.classify(new GeminiNotConfiguredException())).isEqualTo(FallbackReason.NO_API_KEY);
		assertThat(classify("429 . Quota exceeded for metric generate_content_free_tier_requests"))
			.isEqualTo(FallbackReason.QUOTA_EXCEEDED);
		assertThat(classify("400 . API key not valid. Please pass a valid API key.")).isEqualTo(FallbackReason.AUTH_ERROR);
		assertThat(classify("404 . This model models/gemini-2.5-flash is no longer available to new users."))
			.isEqualTo(FallbackReason.MODEL_NOT_FOUND);
		assertThat(classify("503 . This model is currently experiencing high demand."))
			.isEqualTo(FallbackReason.UNAVAILABLE);
		assertThat(FallbackClassifier.classify(new InvalidRecommendationException("missing headline")))
			.isEqualTo(FallbackReason.INVALID_RESPONSE);
		assertThat(FallbackClassifier.classify(new IllegalStateException("x", new TimeoutException())))
			.isEqualTo(FallbackReason.TIMEOUT);
		assertThat(classify("something else")).isEqualTo(FallbackReason.ERROR);
	}

	@Test
	void detailNeverContainsApiKeys() {
		String detail = FallbackClassifier.detail(new RuntimeException(
				"Failed: https://generativelanguage.googleapis.com/v1beta/models/x?key=secret123 AIzaSyA1234567890abcdefghijklmnop"));

		assertThat(detail).doesNotContain("secret123").doesNotContain("AIzaSyA1234567890").contains("key=***");
	}

	private static FallbackReason classify(String message) {
		return FallbackClassifier.classify(new RuntimeException("Failed to generate content", new RuntimeException(message)));
	}

}
