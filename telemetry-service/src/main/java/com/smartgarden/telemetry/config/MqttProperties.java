package com.smartgarden.telemetry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param url full broker URI, e.g. {@code ssl://xxxx.s1.eu.hivemq.cloud:8883} (TLS). When blank,
 * {@code tcp://host:port} is used (local Mosquitto).
 * @param cleanSession {@code false} keeps a persistent session on the broker (with a fixed {@code clientId}), so
 * QoS 1 messages published while the service reconnects are delivered afterwards.
 */
@ConfigurationProperties("smartgarden.mqtt")
public record MqttProperties(
		String url,
		String host,
		int port,
		String username,
		String password,
		String clientId,
		@DefaultValue("true") boolean cleanSession,
		String telemetryTopic,
		String statusTopic,
		String commandsTopic,
		int qos) {

	public String serverUri() {
		if (url != null && !url.isBlank()) {
			return url.trim();
		}
		return "tcp://" + host + ":" + port;
	}

	public boolean hasCredentials() {
		return username != null && !username.isBlank();
	}

}
