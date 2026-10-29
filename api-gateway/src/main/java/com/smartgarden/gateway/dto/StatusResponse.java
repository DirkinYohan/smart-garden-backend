package com.smartgarden.gateway.dto;

import java.time.Instant;

/** {@code reading} is null when the device never reported. */
public record StatusResponse(boolean deviceOnline, String deviceStatus, Instant lastSeen, TelemetryReadingDto reading) {
}
