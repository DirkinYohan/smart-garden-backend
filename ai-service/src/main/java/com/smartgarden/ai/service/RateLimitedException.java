package com.smartgarden.ai.service;

public class RateLimitedException extends RuntimeException {

	public RateLimitedException(String message) {
		super(message);
	}

}
