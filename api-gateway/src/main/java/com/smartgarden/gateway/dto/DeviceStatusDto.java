package com.smartgarden.gateway.dto;

import java.time.Instant;

public record DeviceStatusDto(String deviceId, boolean online, String reason, Instant changedAt) {
}
