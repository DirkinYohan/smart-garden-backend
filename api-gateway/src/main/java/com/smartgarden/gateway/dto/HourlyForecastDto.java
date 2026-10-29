package com.smartgarden.gateway.dto;

import java.time.Instant;

public record HourlyForecastDto(Instant time, double temperature, double relativeHumidity,
		double precipitationProbability, double precipitation) {
}
