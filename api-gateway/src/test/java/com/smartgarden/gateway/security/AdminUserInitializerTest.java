package com.smartgarden.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Bootstrap con credenciales de un solo uso. */
@ExtendWith(MockitoExtension.class)
class AdminUserInitializerTest {

	private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

	@Mock
	private AppUserRepository users;

	@Mock
	private PasswordGenerator generator;

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private UserSessionState sessionState;

	@BeforeEach
	void setUp() {
		sessionState = new UserSessionState(users, clock);
	}

	@Test
	void createsTheAdminFromTheEnvWithMustChangePassword() {
		when(users.count()).thenReturn(0L);

		initializer(new SecurityProperties.Admin("admin", "InicialSegura1", "")).run(null);

		ArgumentCaptor<AppUserEntity> saved = ArgumentCaptor.forClass(AppUserEntity.class);
		verify(users).save(saved.capture());
		assertThat(saved.getValue().getUsername()).isEqualTo("admin");
		assertThat(saved.getValue().isMustChangePassword()).isTrue();
		assertThat(saved.getValue().getTokenVersion()).isZero();
		assertThat(encoder.matches("InicialSegura1", saved.getValue().getPasswordHash())).isTrue();
	}

	@Test
	void generatesARandomPasswordWhenTheEnvValueIsEmpty() {
		when(users.count()).thenReturn(0L);
		when(generator.generate()).thenReturn("Xy7!abcdefgh1234");

		initializer(new SecurityProperties.Admin("admin", "", "")).run(null);

		ArgumentCaptor<AppUserEntity> saved = ArgumentCaptor.forClass(AppUserEntity.class);
		verify(users).save(saved.capture());
		assertThat(encoder.matches("Xy7!abcdefgh1234", saved.getValue().getPasswordHash())).isTrue();
		assertThat(saved.getValue().isMustChangePassword()).isTrue();
	}

	@Test
	void neverOverwritesExistingUsers() {
		when(users.count()).thenReturn(1L);

		initializer(new SecurityProperties.Admin("admin", "OtraClave123", "")).run(null);

		verify(users, never()).save(any());
	}

	@Test
	void resetRestoresAccessAndInvalidatesOldTokens() {
		AppUserEntity user = new AppUserEntity("admin", encoder.encode("ViejaClave123"), "ADMIN", NOW, false);
		when(users.findByUsername("admin")).thenReturn(Optional.of(user));

		initializer(new SecurityProperties.Admin("admin", null, "NuevaClave456")).run(null);

		assertThat(user.isMustChangePassword()).isTrue();
		assertThat(user.getTokenVersion()).isEqualTo(1);
		assertThat(encoder.matches("NuevaClave456", user.getPasswordHash())).isTrue();
		verify(users).save(user);
	}

	private AdminUserInitializer initializer(SecurityProperties.Admin admin) {
		SecurityProperties props = new SecurityProperties(
				new SecurityProperties.Jwt("test-secret-0123456789-abcdefghijklmnop", Duration.ofHours(12)), admin,
				new SecurityProperties.Cors(List.of("*")));
		return new AdminUserInitializer(users, encoder, props, generator, sessionState, clock);
	}

}
