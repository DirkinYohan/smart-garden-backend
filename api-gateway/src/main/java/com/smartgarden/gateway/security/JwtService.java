package com.smartgarden.gateway.security;

import com.smartgarden.gateway.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	static final int MIN_SECRET_BYTES = 32;

	private static final String ROLE_CLAIM = "role";

	private static final String TOKEN_VERSION_CLAIM = "tv";

	private static final String MUST_CHANGE_CLAIM = "mcp";

	private final SecretKey key;

	private final SecurityProperties.Jwt props;

	private final Clock clock;

	public JwtService(SecurityProperties props, Clock clock) {
		this.props = props.jwt();
		this.clock = clock;
		String secret = this.props.secret();
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"JWT_SECRET must be set and be at least " + MIN_SECRET_BYTES + " bytes long");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public IssuedToken issue(String username, String role, int tokenVersion, boolean mustChangePassword) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(props.expiration());
		String token = Jwts.builder()
			.subject(username)
			.claim(ROLE_CLAIM, role)
			.claim(TOKEN_VERSION_CLAIM, tokenVersion)
			.claim(MUST_CHANGE_CLAIM, mustChangePassword)
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiresAt))
			.signWith(key)
			.compact();
		return new IssuedToken(token, expiresAt);
	}

	public TokenClaims parse(String token) {
		try {
			Claims claims = Jwts.parser()
				.verifyWith(key)
				.clock(() -> Date.from(clock.instant()))
				.build()
				.parseSignedClaims(token)
				.getPayload();
			String role = claims.get(ROLE_CLAIM, String.class);
			Integer tokenVersion = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
			Boolean mustChange = claims.get(MUST_CHANGE_CLAIM, Boolean.class);
			return new TokenClaims(claims.getSubject(), role == null ? "USER" : role,
					tokenVersion == null ? 0 : tokenVersion, Boolean.TRUE.equals(mustChange));
		}
		catch (JwtException | IllegalArgumentException ex) {
			throw new InvalidTokenException("Token inválido o expirado");
		}
	}

	public Authentication authenticate(String token) {
		return authentication(parse(token));
	}

	public Authentication authentication(TokenClaims claims) {
		return UsernamePasswordAuthenticationToken.authenticated(claims.username(), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
	}

	public record TokenClaims(String username, String role, int tokenVersion, boolean mustChangePassword) {
	}

	public record IssuedToken(String token, Instant expiresAt) {
	}

	public static class InvalidTokenException extends RuntimeException {

		public InvalidTokenException(String message) {
			super(message);
		}

	}

}
