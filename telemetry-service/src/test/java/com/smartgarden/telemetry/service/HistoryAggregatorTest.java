package com.smartgarden.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgarden.telemetry.domain.MoistureSample;
import com.smartgarden.telemetry.service.HistoryAggregator.HistoryBucket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoryAggregatorTest {

	@Test
	void groupsSamplesIntoAlignedBuckets() {
		Instant base = Instant.parse("2026-10-21T12:00:00Z");
		List<MoistureSample> samples = List.of(new MoistureSample(base.plusSeconds(10), 40, false),
				new MoistureSample(base.plusSeconds(100), 50, true),
				new MoistureSample(base.plusSeconds(299), 45, false),
				new MoistureSample(base.plusSeconds(300), 60, false));

		List<HistoryBucket> buckets = HistoryAggregator.aggregate(samples, Duration.ofMinutes(5));

		assertThat(buckets).hasSize(2);
		HistoryBucket first = buckets.get(0);
		assertThat(first.start()).isEqualTo(base);
		assertThat(first.avgMoisture()).isEqualTo(45.0);
		assertThat(first.minMoisture()).isEqualTo(40);
		assertThat(first.maxMoisture()).isEqualTo(50);
		assertThat(first.samples()).isEqualTo(3);
		assertThat(first.pumpActive()).isTrue();
		assertThat(buckets.get(1).start()).isEqualTo(base.plusSeconds(300));
		assertThat(buckets.get(1).pumpActive()).isFalse();
	}

	@Test
	void emptyInputGivesEmptySeries() {
		assertThat(HistoryAggregator.aggregate(List.of(), Duration.ofHours(1))).isEmpty();
	}

}
