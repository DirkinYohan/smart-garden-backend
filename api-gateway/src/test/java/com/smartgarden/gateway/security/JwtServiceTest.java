package com.smartgarden.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartgarden.gateway.config.SecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class JwtServiceTest {

	private static final String SECRET = "test-secret-0123456789-abcdefghijklmnop";

	private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

	@Test
	void issuedTokenAuthenticatesWithRole() {
		JwtService jwt = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

		JwtService.IssuedToken token = jwt.issue("admin", "ADMIN", 3, true);
		Authentication auth = jwt.authenticate(token.token());

		assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
		assertThat(auth.getName()).isEqualTo("admin");
		assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
	}

	@Test
	void carriesTheTokenVersionAndTheMustChangeFlag() {
		JwtService jwt = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

		JwtService.TokenClaims claims = jwt.parse(jwt.issue("admin", "ADMIN", 7, true).token());

		assertThat(claims.username()).isEqualTo("admin");
		assertThat(claims.role()).isEqualTo("ADMIN");
		assertThat(claims.tokenVersion()).isEqualTo(7);
		assertThat(claims.mustChangePassword()).isTrue();
		assertThat(jwt.parse(jwt.issue("admin", "ADMIN", 7, false).token()).mustChangePassword()).isFalse();
	}

	@Test
	void expiredOrForeignTokensAreRejected() {
		String token = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC)).issue("admin", "ADMIN", 0, false).token();

		JwtService later = service(SECRET, Clock.fixed(NOW.plus(Duration.ofHours(13)), ZoneOffset.UTC));
		JwtService otherKey = service("another-secret-0123456789-abcdefghijk", Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatThrownBy(() -> later.authenticate(token)).isInstanceOf(JwtService.InvalidTokenException.class);
		assertThatThrownBy(() -> otherKey.authenticate(token)).isInstanceOf(JwtService.InvalidTokenException.class);
	}

	@Test
	void shortSecretFailsFast() {
		assertThatThrownBy(() -> service("short", Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
	}

	private static JwtService service(String secret, Clock clock) {
		return new JwtService(new SecurityProperties(new SecurityProperties.Jwt(secret, Duration.ofHours(12)),
				new SecurityProperties.Admin("admin", "x", ""), new SecurityProperties.Cors(List.of("*"))), clock);
	}

}
