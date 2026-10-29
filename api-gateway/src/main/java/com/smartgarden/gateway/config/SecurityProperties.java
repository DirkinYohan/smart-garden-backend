package com.smartgarden.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("smartgarden.security")
public record SecurityProperties(Jwt jwt, Admin admin, Cors cors) {

	public record Jwt(String secret, Duration expiration) {
	}

	public record Admin(String username, String password, String resetPassword) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

}
