package com.smartgarden.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PersistencePolicyTest {

	private static final Instant T0 = Instant.parse("2026-10-21T12:00:00Z");

	private static final Duration INTERVAL = Duration.ofSeconds(30);

	@Test
	void firstReadingIsAlwaysStored() {
		assertThat(PersistencePolicy.shouldPersist(null, null, T0, false, INTERVAL)).isTrue();
	}

	@Test
	void readingsWithinIntervalAreSkipped() {
		assertThat(PersistencePolicy.shouldPersist(T0, false, T0.plusSeconds(5), false, INTERVAL)).isFalse();
		assertThat(PersistencePolicy.shouldPersist(T0, false, T0.plusSeconds(29), false, INTERVAL)).isFalse();
	}

	@Test
	void readingAfterIntervalIsStored() {
		assertThat(PersistencePolicy.shouldPersist(T0, false, T0.plusSeconds(30), false, INTERVAL)).isTrue();
	}

	@Test
	void pumpChangeIsAlwaysStored() {
		assertThat(PersistencePolicy.shouldPersist(T0, false, T0.plusSeconds(5), true, INTERVAL)).isTrue();
		assertThat(PersistencePolicy.shouldPersist(T0, true, T0.plusSeconds(5), false, INTERVAL)).isTrue();
	}

}
