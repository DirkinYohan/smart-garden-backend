package com.smartgarden.telemetry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("smartgarden.mqtt")
public record MqttProperties(
		String host,
		int port,
		@DefaultValue("tcp") String scheme,
		String username,
		String password,
		String clientId,
		String telemetryTopic,
		String statusTopic,
		String commandsTopic,
		int qos) {

	public String serverUri() {
		return scheme + "://" + host + ":" + port;
	}

	public boolean hasCredentials() {
		return username != null && !username.isBlank();
	}

}
