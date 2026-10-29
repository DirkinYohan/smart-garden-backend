package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.IrrigationRecommendation;
import com.smartgarden.ai.domain.MoistureLevel;
import com.smartgarden.ai.domain.RecommendationDetail;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprueba que el texto (titular, resumen y recomendaciones) no contradiga el nivel de humedad ya calculado. Un texto
 * incoherente se descarta y se usa el respaldo por reglas con {@code fallbackReason = INVALID_RESPONSE}.
 * <p>
 * Ejemplos: no se puede decir "humedad baja" con el nivel {@code WET} ni "no hace falta regar" con {@code VERY_DRY}.
 */
public final class CoherenceValidator {

	/** Afirmaciones de suelo seco: prohibidas con la tierra húmeda o saturada. */
	private static final List<Pattern> DRY_CLAIMS = compile("humedad (?:esta |es )?(?:muy )?baja",
			"suelo (?:esta |es )?(?:muy )?seco", "tierra (?:esta |es )?(?:muy )?seca",
			"por debajo del (?:minimo|rango)", "necesita (?:mas |mucha )?agua",
			"(?:regar|riega|riegues) (?:ahora|urgente|cuanto antes)",
			"(?:hay que|conviene|se recomienda|debes|deberias) regar", "demasiado sec[ao]", "falta de agua",
			"estres hidrico");

	/** Afirmaciones de exceso de agua: prohibidas con la tierra seca o en el rango ideal. */
	private static final List<Pattern> WET_CLAIMS = compile("humedad (?:esta |es )?(?:muy )?alta",
			"(?:esta |es )?muy humed[ao]", "saturad[ao]", "encharc", "exceso de agua", "demasiada agua",
			"por encima del (?:maximo|rango)");

	/** "No hace falta regar": prohibido con la tierra muy seca (o seca si no se espera lluvia). */
	private static final List<Pattern> NO_WATER_CLAIMS = compile("no (?:hace falta|se debe|hay que|es necesario) regar",
			"no riegues", "no necesita regar", "evita regar");

	private static final int MAX_TERMS_IN_MESSAGE = 3;

	private CoherenceValidator() {
	}

	private static List<Pattern> compile(String... regex) {
		List<Pattern> patterns = new ArrayList<>(regex.length);
		for (String r : regex) {
			patterns.add(Pattern.compile(r));
		}
		return List.copyOf(patterns);
	}

	/** Texto completo del análisis (titular + resumen + recomendaciones). */
	static String textOf(IrrigationRecommendation r) {
		StringBuilder sb = new StringBuilder();
		sb.append(r.headline() == null ? "" : r.headline()).append(' ');
		sb.append(r.summary() == null ? "" : r.summary());
		for (RecommendationDetail d : r.details()) {
			sb.append(' ').append(d.title() == null ? "" : d.title());
			sb.append(' ').append(d.detail() == null ? "" : d.detail());
		}
		return normalize(sb.toString());
	}

	/**
	 * @throws InvalidRecommendationException when the text contradicts {@code level}
	 */
	public static void check(MoistureLevel level, boolean rainExpected, IrrigationRecommendation recommendation) {
		List<String> problems = problems(level, rainExpected, textOf(recommendation));
		if (!problems.isEmpty()) {
			String reason = String.join(", ", problems.subList(0, Math.min(problems.size(), MAX_TERMS_IN_MESSAGE)));
			throw new InvalidRecommendationException(
					"respuesta incoherente con el nivel " + level + " (menciona: " + reason + ")");
		}
	}

	/** @return los términos contradictorios encontrados (vacío si el texto es coherente) */
	public static List<String> problems(MoistureLevel level, boolean rainExpected, String text) {
		String t = normalize(text);
		List<String> found = new ArrayList<>();
		switch (level) {
			case SENSOR_ERROR -> {
				// Sin datos válidos no se puede afirmar cómo está la tierra.
				found.addAll(any(t, DRY_CLAIMS));
				found.addAll(any(t, WET_CLAIMS));
			}
			case VERY_DRY -> {
				found.addAll(any(t, WET_CLAIMS));
				found.addAll(any(t, NO_WATER_CLAIMS));
			}
			case DRY -> {
				found.addAll(any(t, WET_CLAIMS));
				if (!rainExpected) {
					found.addAll(any(t, NO_WATER_CLAIMS));
				}
			}
			case OPTIMAL -> {
				found.addAll(any(t, DRY_CLAIMS));
				found.addAll(any(t, WET_CLAIMS));
			}
			// La tierra está húmeda o saturada: no se puede decir que esté seca.
			case WET, SATURATED -> found.addAll(any(t, DRY_CLAIMS));
		}
		return found;
	}

	private static List<String> any(String text, List<Pattern> patterns) {
		List<String> found = new ArrayList<>();
		for (Pattern pattern : patterns) {
			Matcher matcher = pattern.matcher(text);
			if (matcher.find()) {
				found.add(matcher.group());
			}
		}
		return found;
	}

	/** Minúsculas y sin tildes, para comparar sin depender de la acentuación del modelo. */
	static String normalize(String text) {
		if (text == null) {
			return "";
		}
		return Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("\\s+", " ");
	}

}
