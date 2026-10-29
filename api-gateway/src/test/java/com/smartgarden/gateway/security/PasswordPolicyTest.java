package com.smartgarden.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

	@Test
	void acceptsAValidPassword() {
		assertThat(PasswordPolicy.violations("admin", "ClaveVieja1", "ClaveNueva2026")).isEmpty();
		assertThatCode(() -> PasswordPolicy.validate("admin", "ClaveVieja1", "ClaveNueva2026"))
			.doesNotThrowAnyException();
	}

	@Test
	void requiresTenCharacters() {
		assertThat(PasswordPolicy.violations("admin", null, "Abc12345"))
			.containsExactly("La contraseña debe tener al menos 10 caracteres");
	}

	@Test
	void requiresUpperLowerAndDigit() {
		assertThat(PasswordPolicy.violations("admin", null, "solominusculas1"))
			.contains("La contraseña debe incluir al menos una mayúscula");
		assertThat(PasswordPolicy.violations("admin", null, "SOLOMAYUSCULAS1"))
			.contains("La contraseña debe incluir al menos una minúscula");
		assertThat(PasswordPolicy.violations("admin", null, "SinNumerosAqui"))
			.contains("La contraseña debe incluir al menos un número");
	}

	@Test
	void rejectsTheCurrentPasswordAndTheUsername() {
		assertThat(PasswordPolicy.violations("admin", "ClaveActual1", "ClaveActual1"))
			.contains("La contraseña nueva debe ser distinta de la actual");
		assertThat(PasswordPolicy.violations("Admin", null, "Admin"))
			.contains("La contraseña no puede ser igual al nombre de usuario");
	}

	@Test
	void rejectsCommonPasswords() {
		assertThat(PasswordPolicy.violations("admin", null, "admin123"))
			.contains("Esa contraseña es demasiado común; elige otra");
		assertThat(PasswordPolicy.violations("admin", null, "Smartgarden"))
			.contains("Esa contraseña es demasiado común; elige otra");
	}

	@Test
	void rejectsMoreThanSeventyTwoCharactersBecauseBcryptTruncates() {
		assertThat(PasswordPolicy.violations("admin", null, "Aa1" + "x".repeat(80)))
			.contains("La contraseña no puede superar 72 caracteres");
	}

	@Test
	void validateThrowsTheFirstViolation() {
		assertThatThrownBy(() -> PasswordPolicy.validate("admin", null, "corta"))
			.isInstanceOf(PasswordPolicyException.class)
			.hasMessage("La contraseña debe tener al menos 10 caracteres");
	}

}
