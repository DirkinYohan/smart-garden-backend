package com.smartgarden.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Flujo completo de credencial de un solo uso: login inicial → 403 en el resto de la API → cambio de contraseña →
 * token nuevo válido → token viejo rechazado. Base H2 propia para partir siempre de un sistema vacío.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:pwd_flow_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class PasswordChangeFlowTest {

	private static final String INITIAL_PASSWORD = "test-password";

	private static final String NEW_PASSWORD = "ClaveNueva2026";

	private static String initialToken;

	private static String newToken;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	@Order(1)
	void firstLoginSaysThePasswordMustBeChanged() throws Exception {
		String body = mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content(loginBody(INITIAL_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(true))
			.andExpect(jsonPath("$.username").value("admin"))
			.andExpect(jsonPath("$.token").exists())
			.andExpect(jsonPath("$.expiresAt").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();
		initialToken = jsonMapper.readTree(body).get("token").asString();
	}

	@Test
	@Order(2)
	void otherEndpointsAnswer403PasswordChangeRequired() throws Exception {
		mvc.perform(get("/api/v1/status").header("Authorization", "Bearer " + initialToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"))
			.andExpect(jsonPath("$.message").value("Debes cambiar la contrase\u00f1a antes de continuar"));
		mvc.perform(get("/api/v1/config").header("Authorization", "Bearer " + initialToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
	}

	@Test
	@Order(3)
	void authMeAndPasswordChangeStayAllowedWhileBlocked() throws Exception {
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + initialToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	@Test
	@Order(4)
	void wrongCurrentPasswordGives400() throws Exception {
		mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + initialToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"no-es-esta\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("La contrase\u00f1a actual no es correcta"));
	}

	@Test
	@Order(5)
	void weakPasswordGives400WithASpecificMessage() throws Exception {
		mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + initialToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"" + INITIAL_PASSWORD + "\",\"newPassword\":\"corta\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("La contrase\u00f1a debe tener al menos 10 caracteres"));
		mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + initialToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"" + INITIAL_PASSWORD
							+ "\",\"newPassword\":\"sinnumerosmayusculas\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("La contrase\u00f1a debe incluir al menos una may\u00fascula"));
	}

	@Test
	@Order(6)
	void changingThePasswordReturnsANewToken() throws Exception {
		String body = mvc
			.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + initialToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"currentPassword\":\"" + INITIAL_PASSWORD + "\",\"newPassword\":\""
							+ NEW_PASSWORD + "\"}"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.token").exists())
			.andExpect(jsonPath("$.expiresAt").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();
		newToken = jsonMapper.readTree(body).get("token").asString();
		org.assertj.core.api.Assertions.assertThat(newToken).isNotEqualTo(initialToken);
	}

	@Test
	@Order(7)
	void theOldTokenIsRejectedAfterTheChange() throws Exception {
		mvc.perform(get("/api/v1/status").header("Authorization", "Bearer " + initialToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Tu sesi\u00f3n se cerr\u00f3 porque la contrase\u00f1a cambi\u00f3"));
	}

	@Test
	@Order(8)
	void theNewTokenWorksAndMeShowsThePasswordAsChanged() throws Exception {
		// 503 = la petición pasó la seguridad y el servicio de destino está apagado en tests
		mvc.perform(get("/api/v1/status").header("Authorization", "Bearer " + newToken))
			.andExpect(status().isServiceUnavailable());
		mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(false))
			.andExpect(jsonPath("$.passwordChangedAt").exists());
	}

	@Test
	@Order(9)
	void loggingInAgainUsesTheNewPassword() throws Exception {
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(INITIAL_PASSWORD)))
			.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(NEW_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	private static String loginBody(String password) {
		return "{\"username\":\"admin\",\"password\":\"" + password + "\"}";
	}

}
