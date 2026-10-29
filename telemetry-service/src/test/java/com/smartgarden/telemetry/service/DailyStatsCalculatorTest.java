package com.smartgarden.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgarden.telemetry.domain.DeviceMode;
import com.smartgarden.telemetry.domain.MoistureSample;
import com.smartgarden.telemetry.entity.IrrigationEventEntity;
import com.smartgarden.telemetry.service.DailyStatsCalculator.DailyStat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyStatsCalculatorTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	@Test
	void groupsByLocalDayAndKeepsEmptyDays() {
		Instant now = Instant.parse("2026-10-28T15:00:00Z"); // 10:00 in Bogotá
		List<MoistureSample> samples = List.of(new MoistureSample(Instant.parse("2026-10-27T13:00:00Z"), 40, false),
				new MoistureSample(Instant.parse("2026-10-27T20:00:00Z"), 60, false),
				// 2026-10-28T03:00Z is still 2026-10-27 in Bogotá (UTC-5)
				new MoistureSample(Instant.parse("2026-10-28T03:00:00Z"), 50, false),
				new MoistureSample(Instant.parse("2026-10-28T14:00:00Z"), 70, false));
		IrrigationEventEntity done = IrrigationEventEntity.open("esp32", Instant.parse("2026-10-27T15:00:00Z"), 30,
				DeviceMode.AUTO);
		done.close(Instant.parse("2026-10-27T15:03:00Z"), 70);
		IrrigationEventEntity running = IrrigationEventEntity.open("esp32", Instant.parse("2026-10-28T14:58:00Z"), 35,
				DeviceMode.AUTO);

		List<DailyStat> days = DailyStatsCalculator.calculate(samples, List.of(done, running), 3, BOGOTA, now);

		assertThat(days).extracting(DailyStat::date)
			.containsExactly(LocalDate.parse("2026-10-26"), LocalDate.parse("2026-10-27"), LocalDate.parse("2026-10-28"));
		assertThat(days.get(0).avgMoisture()).isNull();
		assertThat(days.get(0).samples()).isZero();
		assertThat(days.get(1).avgMoisture()).isEqualTo(50.0);
		assertThat(days.get(1).minMoisture()).isEqualTo(40);
		assertThat(days.get(1).maxMoisture()).isEqualTo(60);
		assertThat(days.get(1).irrigations()).isEqualTo(1);
		assertThat(days.get(1).pumpMinutes()).isEqualTo(3.0);
		assertThat(days.get(2).avgMoisture()).isEqualTo(70.0);
		assertThat(days.get(2).irrigations()).isEqualTo(1);
		assertThat(days.get(2).pumpMinutes()).isEqualTo(2.0);
	}

}
