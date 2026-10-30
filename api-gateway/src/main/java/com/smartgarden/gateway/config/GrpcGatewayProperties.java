package com.smartgarden.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("smartgarden.grpc")
public record GrpcGatewayProperties(Duration deadline, Duration streamInitialBackoff, Duration streamMaxBackoff,
		@DefaultValue("true") boolean streamsEnabled) {
}
