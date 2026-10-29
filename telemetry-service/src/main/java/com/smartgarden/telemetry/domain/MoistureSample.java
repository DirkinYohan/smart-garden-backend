package com.smartgarden.telemetry.domain;

import java.time.Instant;

public record MoistureSample(Instant receivedAt, int moisture, boolean pumpOn) {
}
