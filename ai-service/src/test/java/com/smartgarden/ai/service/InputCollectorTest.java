package com.smartgarden.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Timestamp;
import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.IrrigationRecommendation;
import com.smartgarden.contracts.irrigation.v1.CropProfile;
import com.smartgarden.contracts.irrigation.v1.IrrigationConfig;
import com.smartgarden.contracts.common.v1.IrrigationMode;
import com.smartgarden.contracts.telemetry.v1.CurrentStatus;
import com.smartgarden.contracts.telemetry.v1.GetHistoryResponse;
import com.smartgarden.contracts.telemetry.v1.HistoryPoint;
import com.smartgarden.contracts.telemetry.v1.IrrigationEvent;
import com.smartgarden.contracts.telemetry.v1.TelemetryReading;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the "La humedad del suelo (0 %)" bug: the ESP32 sent raw = 4095 (ADC rail, sensor disconnected) as
 * moisture 0 with sensorOk = true, and the analysis used the latest reading even when it was 12 hours old.
 */
class InputCollectorTest {

	private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");

	private static final IrrigationConfig CONFIG = IrrigationConfig.newBuilder()
		.setMode(IrrigationMode.IRRIGATION_MODE_AUTO)
		.setCrop(CropProfile.newBuilder().setName("Cilantro").setMinMoisture(45).setMaxMoisture(75))
		.setMinMoisture(45)
		.setMaxMoisture(75)
		.build();

	@Test
	void railReadingIsNotUsedAsZeroPercent() {
		// Old telemetry: raw 4095 stored as moisture 0 with sensorOk = true, 1 minute ago
		CurrentStatus status = status(reading(0, 4095, true, NOW.minusSeconds(60)), null);

		AnalysisInputData in = build(status);

		assertThat(in.hasReading()).isFalse();
		assertThat(in.validMoisture()).isNull();
		assertThat(in.isSensorFault()).isTrue();
		IrrigationRecommendation rules = RuleBasedAnalyzer.analyze(in, 70);
		assertThat(rules.summary()).doesNotContain("(0 %)");
		assertThat(rules.headline()).isEqualTo("Revisa el sensor de humedad");
	}

	@Test
	void usesTheLastValidReadingFromGetCurrentStatus() {
		CurrentStatus status = status(reading(0, 4095, false, NOW.minusSeconds(5)),
				reading(51, 2300, true, NOW.minusSeconds(65)));

		AnalysisInputData in = build(status);

		assertThat(in.hasReading()).isTrue();
		assertThat(in.validMoisture()).isEqualTo(51);
		assertThat(in.isSensorFault()).isFalse();
		assertThat(in.readingAgeMinutes()).isEqualTo(1.1);
		assertThat(RuleBasedAnalyzer.analyze(in, 70).summary()).contains("(51 %)");
	}

	@Test
	void staleReadingIsReportedAsMissingNotAsZero() {
		CurrentStatus status = status(reading(0, 4095, true, NOW.minus(Duration.ofHours(12))),
				reading(62, 2479, true, NOW.minus(Duration.ofHours(12))));

		AnalysisInputData in = build(status);

		assertThat(in.hasReading()).isFalse();
		assertThat(in.isSensorFault()).isFalse();
		IrrigationRecommendation rules = RuleBasedAnalyzer.analyze(in, 70);
		assertThat(rules.summary()).startsWith("No hay una lectura válida del sensor en los últimos 5 minutos");
		assertThat(rules.summary()).doesNotContain("0 %)");
	}

	@Test
	void aggregatesIgnoreEmptyIntervalsAndCountRecentIrrigations() {
		GetHistoryResponse history = GetHistoryResponse.newBuilder()
			.addPoints(HistoryPoint.newBuilder().setAvgMoisture(50).setMinMoisture(40).setMaxMoisture(60).setSamples(10))
			.addPoints(HistoryPoint.newBuilder().setAvgMoisture(0).setMinMoisture(0).setMaxMoisture(0).setSamples(0))
			.addPoints(HistoryPoint.newBuilder().setAvgMoisture(70).setMinMoisture(65).setMaxMoisture(75).setSamples(10))
			.build();
		List<IrrigationEvent> events = List.of(event(NOW.minus(Duration.ofHours(2)), 90),
				event(NOW.minus(Duration.ofHours(30)), 120));

		AnalysisInputData in = InputCollector.build(status(reading(55, 2200, true, NOW), null), history, CONFIG, null,
				events, NOW);

		assertThat(in.avgMoisture24h()).isEqualTo(60.0);
		assertThat(in.minMoisture24h()).isEqualTo(40);
		assertThat(in.maxMoisture24h()).isEqualTo(75);
		assertThat(in.irrigations24h()).isEqualTo(1);
		assertThat(in.pumpMinutes24h()).isEqualTo(1.5);
	}

	private static AnalysisInputData build(CurrentStatus status) {
		return InputCollector.build(status, GetHistoryResponse.getDefaultInstance(), CONFIG, null, List.of(), NOW);
	}

	private static CurrentStatus status(TelemetryReading latest, TelemetryReading lastValid) {
		CurrentStatus.Builder b = CurrentStatus.newBuilder()
			.setHasReading(true)
			.setReading(latest)
			.setDeviceOnline(true)
			.setLastSeen(latest.getReceivedAt());
		if (lastValid != null) {
			b.setLastValidReading(lastValid);
		}
		return b.build();
	}

	private static TelemetryReading reading(int moisture, int raw, boolean sensorOk, Instant at) {
		return TelemetryReading.newBuilder()
			.setDeviceId("esp32-huerta-01")
			.setMoisture(moisture)
			.setRaw(raw)
			.setSensorOk(sensorOk)
			.setReceivedAt(ts(at))
			.build();
	}

	private static IrrigationEvent event(Instant start, int seconds) {
		return IrrigationEvent.newBuilder()
			.setStartedAt(ts(start))
			.setEndedAt(ts(start.plusSeconds(seconds)))
			.setDurationSec(seconds)
			.build();
	}

	private static Timestamp ts(Instant i) {
		return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
	}

}
