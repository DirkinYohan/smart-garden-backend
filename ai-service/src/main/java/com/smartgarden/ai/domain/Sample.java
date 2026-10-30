package com.smartgarden.ai.domain;

import java.time.Instant;

public record Sample(Instant time, int moisture, boolean pumpOn) {
}
