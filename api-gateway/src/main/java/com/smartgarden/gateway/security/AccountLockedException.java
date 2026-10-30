package com.smartgarden.gateway.security;

import org.springframework.security.core.AuthenticationException;

public class AccountLockedException extends AuthenticationException {

	private final long minutesRemaining;

	public AccountLockedException(long minutesRemaining) {
		super("Cuenta bloqueada por intentos fallidos. Intenta de nuevo en " + minutesRemaining + " minutos");
		this.minutesRemaining = minutesRemaining;
	}

	public long minutesRemaining() {
		return minutesRemaining;
	}

}
