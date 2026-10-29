package com.smartgarden.irrigation.domain;

/**
 * Moisture threshold rules: {@code 0 <= min < max <= 100} and {@code max - min >= 10}. Messages are user-facing
 * (Spanish).
 */
public final class ThresholdValidator {

	public static final int MIN_GAP = 10;

	private ThresholdValidator() {
	}

	public static void validate(int min, int max) {
		if (min < 0 || min > 100 || max < 0 || max > 100) {
			throw new IllegalArgumentException("Los umbrales deben estar entre 0 y 100 %");
		}
		if (min >= max) {
			throw new IllegalArgumentException("La humedad mínima debe ser menor que la máxima");
		}
		if (max - min < MIN_GAP) {
			throw new IllegalArgumentException(
					"La diferencia entre humedad máxima y mínima debe ser de al menos " + MIN_GAP + " puntos");
		}
	}

}
