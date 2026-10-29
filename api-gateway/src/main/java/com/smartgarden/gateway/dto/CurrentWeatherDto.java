package com.smartgarden.gateway.dto;

import java.time.Instant;

public record CurrentWeatherDto(Instant time, double temperature, double relativeHumidity, double precipitation,
		int weatherCode) {
}
