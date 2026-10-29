package com.smartgarden.gateway.security;

import com.smartgarden.gateway.entity.AppUserEntity;
import com.smartgarden.gateway.repository.AppUserRepository;
import com.smartgarden.gateway.security.JwtService.IssuedToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Inicio de sesión con protección contra fuerza bruta: 5 intentos fallidos seguidos bloquean la cuenta 15 minutos. El
 * mensaje es el mismo si el usuario no existe o si la contraseña es incorrecta.
 */
@Service
public class LoginService {

	private static final Logger log = LoggerFactory.getLogger(LoginService.class);

	public static final int MAX_ATTEMPTS = 5;

	public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	private static final String BAD_CREDENTIALS = "Usuario o contraseña incorrectos";

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	private final UserSessionState sessionState;

	private final Clock clock;

	/** Hash descartable para que un usuario inexistente tarde lo mismo que uno existente. */
	private final String dummyHash;

	public LoginService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
			UserSessionState sessionState, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.sessionState = sessionState;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("timing-only-placeholder");
	}

	/**
	 * Sin {@code @Transactional} a propósito: un intento fallido lanza una excepción y una transacción única haría
	 * rollback del contador de fallos, con lo que la cuenta nunca se bloquearía. Cada {@code save} se confirma solo.
	 */
	public LoginResult login(String username, String password) {
		Instant now = clock.instant();
		AppUserEntity user = users.findByUsername(username).orElse(null);
		if (user == null) {
			passwordEncoder.matches(password, dummyHash);
			throw new BadCredentialsException(BAD_CREDENTIALS);
		}
		if (user.isLocked(now)) {
			throw new AccountLockedException(user.lockedMinutesRemaining(now));
		}
		if (!user.isEnabled() || !passwordEncoder.matches(password, user.getPasswordHash())) {
			user.registerFailedAttempt(now, MAX_ATTEMPTS, LOCK_DURATION);
			users.save(user);
			sessionState.invalidate(username);
			if (user.isLocked(now)) {
				log.warn("Cuenta '{}' bloqueada {} min por intentos fallidos", username, LOCK_DURATION.toMinutes());
				throw new AccountLockedException(user.lockedMinutesRemaining(now));
			}
			throw new BadCredentialsException(BAD_CREDENTIALS);
		}
		user.registerSuccessfulLogin();
		users.save(user);
		sessionState.invalidate(username);
		IssuedToken token = jwtService.issue(user.getUsername(), user.getRole(), user.getTokenVersion(),
				user.isMustChangePassword());
		return new LoginResult(token, user.getUsername(), user.isMustChangePassword());
	}

	public record LoginResult(IssuedToken token, String username, boolean mustChangePassword) {
	}

}
