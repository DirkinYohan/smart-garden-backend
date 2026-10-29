package com.smartgarden.gateway.security;

import com.smartgarden.gateway.config.SecurityProperties;
import com.smartgarden.gateway.entity.AppUserEntity;
import com.smartgarden.gateway.repository.AppUserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstrap del administrador con credenciales de un solo uso:
 * <ul>
 * <li><b>Base de datos vacía:</b> crea el admin con {@code must_change_password = true}; si ADMIN_PASSWORD está vacía
 * genera una contraseña aleatoria de 16 caracteres y la muestra UNA sola vez en el log.</li>
 * <li><b>Ya hay usuarios:</b> no toca nada y avisa de que ADMIN_PASSWORD ya no se usa.</li>
 * <li><b>ADMIN_RESET_PASSWORD:</b> restablece la contraseña (recuperación), vuelve a obligar el cambio e invalida los
 * tokens anteriores; luego pide quitar la variable.</li>
 * </ul>
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final SecurityProperties props;

	private final PasswordGenerator passwordGenerator;

	private final UserSessionState sessionState;

	private final Clock clock;

	public AdminUserInitializer(AppUserRepository users, PasswordEncoder passwordEncoder, SecurityProperties props,
			PasswordGenerator passwordGenerator, UserSessionState sessionState, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.props = props;
		this.passwordGenerator = passwordGenerator;
		this.sessionState = sessionState;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		String username = props.admin().username() == null || props.admin().username().isBlank() ? "admin"
				: props.admin().username().trim();
		String resetPassword = props.admin().resetPassword();
		if (resetPassword != null && !resetPassword.isBlank()) {
			reset(username, resetPassword);
			return;
		}
		if (users.count() > 0) {
			log.info("El administrador ya existe: ADMIN_PASSWORD no se usa y se puede borrar del .env");
			return;
		}
		String configured = props.admin().password();
		boolean generated = configured == null || configured.isBlank();
		String initialPassword = generated ? passwordGenerator.generate() : configured;
		users.save(new AppUserEntity(username, passwordEncoder.encode(initialPassword), "ADMIN", clock.instant(),
				true));
		if (generated) {
			log.warn("Contraseña inicial del administrador: {} (cámbiala al iniciar sesión)", initialPassword);
		}
		else {
			log.info("Administrador '{}' creado desde ADMIN_PASSWORD; deberá cambiarla al iniciar sesión", username);
		}
	}

	private void reset(String username, String newPassword) {
		boolean weak = !PasswordPolicy.violations(username, null, newPassword).isEmpty();
		AppUserEntity user = users.findByUsername(username).orElse(null);
		if (user == null) {
			users.save(new AppUserEntity(username, passwordEncoder.encode(newPassword), "ADMIN", clock.instant(),
					true));
			log.warn("ADMIN_RESET_PASSWORD: administrador '{}' creado de nuevo; deberá cambiar la contraseña al entrar",
					username);
		}
		else {
			user.resetPassword(passwordEncoder.encode(newPassword), clock.instant());
			users.save(user);
			sessionState.invalidate(username);
			log.warn("ADMIN_RESET_PASSWORD aplicado a '{}': contraseña restablecida y sesiones anteriores cerradas. "
					+ "Quita la variable del .env y vuelve a arrancar", username);
		}
		if (weak) {
			log.warn("ADMIN_RESET_PASSWORD no cumple la política de contraseñas: cámbiala al iniciar sesión");
		}
	}

}
