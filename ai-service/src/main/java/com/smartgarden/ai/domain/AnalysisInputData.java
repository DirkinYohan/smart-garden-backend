package com.smartgarden.ai.domain;

/**
 * Everything an analysis is based on. Stored (as JSON) with the analysis and sent to Gemini.
 * <p>
 * {@code hasReading} means "a VALID reading (sensorOk, plausible raw value) not older than 5 minutes";
 * {@code currentMoisture} is only meaningful when it is true. The fields after {@code airHumidity} were added in
 * v1.1, {@code cropId} in v1.2; they are null in analyses stored before.
 */
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
		// v1.1
		Boolean sensorFault,
		Double readingAgeMinutes,
		Double dryingRatePerHour,
		Integer irrigations24h,
		Double pumpMinutes24h,
		Boolean rainPause,
		// v1.2
		Long cropId) {

	public static final int DEFAULT_MIN = 30;

	public static final int DEFAULT_MAX = 70;

	/** Current moisture only if there is a valid recent reading; never an invented 0 %. */
	public Integer validMoisture() {
		return hasReading ? currentMoisture : null;
	}

	/** The latest reading is recent but invalid (sensor disconnected / failing). */
	public boolean isSensorFault() {
		return Boolean.TRUE.equals(sensorFault);
	}

	public boolean isRainPause() {
		return Boolean.TRUE.equals(rainPause);
	}

	/** True when the crop profile defines a usable range. */
	public boolean hasProfileRange() {
		return profileMin > 0 && profileMax > profileMin;
	}

	/** Minimum the ESP32 is using (config), else the crop profile, else 30 %. */
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

	/** Rain is expected when the max probability in 12 h reaches the threshold and some precipitation is forecast. */
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
