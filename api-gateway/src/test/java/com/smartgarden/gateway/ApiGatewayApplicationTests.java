package com.smartgarden.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiGatewayApplicationTests {

	private static final String INITIAL_PASSWORD = "test-password";

	private static final String NEW_PASSWORD = "ClaveNueva2026";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void protectedEndpointsRequireAToken() throws Exception {
		mvc.perform(get("/api/v1/status"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.error").value("Unauthorized"))
			.andExpect(jsonPath("$.path").value("/api/v1/status"))
			.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void invalidTokenIsRejected() throws Exception {
		mvc.perform(get("/api/v1/status").header("Authorization", "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Token inv\u00e1lido o expirado"));
	}

	@Test
	void wrongPasswordGives401() throws Exception {
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void unavailableServiceIsMappedTo503WithUniformBody() throws Exception {
		mvc.perform(get("/api/v1/status").header("Authorization", "Bearer " + token()))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value(503))
			.andExpect(jsonPath("$.message").value("Servicio no disponible temporalmente"));
	}

	@Test
	void beanValidationErrorsGive400() throws Exception {
		String token = token();
		mvc.perform(post("/api/v1/pump/start").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"durationSec\":500}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("durationSec debe estar entre 1 y 120"));
		mvc.perform(get("/api/v1/telemetry/history").param("range", "1y").header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest());
		mvc.perform(put("/api/v1/config").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mode\":\"TURBO\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/ai/stats").param("days", "5").header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("days debe ser 7 o 30"));
	}

	@Test
	void swaggerAndHealthArePublic() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	private String token() throws Exception {
		String already = login(NEW_PASSWORD);
		if (already != null) {
			return already;
		}
		String initial = login(INITIAL_PASSWORD);
		if (initial == null) {
			throw new IllegalStateException("No se pudo iniciar sesi\u00f3n con la contrase\u00f1a inicial");
		}
		String body = mvc
			.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + initial)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"currentPassword\":\"" + INITIAL_PASSWORD + "\",\"newPassword\":\""
							+ NEW_PASSWORD + "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return jsonMapper.readTree(body).get("token").asString();
	}

	private String login(String password) throws Exception {
		String body = mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"admin\",\"password\":\"" + password + "\"}"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		if (body == null || !body.contains("\"token\"")) {
			return null;
		}
		return jsonMapper.readTree(body).get("token").asString();
	}

}
