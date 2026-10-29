package com.smartgarden.gateway.dto;

import java.time.Instant;
import java.util.List;

public record WeatherDto(Instant fetchedAt, double latitude, double longitude, CurrentWeatherDto current,
		List<HourlyForecastDto> hourly, double maxRainProbability12h, double totalPrecipitation12h) {
}
