package com.smartgarden.ai.domain;

public record DryingRate(Double ratePerHour, Confidence confidence, int samples) {

	public static DryingRate unknown() {
		return new DryingRate(null, Confidence.LOW, 0);
	}

}
