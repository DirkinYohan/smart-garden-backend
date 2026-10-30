package com.smartgarden.telemetry.service;

import com.smartgarden.telemetry.domain.MoistureSample;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HistoryAggregator {

	private HistoryAggregator() {
	}

	public static List<HistoryBucket> aggregate(List<MoistureSample> samples, Duration interval) {
		long intervalSec = interval.toSeconds();
		if (intervalSec <= 0) {
			throw new IllegalArgumentException("interval must be positive");
		}
		Map<Long, Accumulator> buckets = new LinkedHashMap<>();
		for (MoistureSample s : samples) {
			long epoch = s.receivedAt().getEpochSecond();
			long bucketStart = Math.floorDiv(epoch, intervalSec) * intervalSec;
			buckets.computeIfAbsent(bucketStart, k -> new Accumulator()).add(s);
		}
		List<HistoryBucket> result = new ArrayList<>(buckets.size());
		buckets.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(e -> result.add(e.getValue().toBucket(Instant.ofEpochSecond(e.getKey()))));
		return result;
	}

	public record HistoryBucket(Instant start, double avgMoisture, int minMoisture, int maxMoisture, int samples,
			boolean pumpActive) {
	}

	private static final class Accumulator {

		private long sum;

		private int count;

		private int min = Integer.MAX_VALUE;

		private int max = Integer.MIN_VALUE;

		private boolean pump;

		void add(MoistureSample s) {
			sum += s.moisture();
			count++;
			min = Math.min(min, s.moisture());
			max = Math.max(max, s.moisture());
			pump |= s.pumpOn();
		}

		HistoryBucket toBucket(Instant start) {
			double avg = Math.round((double) sum / count * 10.0) / 10.0;
			return new HistoryBucket(start, avg, min, max, count, pump);
		}

	}

}
