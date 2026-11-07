package com.smartgarden.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param streamsOnDemand open the gRPC streams only while at least one WebSocket client is connected, so the
 * services behind them can scale to zero (Azure Container Apps).
 * @param streamsIdleGrace how long the streams stay open after the last WebSocket client leaves (covers page
 * reloads).
 * @param notifyAiOnConfigChange after a successful {@code PUT /config}, ask ai-service for an analysis
 * ({@code RequestAnalysis(reason = CONFIG_CHANGED)}). Used when ai-service does not watch the config stream.
 * @param configChangeDebounce wait after the last config change before notifying ai-service.
 */
@ConfigurationProperties("smartgarden.grpc")
public record GrpcGatewayProperties(Duration deadline, Duration streamInitialBackoff, Duration streamMaxBackoff,
		@DefaultValue("true") boolean streamsEnabled, @DefaultValue("false") boolean streamsOnDemand,
		@DefaultValue("30s") Duration streamsIdleGrace, @DefaultValue("false") boolean notifyAiOnConfigChange,
		@DefaultValue("10s") Duration configChangeDebounce) {
}
