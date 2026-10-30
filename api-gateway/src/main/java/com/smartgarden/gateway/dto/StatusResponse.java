package com.smartgarden.gateway.dto;

import java.time.Instant;

public record StatusResponse(boolean deviceOnline, String deviceStatus, Instant lastSeen, TelemetryReadingDto reading) {
}
