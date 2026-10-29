package com.smartgarden.telemetry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("smartgarden.mqtt")
public record MqttProperties(
		String host,
		int port,
		String username,
		String password,
		String clientId,
		String telemetryTopic,
		String statusTopic,
		String commandsTopic,
		int qos) {

	public String serverUri() {
		return "tcp://" + host + ":" + port;
	}

	public boolean hasCredentials() {
		return username != null && !username.isBlank();
	}

}
