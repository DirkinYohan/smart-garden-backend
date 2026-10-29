package com.smartgarden.irrigation.domain;

public record CropProfileView(long id, String name, int minMoisture, int maxMoisture, String description) {
}
