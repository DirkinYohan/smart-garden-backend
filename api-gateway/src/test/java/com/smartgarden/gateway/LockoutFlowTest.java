package com.smartgarden.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Tras 5 intentos fallidos la cuenta se bloquea 15 minutos y el login responde 423. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:lockout_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class LockoutFlowTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void fiveFailedAttemptsLockTheAccountAndAnswer423() throws Exception {
		for (int attempt = 1; attempt <= 4; attempt++) {
			mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content(loginBody("incorrecta")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Usuario o contrase\u00f1a incorrectos"));
		}

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody("incorrecta")))
			.andExpect(status().isLocked())
			.andExpect(jsonPath("$.status").value(423))
			.andExpect(jsonPath("$.message").value(
					"Cuenta bloqueada por intentos fallidos. Intenta de nuevo en 15 minutos"));

		// Incluso con la contrase\u00f1a correcta sigue bloqueada
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("test-password"))).andExpect(status().isLocked());
	}

	private static String loginBody(String password) {
		return "{\"username\":\"admin\",\"password\":\"" + password + "\"}";
	}

}
