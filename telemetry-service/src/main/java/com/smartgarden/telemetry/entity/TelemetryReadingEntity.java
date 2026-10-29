package com.smartgarden.telemetry.entity;

import com.smartgarden.telemetry.domain.DeviceMode;
import com.smartgarden.telemetry.domain.TelemetrySnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "telemetry_reading")
public class TelemetryReadingEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "device_id", nullable = false, length = 64)
	private String deviceId;

	@Column(nullable = false)
	private int raw;

	@Column(nullable = false)
	private int moisture;

	@Column(name = "pump_on", nullable = false)
	private boolean pumpOn;

	@Column(name = "sensor_ok", nullable = false)
	private boolean sensorOk;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private DeviceMode mode;

	@Column(name = "min_moisture", nullable = false)
	private int minMoisture;

	@Column(name = "max_moisture", nullable = false)
	private int maxMoisture;

	@Column(name = "rain_pause", nullable = false)
	private boolean rainPause;

	@Column(name = "uptime_ms", nullable = false)
	private long uptimeMs;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	protected TelemetryReadingEntity() {
	}

	public static TelemetryReadingEntity from(TelemetrySnapshot s) {
		TelemetryReadingEntity e = new TelemetryReadingEntity();
		e.deviceId = s.deviceId();
		e.raw = s.raw();
		e.moisture = s.moisture();
		e.pumpOn = s.pumpOn();
		e.sensorOk = s.sensorOk();
		e.mode = s.mode();
		e.minMoisture = s.minMoisture();
		e.maxMoisture = s.maxMoisture();
		e.rainPause = s.rainPause();
		e.uptimeMs = s.uptimeMs();
		e.receivedAt = s.receivedAt();
		return e;
	}

	public TelemetrySnapshot toSnapshot() {
		return new TelemetrySnapshot(deviceId, raw, moisture, pumpOn, sensorOk, mode, minMoisture, maxMoisture,
				rainPause, uptimeMs, receivedAt);
	}

	public Long getId() {
		return id;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

}
