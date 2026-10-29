package com.smartgarden.gateway.dto;

/** {@code avg/min/max} are null for days without valid readings. */
public record DailyMoistureDto(String date, Double avg, Integer min, Integer max, int irrigations,
		double pumpMinutes) {
}
