package com.smartgarden.ai.service;

import static com.smartgarden.ai.service.TestInputs.input;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartgarden.ai.domain.ActionType;
import com.smartgarden.ai.domain.IrrigationRecommendation;
import com.smartgarden.ai.domain.MoistureLevel;
import com.smartgarden.ai.domain.RecommendationCategory;
import com.smartgarden.ai.domain.RecommendationDetail;
import com.smartgarden.ai.domain.RiskLevel;
import org.junit.jupiter.api.Test;

class RuleBasedAnalyzerTest {

	private static final double RAIN_THRESHOLD = 70;

	private static void assertCoherent(IrrigationRecommendation r, MoistureLevel level, boolean rainExpected) {
		assertThat(CoherenceValidator.problems(level, rainExpected, CoherenceValidator.textOf(r))).isEmpty();
	}

	@Test
	void usesCropProfileThresholdsWithoutRain() {
		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(input().moisture(55).build(), RAIN_THRESHOLD);

		assertThat(r.recommendedMin()).isEqualTo(40);
		assertThat(r.recommendedMax()).isEqualTo(70);
		assertThat(r.pauseForRain()).isFalse();
		assertThat(r.riskLevel()).isEqualTo(RiskLevel.LOW);
		assertThat(r.headline()).isEqualTo("Tu tomate está en su punto");
		assertThat(r.summary()).contains("Tomate").contains("55 %").contains("40–70 %");
		assertThat(r.details()).hasSizeBetween(1, 4);
		assertThat(r.recommendations()).isEqualTo(r.details().stream().map(RecommendationDetail::detail).toList());
		assertCoherent(r, MoistureLevel.OPTIMAL, false);
	}

	@Test
	void saturatedSoilSaysSoAndNeverAsksToWater() {
		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(
				input().crop("Cilantro", 4).profile(45, 75).thresholds(45, 75).moisture(99).build(), RAIN_THRESHOLD);

		assertThat(r.riskLevel()).isEqualTo(RiskLevel.HIGH);
		assertThat(r.headline()).isEqualTo("Suelo saturado: no riegues");
		assertThat(r.summary()).contains("99 %").contains("saturado").doesNotContain("baja");
		assertThat(r.pauseForRain()).isFalse();
		assertThat(r.recommendations()).noneMatch(t -> t.contains("Riega ahora"));
		assertThat(r.details()).anyMatch(d -> d.title().contains("drenaje"));
		assertCoherent(r, MoistureLevel.SATURATED, false);
	}

	@Test
	void saturatedSoilWithRainWarnsAboutWaterlogging() {
		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(
				input().crop("Cilantro", 4).profile(45, 75).thresholds(45, 75).moisture(99).rain(100, 9.8).build(),
				RAIN_THRESHOLD);

		assertThat(r.headline()).isEqualTo("Suelo saturado: cuidado con el encharcamiento");
		assertThat(r.summary()).contains("99 %").contains("lluvia");
		assertThat(r.riskLevel()).isEqualTo(RiskLevel.HIGH);
		assertCoherent(r, MoistureLevel.SATURATED, true);
	}

