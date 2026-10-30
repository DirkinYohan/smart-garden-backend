package com.smartgarden.ai.service;

import static com.smartgarden.ai.service.TestInputs.input;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartgarden.ai.domain.MoistureLevel;
import org.junit.jupiter.api.Test;

class MoistureClassifierTest {

	private static final int MIN = 45;

	private static final int MAX = 75;

	@Test
	void belowMinMinusTenIsVeryDry() {
		assertThat(MoistureClassifier.classify(34, MIN, MAX)).isEqualTo(MoistureLevel.VERY_DRY);
		assertThat(MoistureClassifier.classify(0, MIN, MAX)).isEqualTo(MoistureLevel.VERY_DRY);
	}

	@Test
	void betweenMinMinusTenAndMinIsDry() {
		assertThat(MoistureClassifier.classify(35, MIN, MAX)).isEqualTo(MoistureLevel.DRY);
		assertThat(MoistureClassifier.classify(44, MIN, MAX)).isEqualTo(MoistureLevel.DRY);
	}

	@Test
	void insideTheRangeIsOptimalIncludingBothEdges() {
		assertThat(MoistureClassifier.classify(45, MIN, MAX)).isEqualTo(MoistureLevel.OPTIMAL);
		assertThat(MoistureClassifier.classify(60, MIN, MAX)).isEqualTo(MoistureLevel.OPTIMAL);
		assertThat(MoistureClassifier.classify(75, MIN, MAX)).isEqualTo(MoistureLevel.OPTIMAL);
	}

	@Test
	void aboveMaxUpToMaxPlusFifteenIsWet() {
		assertThat(MoistureClassifier.classify(76, MIN, MAX)).isEqualTo(MoistureLevel.WET);
		assertThat(MoistureClassifier.classify(80, MIN, MAX)).isEqualTo(MoistureLevel.WET);
		assertThat(MoistureClassifier.classify(86, MIN, MAX)).isEqualTo(MoistureLevel.WET);
		assertThat(MoistureClassifier.classify(90, MIN, MAX)).isEqualTo(MoistureLevel.WET);
	}

	@Test
	void aboveMaxPlusFifteenOrNinetyFiveIsSaturated() {
		assertThat(MoistureClassifier.classify(91, MIN, MAX)).isEqualTo(MoistureLevel.SATURATED);
		assertThat(MoistureClassifier.classify(95, MIN, MAX)).isEqualTo(MoistureLevel.SATURATED);
		assertThat(MoistureClassifier.classify(99, MIN, MAX)).isEqualTo(MoistureLevel.SATURATED);
	}

	@Test
	void ninetyFiveIsSaturatedEvenWithAWideRange() {
		assertThat(MoistureClassifier.classify(95, 10, 90)).isEqualTo(MoistureLevel.SATURATED);
		assertThat(MoistureClassifier.classify(94, 10, 90)).isEqualTo(MoistureLevel.WET);
	}

	@Test
	void noValidReadingIsSensorError() {
		assertThat(MoistureClassifier.classify(input().moisture(null).build())).isEqualTo(MoistureLevel.SENSOR_ERROR);
		assertThat(MoistureClassifier.classify(input().sensorFault().build())).isEqualTo(MoistureLevel.SENSOR_ERROR);
	}

	@Test
	void usesTheActiveCropRange() {

		assertThat(MoistureClassifier.classify(input().moisture(80).thresholds(45, 75).build()))
			.isEqualTo(MoistureLevel.WET);
		assertThat(MoistureClassifier.classify(input().moisture(80).thresholds(70, 85).build()))
			.isEqualTo(MoistureLevel.OPTIMAL);
	}

}
