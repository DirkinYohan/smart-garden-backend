package com.smartgarden.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("smartgarden.weather")
public record WeatherProperties(String baseUrl, double latitude, double longitude, String timezone,
		Duration cacheTtl, double rainPauseThreshold) {
}
