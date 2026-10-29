package com.smartgarden.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartgarden.ai.domain.IrrigationRecommendation;
import com.smartgarden.ai.domain.MoistureLevel;
import com.smartgarden.ai.domain.Priority;
import com.smartgarden.ai.domain.RecommendationCategory;
import com.smartgarden.ai.domain.RecommendationDetail;
import com.smartgarden.ai.domain.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoherenceValidatorTest {

	private static IrrigationRecommendation text(String headline, String summary) {
		return new IrrigationRecommendation(45, 75, true, null, RiskLevel.LOW, summary, List.of(summary), headline,
				List.of(new RecommendationDetail("Titulo", summary, RecommendationCategory.IRRIGATION, Priority.MEDIUM,
						null)));
	}

	@Test
	void rejectsLowMoistureWordingWhenTheSoilIsSaturated() {
		IrrigationRecommendation answer = text("Cilantro listo para recibir lluvia",
				"El cilantro tiene humedad baja actualmente, pero la lluvia pronosticada evitará más riego.");

		assertThatThrownBy(() -> CoherenceValidator.check(MoistureLevel.SATURATED, true, answer))
			.isInstanceOf(InvalidRecommendationException.class);
		assertThat(CoherenceValidator.problems(MoistureLevel.SATURATED, true,
				"El cilantro tiene humedad baja actualmente")).contains("humedad baja");
	}

	@Test
	void rejectsLowMoistureWordingWhenTheSoilIsWet() {
		assertThat(CoherenceValidator.problems(MoistureLevel.WET, false, "La humedad es baja, conviene regar"))
			.isNotEmpty();
	}

	@Test
	void rejectsNoWaterAdviceOnVeryDrySoil() {
		assertThat(CoherenceValidator.problems(MoistureLevel.VERY_DRY, true,
				"No hace falta regar: la lluvia se encarga")).contains("no hace falta regar");
	}

	@Test
	void drySoilMayRelyOnRainOnlyWhenRainIsExpected() {
		String answer = "La humedad está un poco baja, pero la lluvia prevista la recuperará: no hace falta regar.";

		assertThatCode(() -> CoherenceValidator.problems(MoistureLevel.DRY, true, answer)).doesNotThrowAnyException();
		assertThat(CoherenceValidator.problems(MoistureLevel.DRY, true, answer)).isEmpty();
		assertThat(CoherenceValidator.problems(MoistureLevel.DRY, false, answer)).contains("no hace falta regar");
	}

	@Test
	void acceptsCoherentTextsForEveryLevel() {
		assertThat(CoherenceValidator.problems(MoistureLevel.VERY_DRY, false,
				"Tu tomate necesita agua ya: la humedad está en 22 %, muy por debajo del 40 % ideal. Riega ahora."))
			.isEmpty();
		assertThat(CoherenceValidator.problems(MoistureLevel.OPTIMAL, false,
				"Tu cilantro está en su punto: 60 % dentro del rango ideal 45-75 %. No hace falta regar."))
			.isEmpty();
		assertThat(CoherenceValidator.problems(MoistureLevel.WET, false,
				"Humedad alta, no riegues: la tierra tiene 84 %, por encima del 75 % ideal."))
			.isEmpty();
		assertThat(CoherenceValidator.problems(MoistureLevel.SATURATED, true,
				"Suelo saturado: la humedad está en 99 % y viene más lluvia. Revisa el drenaje."))
			.isEmpty();
		assertThat(CoherenceValidator.problems(MoistureLevel.SENSOR_ERROR, false,
				"No hay lecturas válidas del sensor; revisa la conexión."))
			.isEmpty();
	}

	@Test
	void sensorErrorCannotClaimAMoistureState() {
		assertThat(CoherenceValidator.problems(MoistureLevel.SENSOR_ERROR, false, "La humedad está en 60 %"))
			.isEmpty();
		assertThat(CoherenceValidator.problems(MoistureLevel.SENSOR_ERROR, false, "La humedad está muy baja"))
			.isNotEmpty();
	}

	@Test
	void ignoresAccentsAndCase() {
		assertThat(CoherenceValidator.problems(MoistureLevel.SATURATED, false, "HUMEDAD BAJA")).isNotEmpty();
		assertThat(CoherenceValidator.problems(MoistureLevel.SATURATED, false, "Humedad Baja")).isNotEmpty();
	}

}
