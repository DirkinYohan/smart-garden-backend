package com.smartgarden.telemetry.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("smartgarden.telemetry")
public record TelemetryProperties(Duration persistInterval, Duration offlineTimeout, Duration offlineCheckInterval,
		@DefaultValue("America/Bogota") ZoneId zone) {
}
