package com.smartgarden.gateway.dto;

import java.time.Instant;

public record ThresholdPointDto(Instant timestamp, int min, int max, String source, String riskLevel) {
}
