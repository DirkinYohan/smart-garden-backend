package com.smartgarden.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param deadline deadline of every unary gRPC call
 * @param streamInitialBackoff first delay before re-opening a broken stream
 * @param streamMaxBackoff maximum delay between reconnection attempts
 * @param streamsEnabled relay gRPC streams to WebSocket (disabled in tests)
 */
@ConfigurationProperties("smartgarden.grpc")
public record GrpcGatewayProperties(Duration deadline, Duration streamInitialBackoff, Duration streamMaxBackoff,
		@DefaultValue("true") boolean streamsEnabled) {
}
