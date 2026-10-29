package com.smartgarden.gateway.security;

import com.smartgarden.gateway.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401 / 403 with the same JSON body as every other API error.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

	static final String ERROR_ATTRIBUTE = RestAuthenticationEntryPoint.class.getName() + ".error";

	private final JsonMapper jsonMapper;

	private final Clock clock;

	public RestAuthenticationEntryPoint(JsonMapper jsonMapper, Clock clock) {
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		Object detail = request.getAttribute(ERROR_ATTRIBUTE);
		write(request, response, HttpStatus.UNAUTHORIZED,
				detail != null ? detail.toString() : "Se requiere autenticación");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(request, response, HttpStatus.FORBIDDEN, "Acceso denegado");
	}

	private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(),
				ErrorResponse.of(clock.instant(), status, message, request.getRequestURI()));
	}

}
