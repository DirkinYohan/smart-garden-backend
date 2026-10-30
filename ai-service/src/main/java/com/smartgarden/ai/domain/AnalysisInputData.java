package com.smartgarden.ai.domain;

public record AnalysisInputData(
		String cropName,
		String mode,
		boolean aiEnabled,
		boolean configAvailable,
		int currentMin,
		int currentMax,
		int profileMin,
		int profileMax,
		boolean hasReading,
		boolean deviceOnline,
		int currentMoisture,
		boolean pumpOn,
		double avgMoisture24h,
		int minMoisture24h,
		int maxMoisture24h,
		int samples24h,
		boolean weatherAvailable,
		double rainProbabilityMax12h,
		double precipitationSum12h,
		double temperature,
		double airHumidity,

		Boolean sensorFault,
		Double readingAgeMinutes,
		Double dryingRatePerHour,
		Integer irrigations24h,
		Double pumpMinutes24h,
		Boolean rainPause,

		Long cropId) {

	public static final int DEFAULT_MIN = 30;

	public static final int DEFAULT_MAX = 70;

	public Integer validMoisture() {
		return hasReading ? currentMoisture : null;
	}

	public boolean isSensorFault() {
		return Boolean.TRUE.equals(sensorFault);
	}

	public boolean isRainPause() {
		return Boolean.TRUE.equals(rainPause);
	}

	public boolean hasProfileRange() {
		return profileMin > 0 && profileMax > profileMin;
	}

	public int minThreshold() {
		if (configAvailable && currentMax > 0 && currentMin < currentMax) {
			return currentMin;
		}
		return hasProfileRange() ? profileMin : DEFAULT_MIN;
	}

	public int maxThreshold() {
		if (configAvailable && currentMax > 0 && currentMin < currentMax) {
			return currentMax;
		}
		return hasProfileRange() ? profileMax : DEFAULT_MAX;
	}

	public boolean rainExpected(double probabilityThreshold) {
		return weatherAvailable && rainProbabilityMax12h >= probabilityThreshold && precipitationSum12h > 0;
	}

	public AnalysisInputData withDryingRate(Double rate) {
		return new AnalysisInputData(cropName, mode, aiEnabled, configAvailable, currentMin, currentMax, profileMin,
				profileMax, hasReading, deviceOnline, currentMoisture, pumpOn, avgMoisture24h, minMoisture24h,
				maxMoisture24h, samples24h, weatherAvailable, rainProbabilityMax12h, precipitationSum12h, temperature,
				airHumidity, sensorFault, readingAgeMinutes, rate, irrigations24h, pumpMinutes24h, rainPause, cropId);
	}

}
