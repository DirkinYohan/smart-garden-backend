package com.smartgarden.gateway.dto;

import java.time.Instant;
import org.springframework.http.HttpStatus;

public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {

	public static ErrorResponse of(Instant timestamp, HttpStatus status, String message, String path) {
		return new ErrorResponse(timestamp, status.value(), status.getReasonPhrase(), message, path);
	}

}
