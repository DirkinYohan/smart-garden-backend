package com.smartgarden.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartgarden.ai.domain.Confidence;
import com.smartgarden.ai.domain.DryingRate;
import com.smartgarden.ai.domain.Projection;
import com.smartgarden.ai.domain.WeatherSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoistureProjectorTest {

	private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

	@Test
	void projectsHourlyAndFindsTheNextIrrigation() {
		Projection p = MoistureProjector.project(60, new DryingRate(-2.0, Confidence.HIGH, 30), null, 45, NOW);

		assertThat(p.points()).hasSize(24);
		assertThat(p.points().getFirst().timestamp()).isEqualTo(NOW.plus(Duration.ofHours(1)));
		assertThat(p.points().getFirst().moisture()).isEqualTo(58.0);
		// 60 − 2 h⁻¹ · t = 45  →  t = 7.5 h
		assertThat(p.estimatedNextIrrigationAt()).isEqualTo(NOW.plus(Duration.ofMinutes(450)));
		assertThat(MoistureProjector.hoursUntil(p, NOW)).isEqualTo(7.5);
		assertThat(p.confidence()).isEqualTo(Confidence.HIGH);
		assertThat(p.points().getLast().moisture()).isEqualTo(12.0);
	}

	@Test
	void rainRaisesMoistureModeratelyAndIsCapped() {
		List<WeatherSnapshot.Hourly> hourly = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			hourly.add(new WeatherSnapshot.Hourly(NOW.plus(Duration.ofHours(i)), 15, 90, 95, 4.0));
		}
		WeatherSnapshot weather = new WeatherSnapshot(NOW, 4.7, -74.1, null, hourly, 95, 48);

		Projection p = MoistureProjector.project(50, new DryingRate(-1.0, Confidence.MEDIUM, 20), weather, 45, NOW);

		// each rainy hour adds min(2.5·4, 5) = 5 points, capped at 15 in total
		assertThat(p.points().get(0).moisture()).isEqualTo(54.0);
		assertThat(p.points().get(2).moisture()).isEqualTo(62.0);
		assertThat(p.points().get(3).moisture()).isEqualTo(61.0);
		// the rain delays the next irrigation: 62 % at +3 h, then −1/h reaches 45 % at +20 h
		assertThat(p.estimatedNextIrrigationAt()).isEqualTo(NOW.plus(Duration.ofHours(20)));
	}

	@Test
	void valuesAreClampedTo0And100() {
		Projection wet = MoistureProjector.project(99, new DryingRate(5.0, Confidence.LOW, 5), null, 45, NOW);
		Projection dry = MoistureProjector.project(5, new DryingRate(-10.0, Confidence.LOW, 5), null, 45, NOW);

		assertThat(wet.points()).allSatisfy(pt -> assertThat(pt.moisture()).isLessThanOrEqualTo(100));
		assertThat(dry.points()).allSatisfy(pt -> assertThat(pt.moisture()).isGreaterThanOrEqualTo(0));
		assertThat(dry.estimatedNextIrrigationAt()).isEqualTo(NOW);
	}

	@Test
	void unknownRateGivesFlatLowConfidenceProjection() {
		Projection p = MoistureProjector.project(55, DryingRate.unknown(), null, 45, NOW);

		assertThat(p.points()).allSatisfy(pt -> assertThat(pt.moisture()).isCloseTo(55, within(0.01)));
		assertThat(p.confidence()).isEqualTo(Confidence.LOW);
		assertThat(p.estimatedNextIrrigationAt()).isNull();
	}

	@Test
	void noValidReadingMeansNoProjection() {
		Projection p = MoistureProjector.project(null, new DryingRate(-2.0, Confidence.HIGH, 30), null, 45, NOW);

		assertThat(p.points()).isEmpty();
		assertThat(p.estimatedNextIrrigationAt()).isNull();
		assertThat(MoistureProjector.hoursUntil(p, NOW)).isNull();
	}

}
