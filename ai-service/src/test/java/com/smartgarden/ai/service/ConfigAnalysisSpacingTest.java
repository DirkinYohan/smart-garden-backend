package com.smartgarden.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConfigAnalysisSpacingTest {

	private static final Instant T0 = Instant.parse("2026-10-28T15:00:00Z");

	@Test
	void runsImmediatelyWithoutAPreviousAnalysis() {
		assertThat(AnalysisService.spacingWaitMillis(null, T0)).isZero();
	}

	@Test
	void waitsTheRemainingPartOfTheWindow() {
		assertThat(AnalysisService.spacingWaitMillis(T0, T0.plusSeconds(10))).isEqualTo(20_000);
		assertThat(AnalysisService.spacingWaitMillis(T0, T0.plusSeconds(27))).isEqualTo(3_000);
	}

	@Test
	void doesNotWaitOnceTheWindowHasElapsed() {
		assertThat(AnalysisService.spacingWaitMillis(T0, T0.plusSeconds(30))).isZero();
		assertThat(AnalysisService.spacingWaitMillis(T0, T0.plusSeconds(120))).isZero();
	}

}
