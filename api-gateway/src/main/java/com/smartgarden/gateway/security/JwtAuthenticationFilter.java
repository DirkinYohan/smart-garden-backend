package com.smartgarden.gateway.security;

import com.smartgarden.gateway.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code Authorization: Bearer <jwt>} and enforces two things on every request:
 * <ul>
 * <li>the {@code tv} claim must match the user's current {@code token_version} (a password change closes the other
 * sessions) — 401 when it does not;</li>
 * <li>while {@code mustChangePassword} is true only {@code GET /auth/me} and {@code PUT /auth/password} are allowed
 * — everything else answers 403 {@code PASSWORD_CHANGE_REQUIRED}.</li>
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	/** Rutas permitidas mientras el usuario debe cambiar la contraseña. */
	private static final List<String> ALLOWED_WHILE_CHANGE = List.of("/api/v1/auth/me", "/api/v1/auth/password");

	private static final List<String> PUBLIC_PREFIXES = List.of("/actuator/", "/swagger-ui", "/v3/api-docs", "/ws",
			"/error");

	private final JwtService jwtService;

	private final UserSessionState sessionState;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	public JwtAuthenticationFilter(JwtService jwtService, UserSessionState sessionState, JsonMapper jsonMapper,
			Clock clock) {
		this.jwtService = jwtService;
		this.sessionState = sessionState;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER)) {
			chain.doFilter(request, response);
			return;
		}
		JwtService.TokenClaims claims;
		try {
			claims = jwtService.parse(header.substring(BEARER.length()).trim());
		}
		catch (JwtService.InvalidTokenException ex) {
			SecurityContextHolder.clearContext();
			request.setAttribute(RestAuthenticationEntryPoint.ERROR_ATTRIBUTE, ex.getMessage());
			chain.doFilter(request, response);
			return;
		}
		UserSessionState.Snapshot state = sessionState.snapshot(claims.username()).orElse(null);
		if (state == null) {
			write(response, request, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Tu sesión ya no es válida");
			return;
		}
		if (state.tokenVersion() != claims.tokenVersion()) {
			write(response, request, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
					"Tu sesión se cerró porque la contraseña cambió");
			return;
		}
		SecurityContextHolder.getContext().setAuthentication(jwtService.authentication(claims));
		if (state.mustChangePassword() && !isAllowedWhileChangingPassword(request)) {
			write(response, request, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
					"Debes cambiar la contraseña antes de continuar");
			return;
		}
		chain.doFilter(request, response);
	}

	private boolean isAllowedWhileChangingPassword(HttpServletRequest request) {
		String path = request.getRequestURI() == null ? "" : request.getRequestURI().toLowerCase(Locale.ROOT);
		if (HttpMethod.OPTIONS.matches(request.getMethod())) {
			return true;
		}
		if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
			return true;
		}
		if (ALLOWED_WHILE_CHANGE.contains(path)) {
			return HttpMethod.GET.matches(request.getMethod()) || HttpMethod.PUT.matches(request.getMethod());
		}
		return false;
	}

	private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String error,
			String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), new ErrorResponse(clock.instant(), status.value(),
				error, message, request.getRequestURI()));
	}

}
