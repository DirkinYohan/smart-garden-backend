package com.smartgarden.ai.service;

import static com.smartgarden.ai.service.TestInputs.input;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartgarden.ai.domain.ActionType;
import com.smartgarden.ai.domain.MoistureLevel;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeminiPromptTest {

	private static final Set<ActionType> ACTIONS = Set.of(ActionType.CHECK_SENSOR);

	@Test
	void sendsTheRealMoistureAndTheCalculatedLevel() {
		String prompt = GeminiAnalyzer.userPrompt(
				input().crop("Cilantro", 4).profile(45, 75).thresholds(45, 75).moisture(99).build(),
				MoistureLevel.SATURATED, false, ACTIONS);

		assertThat(prompt).contains("- Humedad actual del suelo (%): 99");
		assertThat(prompt).contains("Perfil del cultivo (humedad %): 45–75");
		assertThat(prompt).contains("Nivel de humedad calculado por el sistema (obligatorio respetarlo): SATURATED (saturada)");
		assertThat(prompt).contains("el suelo está saturado: no riegues");
	}

	@Test
	void explainsEachLevelToTheModel() {
		assertThat(GeminiAnalyzer.userPrompt(input().moisture(22).build(), MoistureLevel.VERY_DRY, false, ACTIONS))
			.contains("la tierra está muy seca y por debajo del mínimo: recomienda regar ya");
		assertThat(GeminiAnalyzer.userPrompt(input().moisture(80).build(), MoistureLevel.WET, false, ACTIONS))
			.contains("la humedad está por encima del máximo: no riegues y deja que baje");
		assertThat(GeminiAnalyzer.userPrompt(input().moisture(60).build(), MoistureLevel.OPTIMAL, false, ACTIONS))
			.contains("la humedad está en el rango ideal del cultivo: no hace falta regar");
	}

	@Test
	void neverInventsAMoistureWithoutAValidReading() {
		String prompt = GeminiAnalyzer.userPrompt(input().moisture(null).build(), MoistureLevel.SENSOR_ERROR, false,
				ACTIONS);

		assertThat(prompt).contains("desconocida").contains("SENSOR_ERROR");
		assertThat(prompt).doesNotContain("Humedad actual del suelo (%):");
	}

	@Test
	void tellsTheModelWhetherRainCanReplaceIrrigation() {
		assertThat(GeminiAnalyzer.userPrompt(input().moisture(30).rain(90, 9).build(), MoistureLevel.DRY, true, ACTIONS))
			.contains("Lluvia suficiente para reemplazar el riego en 12 h: sí");
		assertThat(GeminiAnalyzer.userPrompt(input().moisture(30).build(), MoistureLevel.DRY, false, ACTIONS))
			.contains("Lluvia suficiente para reemplazar el riego en 12 h: no");
	}

}
