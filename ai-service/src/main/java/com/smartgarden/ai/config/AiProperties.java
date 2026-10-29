package com.smartgarden.ai.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("smartgarden.ai")
public record AiProperties(Duration analysisInterval, Duration analysisInitialDelay, Duration manualRequestCooldown,
		Duration grpcDeadline, @DefaultValue("25s") Duration geminiTimeout,
		@DefaultValue("gemini-flash-lite-latest") List<String> geminiFallbackModels) {
}
