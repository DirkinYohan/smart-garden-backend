package com.smartgarden.gateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false, length = 32)
	private String role;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	// --- credenciales seguras (V2) ---

	/** true cuando la contraseña es inicial y el usuario debe cambiarla antes de usar la aplicación. */
	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	@Column(name = "password_changed_at")
	private Instant passwordChangedAt;

	/** Se incrementa al cambiar o restablecer la contraseña: invalida los JWT anteriores. */
	@Column(name = "token_version", nullable = false)
	private int tokenVersion;

	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	protected AppUserEntity() {
	}

	public AppUserEntity(String username, String passwordHash, String role, Instant createdAt) {
		this(username, passwordHash, role, createdAt, false);
	}

	public AppUserEntity(String username, String passwordHash, String role, Instant createdAt,
			boolean mustChangePassword) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = role;
		this.enabled = true;
		this.createdAt = createdAt;
		this.mustChangePassword = mustChangePassword;
		this.tokenVersion = 0;
		this.failedLoginAttempts = 0;
	}

	/** Cambio hecho por el propio usuario: deja de ser obligatorio y cierra las demás sesiones. */
	public void changePassword(String newPasswordHash, Instant now) {
		this.passwordHash = newPasswordHash;
		this.mustChangePassword = false;
		this.passwordChangedAt = now;
		this.tokenVersion++;
		this.failedLoginAttempts = 0;
		this.lockedUntil = null;
	}

	/** Restablecimiento por recuperación (ADMIN_RESET_PASSWORD): vuelve a ser obligatorio cambiarla. */
	public void resetPassword(String newPasswordHash, Instant now) {
		changePassword(newPasswordHash, now);
		this.mustChangePassword = true;
	}

	/** Cuenta un intento fallido; al llegar a {@code maxAttempts} bloquea la cuenta. */
	public void registerFailedAttempt(Instant now, int maxAttempts, Duration lockDuration) {
		this.failedLoginAttempts++;
		if (this.failedLoginAttempts >= maxAttempts) {
			this.lockedUntil = now.plus(lockDuration);
			this.failedLoginAttempts = 0;
		}
	}

	public void registerSuccessfulLogin() {
		this.failedLoginAttempts = 0;
		this.lockedUntil = null;
	}

	public boolean isLocked(Instant now) {
		return lockedUntil != null && now.isBefore(lockedUntil);
	}

	/** Minutos que faltan para poder volver a intentar (mínimo 1 mientras siga bloqueada). */
	public long lockedMinutesRemaining(Instant now) {
		if (!isLocked(now)) {
			return 0;
		}
		long seconds = Duration.between(now, lockedUntil).toSeconds();
		return Math.max(1, (seconds + 59) / 60);
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getRole() {
		return role;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	public Instant getPasswordChangedAt() {
		return passwordChangedAt;
	}

	public int getTokenVersion() {
		return tokenVersion;
	}

	public int getFailedLoginAttempts() {
		return failedLoginAttempts;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

}
