package com.smartgarden.gateway.dto;

import java.time.Instant;

public record HistoryPointDto(Instant timestamp, double avgMoisture, int minMoisture, int maxMoisture, int samples,
		boolean pumpActive) {
}
