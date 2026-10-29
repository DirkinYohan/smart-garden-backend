package com.smartgarden.gateway.security;

/** La contraseña nueva no cumple la política: se responde 400 con el motivo concreto. */
public class PasswordPolicyException extends RuntimeException {

	public PasswordPolicyException(String message) {
		super(message);
	}

}
