package com.smartgarden.gateway.security;

import com.smartgarden.gateway.entity.AppUserEntity;
import com.smartgarden.gateway.repository.AppUserRepository;
import com.smartgarden.gateway.security.JwtService.IssuedToken;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordService {

	private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	private final UserSessionState sessionState;

	private final Clock clock;

	public PasswordService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
			UserSessionState sessionState, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.sessionState = sessionState;
		this.clock = clock;
	}

	@Transactional
	public IssuedToken changePassword(String username, String currentPassword, String newPassword) {
		AppUserEntity user = users.findByUsername(username)
			.orElseThrow(() -> new BadCredentialsException("La sesión ya no es válida"));
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new IllegalArgumentException("La contraseña actual no es correcta");
		}
		PasswordPolicy.validate(username, currentPassword, newPassword);
		user.changePassword(passwordEncoder.encode(newPassword), clock.instant());
		users.save(user);
		sessionState.invalidate(username);
		log.info("Contraseña cambiada para '{}' (nuevo token_version {})", username, user.getTokenVersion());
		return jwtService.issue(user.getUsername(), user.getRole(), user.getTokenVersion(), false);
	}

}
