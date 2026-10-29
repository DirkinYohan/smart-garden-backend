package com.smartgarden.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Política de contraseñas del sistema: mínimo 10 caracteres, una mayúscula, una minúscula y un número, distinta
 * de la actual y del nombre de usuario, y fuera de una lista corta de contraseñas comunes. Los mensajes son
 * específicos y en español.
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 10;

	/** BCrypt solo usa los primeros 72 bytes: más allá se truncaría en silencio. */
	public static final int MAX_LENGTH = 72;

	private static final Set<String> COMMON = Set.of("admin123", "admin1234", "admin123456", "password",
			"password1", "12345678", "123456789", "1234567890", "qwerty123", "smartgarden", "contrasena",
			"contraseña", "riego123", "huerta123");

	private static final Pattern UPPER = Pattern.compile("[A-Z\u00c0-\u00dd]");

	private static final Pattern LOWER = Pattern.compile("[a-z\u00e0-\u00ff]");

	private static final Pattern DIGIT = Pattern.compile("\\d");

	private PasswordPolicy() {
	}

	/** Todos los incumplimientos, en orden de comprobación (vacío = contraseña válida). */
	public static List<String> violations(String username, String currentPassword, String newPassword) {
		List<String> problems = new ArrayList<>();
		String candidate = newPassword == null ? "" : newPassword;
		if (candidate.length() < MIN_LENGTH) {
			problems.add("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
		}
		if (candidate.length() > MAX_LENGTH) {
			problems.add("La contraseña no puede superar " + MAX_LENGTH + " caracteres");
		}
		if (!UPPER.matcher(candidate).find()) {
			problems.add("La contraseña debe incluir al menos una mayúscula");
		}
		if (!LOWER.matcher(candidate).find()) {
			problems.add("La contraseña debe incluir al menos una minúscula");
		}
		if (!DIGIT.matcher(candidate).find()) {
			problems.add("La contraseña debe incluir al menos un número");
		}
		if (!candidate.isEmpty() && candidate.equals(currentPassword)) {
			problems.add("La contraseña nueva debe ser distinta de la actual");
		}
		if (!candidate.isEmpty() && username != null
				&& candidate.toLowerCase(Locale.ROOT).equals(username.toLowerCase(Locale.ROOT))) {
			problems.add("La contraseña no puede ser igual al nombre de usuario");
		}
		if (!candidate.isEmpty() && COMMON.contains(candidate.toLowerCase(Locale.ROOT))) {
			problems.add("Esa contraseña es demasiado común; elige otra");
		}
		return problems;
	}

	/** @throws PasswordPolicyException con el primer incumplimiento encontrado */
	public static void validate(String username, String currentPassword, String newPassword) {
		List<String> problems = violations(username, currentPassword, newPassword);
		if (!problems.isEmpty()) {
			throw new PasswordPolicyException(problems.get(0));
		}
	}

}
