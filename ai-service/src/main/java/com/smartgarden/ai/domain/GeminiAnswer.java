package com.smartgarden.ai.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Structured output requested from Gemini (Spring AI derives the JSON schema from this record). Deterministic
 * values (projection, next irrigation time, health, next action) are computed in Java, not here.
 */
public record GeminiAnswer(
		@JsonPropertyDescription("Humedad de suelo mínima recomendada en %, entero entre 10 y 90") Integer recommendedMin,
		@JsonPropertyDescription("Humedad de suelo máxima recomendada en %, entero entre 10 y 90, al menos 10 puntos sobre el mínimo") Integer recommendedMax,
		@JsonPropertyDescription("true si se debe pausar el riego automático por lluvia prevista") Boolean pauseForRain,
		@JsonPropertyDescription("Riesgo hídrico del cultivo: LOW, MEDIUM o HIGH") RiskLevel riskLevel,
		@JsonPropertyDescription("Frase corta en español (máximo 60 caracteres) sobre el estado del cultivo, por ejemplo: Tu cilantro está bien hidratado") String headline,
		@JsonPropertyDescription("Resumen en español, máximo 2 frases") String summary,
		@JsonPropertyDescription("Entre 1 y 4 recomendaciones en español, concretas y fáciles de entender") List<Detail> recommendationDetails) {

	public record Detail(
			@JsonPropertyDescription("Título corto en español (máximo 40 caracteres)") String title,
			@JsonPropertyDescription("Explicación en español de 1 o 2 frases, sin tecnicismos") String detail,
			@JsonPropertyDescription("IRRIGATION, WEATHER, SENSOR o CROP") RecommendationCategory category,
			@JsonPropertyDescription("HIGH, MEDIUM o LOW") Priority priority,
			@JsonPropertyDescription("Una de las acciones disponibles indicadas en los datos, o null si no aplica") ActionType action) {
	}

}
