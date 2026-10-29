package com.smartgarden.gateway.dto;

public record AnalysisInputDto(String cropName, String mode, int currentMin, int currentMax, int profileMin,
		int profileMax, boolean hasReading, int currentMoisture, double avgMoisture24h, int minMoisture24h,
		int maxMoisture24h, boolean weatherAvailable, double rainProbabilityMax12h, double precipitationSum12h,
		double temperature, double airHumidity,
		// v1.2: active crop profile (0 when unknown)
		long cropId) {
}
