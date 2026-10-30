package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.AnalysisInputData;

final class TestInputs {

	String crop = "Tomate";

	long cropId = 2;

	String mode = "AUTO";

	boolean configAvailable = true;

	int currentMin = 40;

	int currentMax = 70;

	int profileMin = 40;

	int profileMax = 70;

	Integer moisture = 55;

	boolean sensorFault = false;

	boolean weather = true;

	double rainProbability = 10;

	double precipitation = 0;

	double temperature = 18;

	boolean rainPause = false;

	static TestInputs input() {
		return new TestInputs();
	}

	TestInputs moisture(Integer value) {
		this.moisture = value;
		return this;
	}

	TestInputs sensorFault() {
		this.sensorFault = true;
		this.moisture = null;
		return this;
	}

	TestInputs mode(String value) {
		this.mode = value;
		return this;
	}

	TestInputs crop(String name, long id) {
		this.crop = name;
		this.cropId = id;
		return this;
	}

	TestInputs thresholds(int min, int max) {
		this.currentMin = min;
		this.currentMax = max;
		return this;
	}

	TestInputs profile(int min, int max) {
		this.profileMin = min;
		this.profileMax = max;
		return this;
	}

	TestInputs rain(double probability, double mm) {
		this.rainProbability = probability;
		this.precipitation = mm;
		return this;
	}

	TestInputs rainPause(boolean value) {
		this.rainPause = value;
		return this;
	}

	TestInputs temperature(double value) {
		this.temperature = value;
		return this;
	}

	TestInputs noWeather() {
		this.weather = false;
		return this;
	}

	TestInputs noConfig() {
		this.configAvailable = false;
		return this;
	}

	AnalysisInputData build() {
		boolean hasReading = moisture != null;
		return new AnalysisInputData(crop, mode, true, configAvailable, configAvailable ? currentMin : 0,
				configAvailable ? currentMax : 0, profileMin, profileMax, hasReading, hasReading,
				hasReading ? moisture : 0, false, 50, 40, 60, 288, weather, weather ? rainProbability : 0,
				weather ? precipitation : 0, weather ? temperature : 0, weather ? 70 : 0, sensorFault,
				hasReading ? 0.5 : null, -1.5, 2, 3.0, rainPause, cropId);
	}

}
