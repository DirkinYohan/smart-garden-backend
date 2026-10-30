package com.smartgarden.telemetry.service;

import com.smartgarden.telemetry.domain.MoistureSample;
import com.smartgarden.telemetry.entity.IrrigationEventEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DailyStatsCalculator {

	private DailyStatsCalculator() {
	}

	public static List<DailyStat> calculate(List<MoistureSample> samples, List<IrrigationEventEntity> events,
			int days, ZoneId zone, Instant now) {
		LocalDate today = LocalDate.ofInstant(now, zone);
		Map<LocalDate, Acc> byDay = new TreeMap<>();
		for (int i = days - 1; i >= 0; i--) {
			byDay.put(today.minusDays(i), new Acc());
		}
		for (MoistureSample s : samples) {
			Acc acc = byDay.get(LocalDate.ofInstant(s.receivedAt(), zone));
			if (acc != null) {
				acc.add(s.moisture());
			}
		}
		for (IrrigationEventEntity e : events) {
			Acc acc = byDay.get(LocalDate.ofInstant(e.getStartedAt(), zone));
			if (acc != null) {
				acc.irrigations++;
				Instant end = e.getEndedAt() != null ? e.getEndedAt() : now;
				acc.pumpSeconds += Math.max(0, Duration.between(e.getStartedAt(), end).toSeconds());
			}
		}
		List<DailyStat> result = new ArrayList<>(byDay.size());
		byDay.forEach((date, acc) -> result.add(acc.toStat(date)));
		return result;
	}

	public record DailyStat(LocalDate date, Double avgMoisture, Integer minMoisture, Integer maxMoisture, int samples,
			int irrigations, double pumpMinutes) {
	}

	private static final class Acc {

		private long sum;

		private int count;

		private int min = Integer.MAX_VALUE;

		private int max = Integer.MIN_VALUE;

		private int irrigations;

		private long pumpSeconds;

		void add(int moisture) {
			sum += moisture;
			count++;
			min = Math.min(min, moisture);
			max = Math.max(max, moisture);
		}

		DailyStat toStat(LocalDate date) {
			double pumpMinutes = Math.round(pumpSeconds / 60.0 * 10.0) / 10.0;
			if (count == 0) {
				return new DailyStat(date, null, null, null, 0, irrigations, pumpMinutes);
			}
			double avg = Math.round((double) sum / count * 10.0) / 10.0;
			return new DailyStat(date, avg, min, max, count, irrigations, pumpMinutes);
		}

	}

}
