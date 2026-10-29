package com.smartgarden.gateway.dto;

import java.time.Instant;

public record IrrigationEventDto(long id, String deviceId, Instant startedAt, Instant endedAt, Long durationSec,
		int startMoisture, Integer endMoisture, String mode, boolean inProgress) {
}
