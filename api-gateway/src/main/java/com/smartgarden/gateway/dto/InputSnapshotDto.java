package com.smartgarden.gateway.dto;

public record InputSnapshotDto(Integer currentMoisture, Double avgMoisture24h, Integer minMoisture24h,
		Integer maxMoisture24h, Double dryingRatePerHour, int irrigations24h, double pumpMinutes24h,
		Double rainProbability12h, Double precipitation12h, Double temperature, Double airHumidity, String cropName,
		int cropMin, int cropMax, int currentMin, int currentMax, String mode, boolean sensorOk, boolean rainPause,
		Double readingAgeMinutes,

		Long cropId, String moistureLevel) {
}
