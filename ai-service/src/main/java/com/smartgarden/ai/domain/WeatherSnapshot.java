package com.smartgarden.ai.domain;

import java.time.Instant;
import java.util.List;

public record WeatherSnapshot(
		Instant fetchedAt,
		double latitude,
		double longitude,
		Current current,
		List<Hourly> hourly,
		double maxRainProbability12h,
		double totalPrecipitation12h) {

	public record Current(Instant time, double temperature, double relativeHumidity, double precipitation,
			int weatherCode) {
	}

	public record Hourly(Instant time, double temperature, double relativeHumidity, double precipitationProbability,
			double precipitation) {
	}

}
