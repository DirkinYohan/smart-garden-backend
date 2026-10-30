package com.smartgarden.ai.domain;

public enum MoistureLevel {

	SENSOR_ERROR("error de sensor"),

	VERY_DRY("muy seca"),

	DRY("baja"),

	OPTIMAL("ideal"),

	WET("alta"),

	SATURATED("saturada");

	private final String label;

	MoistureLevel(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public boolean isDry() {
		return this == VERY_DRY || this == DRY;
	}

	public boolean isWet() {
		return this == WET || this == SATURATED;
	}

	public boolean isKnown() {
		return this != SENSOR_ERROR;
	}

	public RiskLevel risk() {
		return switch (this) {
			case VERY_DRY, SATURATED, SENSOR_ERROR -> RiskLevel.HIGH;
			case DRY, WET -> RiskLevel.MEDIUM;
			case OPTIMAL -> RiskLevel.LOW;
		};
	}

}
