package com.smartgarden.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartgarden.ai.domain.ActionType;
import com.smartgarden.ai.domain.GeminiAnswer;
import com.smartgarden.ai.domain.IrrigationRecommendation;
import com.smartgarden.ai.domain.Priority;
import com.smartgarden.ai.domain.RecommendationCategory;
import com.smartgarden.ai.domain.RiskLevel;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecommendationValidatorTest {

	@Test
	void keepsAValidAnswerAndDerivesRecommendations() {
		IrrigationRecommendation r = RecommendationValidator.sanitize(answer(40, 70, "Todo bien.",
				List.of(new GeminiAnswer.Detail("Riega temprano", "Riega en la mañana.", RecommendationCategory.IRRIGATION,
						Priority.HIGH, ActionType.APPLY_THRESHOLDS))));

		assertThat(r.recommendedMin()).isEqualTo(40);
		assertThat(r.recommendedMax()).isEqualTo(70);
		assertThat(r.headline()).isEqualTo("Tu tomate está bien");
		assertThat(r.recommendations()).containsExactly("Riega en la mañana.");
		assertThat(r.details().getFirst().action().type()).isEqualTo(ActionType.APPLY_THRESHOLDS);
		assertThat(r.hoursUntilNextIrrigation()).isNull();
	}

	@ParameterizedTest
	@CsvSource({ "5,95,10,90", "0,5,10,20", "85,95,80,90", "70,40,40,70", "50,52,50,60", "88,90,80,90" })
	void clampsThresholdsToRangeAndMinimumGap(int min, int max, int expectedMin, int expectedMax) {
		IrrigationRecommendation r = RecommendationValidator.sanitize(answer(min, max, "Ok.", tips("Tip")));

		assertThat(r.recommendedMin()).isEqualTo(expectedMin);
		assertThat(r.recommendedMax()).isEqualTo(expectedMax);
	}

	@Test
	void limitsSummaryToTwoSentencesAndDetailsToFourWithDefaults() {
		IrrigationRecommendation r = RecommendationValidator.sanitize(answer(40, 70,
				"Primera frase. Segunda frase. Tercera frase.", Arrays.asList(
						new GeminiAnswer.Detail(null, "Uno dos tres cuatro cinco seis siete", null, null, null), null,
						detail("b"), detail("c"), detail("d"), detail("e"))));

		assertThat(r.summary()).isEqualTo("Primera frase. Segunda frase.");
		assertThat(r.details()).hasSize(4);
		assertThat(r.details().getFirst().title()).isEqualTo("Uno dos tres cuatro cinco");
		assertThat(r.details().getFirst().category()).isEqualTo(RecommendationCategory.IRRIGATION);
		assertThat(r.details().getFirst().priority()).isEqualTo(Priority.MEDIUM);
	}

	@Test
	void rejectsStructurallyInvalidAnswers() {
		assertThatThrownBy(() -> RecommendationValidator.sanitize(null))
			.isInstanceOf(InvalidRecommendationException.class);
		assertThatThrownBy(() -> RecommendationValidator.sanitize(answer(null, 70, "Ok.", tips("x"))))
			.isInstanceOf(InvalidRecommendationException.class);
		assertThatThrownBy(() -> RecommendationValidator.sanitize(answer(40, 70, " ", tips("x"))))
			.isInstanceOf(InvalidRecommendationException.class);
		assertThatThrownBy(() -> RecommendationValidator.sanitize(answer(40, 70, "Ok.", List.of())))
			.isInstanceOf(InvalidRecommendationException.class);
		assertThatThrownBy(() -> RecommendationValidator
			.sanitize(new GeminiAnswer(40, 70, false, RiskLevel.LOW, null, "Ok.", tips("x"))))
			.isInstanceOf(InvalidRecommendationException.class);
	}

	private static GeminiAnswer answer(Integer min, Integer max, String summary, List<GeminiAnswer.Detail> details) {
		return new GeminiAnswer(min, max, null, RiskLevel.MEDIUM, "Tu tomate está bien", summary, details);
	}

	private static List<GeminiAnswer.Detail> tips(String text) {
		return List.of(detail(text));
	}

	private static GeminiAnswer.Detail detail(String text) {
		return new GeminiAnswer.Detail("Título", text, RecommendationCategory.CROP, Priority.LOW, null);
	}

}
