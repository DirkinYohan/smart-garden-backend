package com.smartgarden.irrigation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("smartgarden.irrigation")
public record IrrigationProperties(Duration telemetryDeadline, boolean publishConfigOnStartup) {
}
