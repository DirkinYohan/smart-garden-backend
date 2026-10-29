package com.smartgarden.ai.service;

import static com.smartgarden.ai.service.TestInputs.input;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartgarden.ai.domain.ActionType;
import com.smartgarden.ai.domain.Priority;
import com.smartgarden.ai.domain.RecommendationCategory;
import com.smartgarden.ai.domain.RecommendationDetail;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActionAdvisorTest {

	private static final double RAIN = 70;

	@Test
	void applyThresholdsOnlyWhenTheyDifferAndModeIsNotAi() {
		assertThat(ActionAdvisor.eligible(input().build(), RAIN, 45, 75)).contains(ActionType.APPLY_THRESHOLDS);
		assertThat(ActionAdvisor.eligible(input().build(), RAIN, 40, 70)).doesNotContain(ActionType.APPLY_THRESHOLDS);
		assertThat(ActionAdvisor.eligible(input().mode("AI").build(), RAIN, 45, 75)).isEmpty();
	}

	@Test
	void rainPauseActionsDependOnForecastAndCurrentPause() {
		assertThat(ActionAdvisor.candidates(input().rain(90, 9).build(), RAIN)).contains(ActionType.ENABLE_RAIN_PAUSE)
			.doesNotContain(ActionType.DISABLE_RAIN_PAUSE);
		assertThat(ActionAdvisor.candidates(input().rain(10, 0).rainPause(true).build(), RAIN))
			.contains(ActionType.DISABLE_RAIN_PAUSE)
			.doesNotContain(ActionType.ENABLE_RAIN_PAUSE);
		// pause on and rain expected: nothing to change
		assertThat(ActionAdvisor.candidates(input().rain(90, 9).rainPause(true).build(), RAIN))
			.doesNotContain(ActionType.ENABLE_RAIN_PAUSE, ActionType.DISABLE_RAIN_PAUSE);
	}

	@Test
	void sensorProblemsOfferCheckSensorInEveryMode() {
		assertThat(ActionAdvisor.candidates(input().mode("AI").sensorFault().build(), RAIN))
			.containsExactly(ActionType.CHECK_SENSOR);
	}

	@Test
	void attachDropsActionsThatDoNotApplyAndForcesSensorCheck() {
		List<RecommendationDetail> details = List.of(
				detail("Regar", ActionType.APPLY_THRESHOLDS), detail("Lluvia", ActionType.DISABLE_RAIN_PAUSE));

		List<RecommendationDetail> result = ActionAdvisor.attach(details,
				Set.of(ActionType.CHECK_SENSOR, ActionType.APPLY_THRESHOLDS));

		assertThat(result).hasSize(3);
		assertThat(result.getFirst().action().type()).isEqualTo(ActionType.CHECK_SENSOR);
		assertThat(result.get(1).action().type()).isEqualTo(ActionType.APPLY_THRESHOLDS);
		assertThat(result.get(1).action().label()).isEqualTo("Aplicar umbrales recomendados");
		assertThat(result.get(2).action()).isNull();
	}

	private static RecommendationDetail detail(String title, ActionType action) {
		return new RecommendationDetail(title, title + " detalle", RecommendationCategory.IRRIGATION, Priority.MEDIUM,
				action.toAction());
	}

}