	@Test
	void wetSoilIsNeverReportedAsInRange() {

		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(input().moisture(72).build(), RAIN_THRESHOLD);

		assertThat(r.headline()).isEqualTo("Humedad alta, no riegues");
		assertThat(r.summary()).contains("por encima").doesNotContain("dentro del rango");
		assertThat(r.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
		assertCoherent(r, MoistureLevel.WET, false);
	}

	@Test
	void veryDrySoilAsksToWaterNow() {
		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(input().moisture(22).build(), RAIN_THRESHOLD);

		assertThat(r.riskLevel()).isEqualTo(RiskLevel.HIGH);
		assertThat(r.headline()).isEqualTo("¡Tu tomate necesita agua ya!");
		assertThat(r.summary()).contains("22 %").contains("muy por debajo");
		assertThat(r.details().getFirst().title()).isEqualTo("Riega ahora");
		assertCoherent(r, MoistureLevel.VERY_DRY, false);
	}

	@Test
	void drySoilWithRainWaitsForIt() {
		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(input().moisture(35).rain(85, 9.3).build(),
				RAIN_THRESHOLD);

		assertThat(r.headline()).isEqualTo("Tomate puede aprovechar la lluvia");
		assertThat(r.summary()).contains("un poco baja").contains("mm");
		assertThat(r.pauseForRain()).isTrue();
		assertThat(r.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
		assertThat(r.details()).anyMatch(d -> d.category() == RecommendationCategory.WEATHER && d.action() != null
				&& d.action().type() == ActionType.ENABLE_RAIN_PAUSE);
		assertCoherent(r, MoistureLevel.DRY, true);
	}

	@Test
	void veryDrySoilWithRainStillAsksToWater() {
		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(input().moisture(12).rain(90, 9).build(),
				RAIN_THRESHOLD);

		assertThat(r.headline()).contains("aunque llueva");

		assertThat(r.pauseForRain()).isFalse();
		assertThat(r.recommendations()).anyMatch(t -> t.contains("riega aunque la pausa"));
		assertCoherent(r, MoistureLevel.VERY_DRY, true);
	}

	@Test
	void rainProbabilityWithoutPrecipitationDoesNotPause() {
		assertThat(RuleBasedAnalyzer.analyze(input().rain(85, 0).build(), RAIN_THRESHOLD).pauseForRain()).isFalse();
	}

	@Test
	void missingReadingIsExplicitAndNeverZero() {
		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(input().moisture(null).noWeather().build(),
				RAIN_THRESHOLD);

		assertThat(r.headline()).isEqualTo("No hay lecturas recientes del sensor");
		assertThat(r.summary()).contains("últimos 5 minutos").doesNotContain("(0 %)");
		assertThat(r.details().getFirst().action().type()).isEqualTo(ActionType.CHECK_SENSOR);
		assertCoherent(r, MoistureLevel.SENSOR_ERROR, false);
	}

	@Test
	void sensorFaultAsksToCheckTheSensor() {
		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(input().sensorFault().build(), RAIN_THRESHOLD);

		assertThat(r.headline()).isEqualTo("Revisa el sensor de humedad");
		assertThat(r.details().getFirst().action().type()).isEqualTo(ActionType.CHECK_SENSOR);
		assertCoherent(r, MoistureLevel.SENSOR_ERROR, false);
	}

	@Test
	void suggestsApplyingProfileThresholdsWhenTheyDiffer() {
		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(input().thresholds(30, 60).build(), RAIN_THRESHOLD);

		assertThat(r.details()).anyMatch(d -> d.action() != null && d.action().type() == ActionType.APPLY_THRESHOLDS);
	}

	@Test
	void withoutConfigUsesGenericDefaults() {
		TestInputs in = input().noConfig();
		in.profileMin = 0;
		in.profileMax = 0;

		IrrigationRecommendation r = RuleBasedAnalyzer.analyze(in.build(), RAIN_THRESHOLD);

		assertThat(r.recommendedMin()).isEqualTo(30);
		assertThat(r.recommendedMax()).isEqualTo(70);
	}

	@Test
	void everyLevelIsCoherentWithAndWithoutRain() {
		for (Integer moisture : new Integer[] { null, 10, 22, 35, 44, 55, 70, 75, 76, 85, 91, 99 }) {
			for (double[] rain : new double[][] { { 10, 0 }, { 90, 9 } }) {
				IrrigationRecommendation r = RuleBasedAnalyzer.analyze(
						input().moisture(moisture).rain(rain[0], rain[1]).build(), RAIN_THRESHOLD);
				MoistureLevel level = MoistureClassifier.classify(input().moisture(moisture).rain(rain[0], rain[1]).build());
				boolean rainExpected = rain[0] >= RAIN_THRESHOLD && rain[1] > 0;

				assertThat(CoherenceValidator.problems(level, rainExpected, CoherenceValidator.textOf(r)))
					.as("nivel %s con humedad %s y lluvia %s", level, moisture, rainExpected)
					.isEmpty();
			}
		}
	}

}
