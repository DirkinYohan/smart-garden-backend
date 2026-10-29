package com.smartgarden.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartgarden.ai.domain.Confidence;
import com.smartgarden.ai.domain.DryingRate;
import com.smartgarden.ai.domain.Sample;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DryingRateEstimatorTest {

	private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

	@Test
	void steadyDryingGivesHighConfidence() {
		List<Sample> samples = new ArrayList<>();
		// every 15 min for 10 h, -2 points per hour
		for (int i = 0; i <= 40; i++) {
			Instant t = NOW.minus(Duration.ofMinutes(600 - 15L * i));
			samples.add(new Sample(t, (int) Math.round(70 - 0.5 * i), false));
		}

		DryingRate rate = DryingRateEstimator.estimate(samples, NOW);

		assertThat(rate.ratePerHour()).isCloseTo(-2.0, within(0.1));
		assertThat(rate.confidence()).isEqualTo(Confidence.HIGH);
	}

	@Test
	void irrigationJumpsAndPumpPeriodsAreExcluded() {
		List<Sample> samples = new ArrayList<>();
		// segment 1: 4 h drying 60 → 52, pump on, 15 min after, segment 2: drying 75 → 67
		for (int i = 0; i <= 16; i++) {
			samples.add(new Sample(NOW.minus(Duration.ofMinutes(600 - 15L * i)), 60 - i / 2, false));
		}
		samples.add(new Sample(NOW.minus(Duration.ofMinutes(345)), 52, true));
		samples.add(new Sample(NOW.minus(Duration.ofMinutes(340)), 70, true));
		samples.add(new Sample(NOW.minus(Duration.ofMinutes(330)), 76, false)); // within 15 min after the pump
		for (int i = 0; i <= 16; i++) {
			samples.add(new Sample(NOW.minus(Duration.ofMinutes(300 - 15L * i)), 75 - i / 2, false));
		}

		DryingRate rate = DryingRateEstimator.estimate(samples, NOW);

		// both segments dry at ~2 points/h; a single regression across the jump would give a positive slope
		assertThat(rate.ratePerHour()).isNegative().isCloseTo(-2.0, within(0.3));
	}

	@Test
	void tooFewReadingsGiveUnknownRate() {
		List<Sample> samples = List.of(new Sample(NOW.minus(Duration.ofMinutes(30)), 50, false),
				new Sample(NOW.minus(Duration.ofMinutes(15)), 49, false), new Sample(NOW, 49, false));

		DryingRate rate = DryingRateEstimator.estimate(samples, NOW);

		assertThat(rate.ratePerHour()).isNull();
		assertThat(rate.confidence()).isEqualTo(Confidence.LOW);
	}

	@Test
	void noisyDataHasLowConfidence() {
		List<Sample> samples = new ArrayList<>();
		for (int i = 0; i <= 12; i++) {
			samples.add(new Sample(NOW.minus(Duration.ofMinutes(240 - 20L * i)), i % 2 == 0 ? 40 : 60, false));
		}

		assertThat(DryingRateEstimator.estimate(samples, NOW).confidence()).isEqualTo(Confidence.LOW);
	}

}
