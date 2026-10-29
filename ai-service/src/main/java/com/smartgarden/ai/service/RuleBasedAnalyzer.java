package com.smartgarden.ai.service;

import com.smartgarden.common.thresholds.CropThresholdClamp;
import com.smartgarden.ai.domain.ActionType;
import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.IrrigationRecommendation;
import com.smartgarden.ai.domain.MoistureLevel;
import com.smartgarden.ai.domain.Priority;
import com.smartgarden.ai.domain.RecommendationCategory;
import com.smartgarden.ai.domain.RecommendationDetail;
import com.smartgarden.ai.domain.RiskLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic fallback used when Gemini is not configured, fails, exceeds its quota or answers something incoherent:
 * crop-profile thresholds, a message that always matches the calculated {@link MoistureLevel}, and a pause when rain is
 * expected in the next 12 h. Fills the same fields as Gemini (headline, summary and recommendation details).
 */
public final class RuleBasedAnalyzer {

	private static final double HOT_TEMPERATURE = 28;

	private static final Locale ES = Locale.forLanguageTag("es");

	private RuleBasedAnalyzer() {
	}

	public static IrrigationRecommendation analyze(AnalysisInputData in, double rainPauseThreshold) {
		int[] thresholds = CropThresholdClamp.clamp(baseMin(in), baseMax(in), in.profileMin(), in.profileMax());
		int min = thresholds[0];
		int max = thresholds[1];
		boolean rainExpected = in.rainExpected(rainPauseThreshold);
		MoistureLevel level = MoistureClassifier.classify(in);
		Integer m = in.validMoisture();
		String crop = crop(in);
		String cropLower = crop.toLowerCase(ES);
		String range = min + "–" + max + " %";

		RiskLevel risk = level.risk();
		String headline;
		String summary;
		List<RecommendationDetail> details = new ArrayList<>();
		// Below 15 % the ESP32 irrigates even when the rain pause is active.
		boolean pauseForRain = rainExpected
				&& !(level == MoistureLevel.VERY_DRY && m != null && m < HealthEvaluator.CRITICAL_MOISTURE);

		switch (level) {
			case SENSOR_ERROR -> {
				if (in.isSensorFault()) {
					headline = "Revisa el sensor de humedad";
					summary = "El sensor envía lecturas no válidas, así que no se puede saber la humedad real del suelo. "
							+ "Revisa la conexión del sensor antes de confiar en el riego automático.";
					details.add(detail("Sensor con fallas",
							"Las últimas lecturas no son válidas: el sensor puede estar desconectado o fuera de la tierra.",
							RecommendationCategory.SENSOR, Priority.HIGH, ActionType.CHECK_SENSOR));
				}
				else {
					headline = "No hay lecturas recientes del sensor";
					summary = "No hay una lectura válida del sensor en los últimos 5 minutos; se mantienen los umbrales del "
							+ "perfil de %s (%s).".formatted(crop, range);
					details.add(detail("Sin datos del sensor",
							"Verifica que el ESP32 esté encendido y conectado al Wi-Fi para volver a recibir lecturas.",
							RecommendationCategory.SENSOR, Priority.HIGH, ActionType.CHECK_SENSOR));
				}
			}
			case VERY_DRY -> {
				headline = rainExpected ? "¡Tu %s necesita agua ya, aunque llueva!".formatted(cropLower)
						: "¡Tu %s necesita agua ya!".formatted(cropLower);
				summary = ("La humedad está en %d %%, muy por debajo del mínimo de %s (%s). "
						+ "Riega ahora: la tierra está demasiado seca.%s")
					.formatted(m, crop, range,
							rainExpected ? " La lluvia prevista no llegará a tiempo para recuperarla." : "");
				details.add(detail("Riega ahora",
						"La humedad (%d %%) está %d puntos por debajo del mínimo (%d %%)."
								.formatted(m, min - m, min),
						RecommendationCategory.IRRIGATION, Priority.HIGH, null));
				if (m < HealthEvaluator.CRITICAL_MOISTURE) {
					details.add(detail("Riego inmediato",
							"Con %d %% de humedad la tierra está casi sin agua: el sistema riega aunque la pausa por lluvia "
									+ "esté activa.".formatted(m),
							RecommendationCategory.IRRIGATION, Priority.HIGH, null));
				}
			}
			case DRY -> {
				if (rainExpected) {
					headline = "%s puede aprovechar la lluvia".formatted(crop);
					summary = ("La humedad (%d %%) está un poco baja, pero se esperan %.1f mm de lluvia en las "
							+ "próximas 12 h. Se pausa el riego para aprovechar el agua natural.")
						.formatted(m, in.precipitationSum12h());
					details.add(detail("Humedad un poco baja",
							"La humedad (%d %%) está por debajo del mínimo de %s (%s), pero la lluvia prevista la "
									+ "recuperará.".formatted(m, crop, range),
							RecommendationCategory.IRRIGATION, Priority.MEDIUM, null));
				}
				else {
					headline = "Tu %s necesita un poco de agua".formatted(cropLower);
					summary = "La humedad (%d %%) está por debajo del mínimo ideal para %s (%s). Conviene regar pronto."
						.formatted(m, crop, range);
					details.add(detail("Humedad baja",
							"La humedad (%d %%) está %d puntos por debajo del mínimo de %s (%s)."
									.formatted(m, min - m, crop, range),
							RecommendationCategory.IRRIGATION, Priority.HIGH, null));
				}
			}
			case OPTIMAL -> {
				headline = "Tu %s está en su punto".formatted(cropLower);
				summary = "La humedad (%d %%) está dentro del rango ideal de %s (%s). No hace falta regar.%s"
					.formatted(m, crop, range,
							rainExpected ? " La lluvia prevista mantendrá la humedad." : "");
				details.add(detail("Todo en orden",
						"Mantén los umbrales del perfil de %s y revisa la huerta a diario.".formatted(crop),
						RecommendationCategory.CROP, Priority.LOW, null));
			}
			case WET -> {
				headline = "Humedad alta, no riegues";
				summary = ("La tierra tiene %d %%, por encima del %d %% ideal para %s (%s). "
						+ "Espera a que baje para evitar hongos en la raíz.").formatted(m, max, crop, range);
				details.add(detail("No riegues",
						"La humedad supera el máximo del cultivo; deja que la tierra se seque un poco antes de volver a regar.",
						RecommendationCategory.IRRIGATION, Priority.MEDIUM, null));
				if (rainExpected) {
					details.add(detail("Va a llover sobre suelo húmedo",
							"Se esperan %.1f mm más (probabilidad %.0f %%). Protege la maceta para que no se acumule agua."
								.formatted(in.precipitationSum12h(), in.rainProbabilityMax12h()),
							RecommendationCategory.WEATHER, Priority.MEDIUM, null));
				}
			}
			default -> {
				// SATURATED
				headline = rainExpected ? "Suelo saturado: cuidado con el encharcamiento" : "Suelo saturado: no riegues";
				summary = rainExpected
						? ("La humedad está en %d %% y viene más lluvia: el suelo está saturado. "
								+ "Revisa que la maceta drene bien o protégela de la lluvia.").formatted(m)
						: ("La humedad está en %d %%: el suelo está saturado, muy por encima del máximo de %s (%s). "
								+ "Revisa el drenaje y no riegues hasta que baje.").formatted(m, crop, range);
				details.add(detail("Revisa el drenaje",
						"El agua no se está evacuando bien; el exceso puede pudrir la raíz y ahogar el cultivo.",
						RecommendationCategory.CROP, Priority.HIGH, null));
				if (m != null && m >= MoistureClassifier.SATURATED_FLOOR) {
					details.add(detail("Comprueba el sensor",
							"Con la tierra encharcada el sensor puede estar midiendo agua en vez de suelo: revísalo cuando "
									+ "baje el nivel.",
							RecommendationCategory.SENSOR, Priority.MEDIUM, ActionType.CHECK_SENSOR));
				}
			}
		}

		if (in.configAvailable() && !"AI".equals(in.mode()) && (min != in.currentMin() || max != in.currentMax())) {
			details.add(detail("Ajusta los umbrales",
					"Para %s se recomienda mantener la humedad entre %d %% y %d %%.".formatted(crop, min, max),
					RecommendationCategory.CROP, Priority.MEDIUM, ActionType.APPLY_THRESHOLDS));
		}
		if (rainExpected) {
			String text = switch (level) {
				case VERY_DRY, DRY -> ("Se esperan %.1f mm (probabilidad %.0f %%), pero el suelo está por debajo del "
						+ "mínimo: la lluvia no reemplaza el riego.").formatted(in.precipitationSum12h(),
								in.rainProbabilityMax12h());
				case WET, SATURATED -> "Se esperan %.1f mm más (probabilidad %.0f %%): el suelo ya está húmedo."
					.formatted(in.precipitationSum12h(), in.rainProbabilityMax12h());
				default -> "Probabilidad de lluvia de %.0f %% y %.1f mm en las próximas 12 h: no hace falta regar."
					.formatted(in.rainProbabilityMax12h(), in.precipitationSum12h());
			};
			ActionType action = in.configAvailable() && !in.isRainPause() && !"AI".equals(in.mode())
					&& level != MoistureLevel.VERY_DRY ? ActionType.ENABLE_RAIN_PAUSE : null;
			details.add(detail("Lluvia prevista", text, RecommendationCategory.WEATHER,
					level.isDry() ? Priority.HIGH : Priority.MEDIUM, action));
		}
		else if (in.isRainPause() && in.weatherAvailable() && (level.isDry() || level == MoistureLevel.OPTIMAL)) {
			details.add(detail("Pausa por lluvia activa",
					"No se espera lluvia importante en las próximas 12 h; la pausa podría dejar el suelo seco.",
					RecommendationCategory.WEATHER, Priority.LOW, ActionType.DISABLE_RAIN_PAUSE));
		}
		if (in.weatherAvailable() && in.temperature() >= HOT_TEMPERATURE && !level.isWet() && level.isKnown()) {
			details.add(detail("Calor",
					"Temperatura alta (%.0f °C): riega temprano en la mañana o al atardecer.".formatted(in.temperature()),
					RecommendationCategory.WEATHER, Priority.LOW, null));
		}
		if (!in.weatherAvailable()) {
			details.add(detail("Sin pronóstico", "No se pudo consultar el clima; el análisis no considera lluvia.",
					RecommendationCategory.WEATHER, Priority.LOW, null));
		}
		if (details.isEmpty()) {
			details.add(detail("Todo en orden",
					"Mantén los umbrales del perfil de %s y revisa la huerta a diario.".formatted(crop),
					RecommendationCategory.CROP, Priority.LOW, null));
		}
		List<RecommendationDetail> limited = List
			.copyOf(details.subList(0, Math.min(details.size(), RecommendationValidator.MAX_RECOMMENDATIONS)));
		return new IrrigationRecommendation(min, max, pauseForRain, null, risk, summary,
				limited.stream().map(RecommendationDetail::detail).toList(), headline, limited);
	}

	private static String crop(AnalysisInputData in) {
		return in.cropName() == null || in.cropName().isBlank() ? "el cultivo" : in.cropName();
	}

	private static RecommendationDetail detail(String title, String text, RecommendationCategory category,
			Priority priority, ActionType action) {
		return new RecommendationDetail(title, text, category, priority, action == null ? null : action.toAction());
	}

	private static int baseMin(AnalysisInputData in) {
		return in.hasProfileRange() ? in.profileMin() : in.minThreshold();
	}

	private static int baseMax(AnalysisInputData in) {
		return in.hasProfileRange() ? in.profileMax() : in.maxThreshold();
	}

}
