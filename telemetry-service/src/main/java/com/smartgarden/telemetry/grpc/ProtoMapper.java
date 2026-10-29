package com.smartgarden.telemetry.grpc;

import com.google.protobuf.Timestamp;
import com.smartgarden.contracts.telemetry.v1.DailyStat;
import com.smartgarden.contracts.telemetry.v1.DeviceStatusEvent;
import com.smartgarden.contracts.telemetry.v1.HistoryPoint;
import com.smartgarden.contracts.telemetry.v1.IrrigationEvent;
import com.smartgarden.contracts.telemetry.v1.MoistureSample;
import com.smartgarden.contracts.telemetry.v1.TelemetryReading;
import com.smartgarden.telemetry.domain.TelemetrySnapshot;
import com.smartgarden.telemetry.entity.IrrigationEventEntity;
import com.smartgarden.telemetry.service.DailyStatsCalculator;
import com.smartgarden.telemetry.service.DeviceStatusTracker.DeviceStatusChange;
import com.smartgarden.telemetry.service.HistoryAggregator.HistoryBucket;
import java.time.Instant;

final class ProtoMapper {

	private ProtoMapper() {
	}

	static Timestamp timestamp(Instant instant) {
		return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
	}

	static TelemetryReading reading(TelemetrySnapshot s) {
		return TelemetryReading.newBuilder()
			.setDeviceId(s.deviceId())
			.setRaw(s.raw())
			.setMoisture(s.moisture())
			.setPumpOn(s.pumpOn())
			.setSensorOk(s.sensorOk())
			.setMode(s.mode().toProto())
			.setMinMoisture(s.minMoisture())
			.setMaxMoisture(s.maxMoisture())
			.setRainPause(s.rainPause())
			.setUptimeMs(s.uptimeMs())
			.setReceivedAt(timestamp(s.receivedAt()))
			.build();
	}

	static HistoryPoint historyPoint(HistoryBucket b) {
		return HistoryPoint.newBuilder()
			.setBucketStart(timestamp(b.start()))
			.setAvgMoisture(b.avgMoisture())
			.setMinMoisture(b.minMoisture())
			.setMaxMoisture(b.maxMoisture())
			.setSamples(b.samples())
			.setPumpActive(b.pumpActive())
			.build();
	}

	static IrrigationEvent irrigationEvent(IrrigationEventEntity e) {
		IrrigationEvent.Builder b = IrrigationEvent.newBuilder()
			.setId(e.getId())
			.setDeviceId(e.getDeviceId())
			.setStartedAt(timestamp(e.getStartedAt()))
			.setStartMoisture(e.getStartMoisture())
			.setMode(e.getMode().toProto())
			.setInProgress(e.isInProgress());
		if (e.getEndedAt() != null) {
			b.setEndedAt(timestamp(e.getEndedAt()));
		}
		if (e.getDurationSec() != null) {
			b.setDurationSec(e.getDurationSec());
		}
		if (e.getEndMoisture() != null) {
			b.setEndMoisture(e.getEndMoisture());
		}
		return b.build();
	}

	static MoistureSample sample(com.smartgarden.telemetry.domain.MoistureSample s) {
		return MoistureSample.newBuilder()
			.setTime(timestamp(s.receivedAt()))
			.setMoisture(s.moisture())
			.setPumpOn(s.pumpOn())
			.build();
	}

	static DailyStat dailyStat(DailyStatsCalculator.DailyStat d) {
		DailyStat.Builder b = DailyStat.newBuilder()
			.setDate(d.date().toString())
			.setSamples(d.samples())
			.setIrrigations(d.irrigations())
			.setPumpMinutes(d.pumpMinutes());
		if (d.avgMoisture() != null) {
			b.setAvgMoisture(d.avgMoisture()).setMinMoisture(d.minMoisture()).setMaxMoisture(d.maxMoisture());
		}
		return b.build();
	}

	static DeviceStatusEvent deviceStatus(DeviceStatusChange c) {
		return DeviceStatusEvent.newBuilder()
			.setDeviceId(c.deviceId())
			.setOnline(c.online())
			.setReason(c.reason())
			.setChangedAt(timestamp(c.changedAt()))
			.build();
	}

}
