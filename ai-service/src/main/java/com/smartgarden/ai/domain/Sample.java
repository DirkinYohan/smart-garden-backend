package com.smartgarden.ai.domain;

import java.time.Instant;

/** A valid moisture reading (from telemetry-service GetRecentSamples). */
public record Sample(Instant time, int moisture, boolean pumpOn) {
}
