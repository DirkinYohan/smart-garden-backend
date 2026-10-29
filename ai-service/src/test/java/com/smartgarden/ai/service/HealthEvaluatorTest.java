package com.smartgarden.ai.service;

import static com.smartgarden.ai.service.TestInputs.input;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartgarden.ai.domain.HealthStatus;
import com.smartgarden.ai.domain.NextAction;
import com.smartgarden.ai.service.HealthEvaluator.Result;
import org.junit.jupiter.api.Test;

class HealthEvaluatorTest {

	@Test
	void inRangeIsGood() {
		assertThat(HealthEvaluator.evaluate(input().moisture(55).build(), false))
			.isEqualTo(new Result(HealthStatus.GOOD, NextAction.WAIT));
		// 40-70: 43 sigue siendo ideal, ya no "cerca del mínimo"
		assertThat(HealthEvaluator.evaluate(input().moisture(43).build(), false))
			.isEqualTo(new Result(HealthStatus.GOOD, NextAction.WAIT));
	}

	@Test
	void drySoilHasAttentionAndNeedsWater() {
		assertThat(HealthEvaluator.evaluate(input().moisture(35).build(), false))
			.isEqualTo(new Result(HealthStatus.ATTENTION, NextAction.IRRIGATE_NOW));
	}

	@Test
	void veryDrySoilIsCritical() {
		assertThat(HealthEvaluator.evaluate(input().moisture(29).build(), false))
			.isEqualTo(new Result(HealthStatus.CRITICAL, NextAction.IRRIGATE_NOW));
	}

	@Test
	void rainPauseIsReportedWhenItApplies() {
		assertThat(HealthEvaluator.evaluate(input().moisture(35).build(), true).nextAction())
			.isEqualTo(NextAction.PAUSED_FOR_RAIN);
		assertThat(HealthEvaluator.evaluate(input().moisture(55).build(), true).nextAction())
			.isEqualTo(NextAction.PAUSED_FOR_RAIN);
		// below 15 % the ESP32 waters anyway
		assertThat(HealthEvaluator.evaluate(input().moisture(10).build(), true).nextAction())
			.isEqualTo(NextAction.IRRIGATE_NOW);
	}

	@Test
	void wetSoilHasAttentionAndNeverWaters() {
		assertThat(HealthEvaluator.evaluate(input().moisture(70).build(), false))
			.isEqualTo(new Result(HealthStatus.GOOD, NextAction.WAIT));
		assertThat(HealthEvaluator.evaluate(input().moisture(80).build(), false))
			.isEqualTo(new Result(HealthStatus.ATTENTION, NextAction.WAIT));
		assertThat(HealthEvaluator.evaluate(input().moisture(85).build(), false))
			.isEqualTo(new Result(HealthStatus.ATTENTION, NextAction.WAIT));
	}

	@Test
	void saturatedSoilIsCriticalAndNeverWaters() {
		// 40-70: 85 es el último punto de la banda "alta" y 86 ya es saturación
		assertThat(HealthEvaluator.evaluate(input().moisture(85).build(), false))
			.isEqualTo(new Result(HealthStatus.ATTENTION, NextAction.WAIT));
		assertThat(HealthEvaluator.evaluate(input().moisture(86).build(), false))
			.isEqualTo(new Result(HealthStatus.CRITICAL, NextAction.WAIT));
		assertThat(HealthEvaluator.evaluate(input().moisture(99).build(), true))
			.isEqualTo(new Result(HealthStatus.CRITICAL, NextAction.WAIT));
	}

	@Test
	void sensorProblemsAskToCheckTheSensor() {
		assertThat(HealthEvaluator.evaluate(input().sensorFault().build(), false))
			.isEqualTo(new Result(HealthStatus.CRITICAL, NextAction.CHECK_SENSOR));
		assertThat(HealthEvaluator.evaluate(input().moisture(null).build(), false))
			.isEqualTo(new Result(HealthStatus.CRITICAL, NextAction.CHECK_SENSOR));
	}

	@Test
	void usesTheConfiguredThresholds() {
		// Con umbrales 60-80, 55 % queda en la banda "baja"
		assertThat(HealthEvaluator.evaluate(input().thresholds(60, 80).moisture(55).build(), false))
			.isEqualTo(new Result(HealthStatus.ATTENTION, NextAction.IRRIGATE_NOW));
		assertThat(HealthEvaluator.evaluate(input().thresholds(60, 80).moisture(49).build(), false).healthStatus())
			.isEqualTo(HealthStatus.CRITICAL);
	}

}
