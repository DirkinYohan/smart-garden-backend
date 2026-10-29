package com.smartgarden.ai.domain;

/**
 * Result of the drying-rate regression.
 * @param ratePerHour moisture change in percentage points per hour (negative = drying), or null if not computable
 */
public record DryingRate(Double ratePerHour, Confidence confidence, int samples) {

	public static DryingRate unknown() {
		return new DryingRate(null, Confidence.LOW, 0);
	}

}
