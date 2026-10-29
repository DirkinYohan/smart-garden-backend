package com.smartgarden.gateway.dto;

public record CropProfileDto(long id, String name, int minMoisture, int maxMoisture, String description) {
}
