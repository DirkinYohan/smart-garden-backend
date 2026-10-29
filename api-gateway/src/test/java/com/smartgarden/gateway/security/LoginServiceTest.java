package com.smartgarden.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartgarden.gateway.config.SecurityProperties;
import com.smartgarden.gateway.entity.AppUserEntity;
import com.smartgarden.gateway.repository.AppUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Bloqueo tras 5 intentos fallidos y mensaje genérico. */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

	private static final String GOOD_PASSWORD = "ClaveBuena123";

	@Mock
	private AppUserRepository users;

	private LoginService service;

	private UserSessionState sessionState;

	private AppUserEntity admin;

	@BeforeEach
	void setUp() {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		SecurityProperties props = new SecurityProperties(
				new SecurityProperties.Jwt("test-secret-0123456789-abcdefghijklmnop", Duration.ofHours(12)),
				new SecurityProperties.Admin("admin", GOOD_PASSWORD, ""), new SecurityProperties.Cors(List.of("*")));
		JwtService jwt = new JwtService(props, clock);
		sessionState = new UserSessionState(users, clock);
		service = new LoginService(users, encoder, jwt, sessionState, clock);
		admin = new AppUserEntity("admin", encoder.encode(GOOD_PASSWORD), "ADMIN", NOW, true);
	}

	private void stubAdmin() {
		when(users.findByUsername("admin")).thenReturn(Optional.of(admin));
	}

	@Test
	void locksTheAccountAfterFiveFailedAttemptsAndReportsTheMinutesLeft() {
		stubAdmin();
		for (int attempt = 1; attempt <= 4; attempt++) {
			assertThatThrownBy(() -> service.login("admin", "incorrecta"))
				.isInstanceOf(BadCredentialsException.class)
				.hasMessage("Usuario o contraseña incorrectos");
		}

		assertThatThrownBy(() -> service.login("admin", "incorrecta")).isInstanceOf(AccountLockedException.class)
			.hasMessage("Cuenta bloqueada por intentos fallidos. Intenta de nuevo en 15 minutos");
		assertThat(admin.isLocked(NOW)).isTrue();
		assertThat(admin.lockedMinutesRemaining(NOW)).isEqualTo(15);

		// Aun con la contraseña correcta sigue bloqueada
		assertThatThrownBy(() -> service.login("admin", GOOD_PASSWORD)).isInstanceOf(AccountLockedException.class);
	}

	@Test
	void aSuccessfulLoginResetsTheCounter() {
		stubAdmin();
		assertThatThrownBy(() -> service.login("admin", "incorrecta")).isInstanceOf(BadCredentialsException.class);
		assertThat(admin.getFailedLoginAttempts()).isEqualTo(1);

		LoginService.LoginResult result = service.login("admin", GOOD_PASSWORD);

		assertThat(admin.getFailedLoginAttempts()).isZero();
		assertThat(result.username()).isEqualTo("admin");
		assertThat(result.mustChangePassword()).isTrue();
		assertThat(result.token().token()).isNotBlank();
	}

	@Test
	void unknownUserGetsTheSameMessage() {
		when(users.findByUsername("nadie")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.login("nadie", "loquesea"))
			.isInstanceOf(BadCredentialsException.class)
			.hasMessage("Usuario o contraseña incorrectos");
	}

	@Test
	void issuesATokenWithTheCurrentTokenVersion() {
		stubAdmin();
		admin.resetPassword(admin.getPasswordHash(), NOW);

		LoginService.LoginResult result = service.login("admin", GOOD_PASSWORD);

		assertThat(result.token().token()).isNotBlank();
		assertThat(sessionState.snapshot("admin").orElseThrow().tokenVersion()).isEqualTo(admin.getTokenVersion());
	}

}
