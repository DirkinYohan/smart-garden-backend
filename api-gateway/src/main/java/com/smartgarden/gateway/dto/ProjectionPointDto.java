package com.smartgarden.gateway.dto;

import java.time.Instant;

public record ProjectionPointDto(Instant timestamp, double moisture) {
}
