package com.smartgarden.telemetry.entity;

import com.smartgarden.telemetry.domain.DeviceMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "irrigation_event")
public class IrrigationEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "device_id", nullable = false, length = 64)
	private String deviceId;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "duration_sec")
	private Long durationSec;

	@Column(name = "start_moisture", nullable = false)
	private int startMoisture;

	@Column(name = "end_moisture")
	private Integer endMoisture;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private DeviceMode mode;

	protected IrrigationEventEntity() {
	}

	public static IrrigationEventEntity open(String deviceId, Instant startedAt, int startMoisture, DeviceMode mode) {
		IrrigationEventEntity e = new IrrigationEventEntity();
		e.deviceId = deviceId;
		e.startedAt = startedAt;
		e.startMoisture = startMoisture;
		e.mode = mode;
		return e;
	}

	public void close(Instant endedAt, int endMoisture) {
		this.endedAt = endedAt;
		this.endMoisture = endMoisture;
		this.durationSec = Math.max(0, Duration.between(startedAt, endedAt).toSeconds());
	}

	public boolean isInProgress() {
		return endedAt == null;
	}

	public Long getId() {
		return id;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public Long getDurationSec() {
		return durationSec;
	}

	public int getStartMoisture() {
		return startMoisture;
	}

	public Integer getEndMoisture() {
		return endMoisture;
	}

	public DeviceMode getMode() {
		return mode;
	}

}
