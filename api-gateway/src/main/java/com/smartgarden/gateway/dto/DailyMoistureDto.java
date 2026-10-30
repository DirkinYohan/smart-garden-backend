package com.smartgarden.gateway.dto;

public record DailyMoistureDto(String date, Double avg, Integer min, Integer max, int irrigations,
		double pumpMinutes) {
}
