package com.smartgarden.ai.service;

import com.smartgarden.ai.config.AiProperties;
import com.smartgarden.ai.domain.ActionType;
import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.FallbackReason;
import com.smartgarden.ai.domain.GeminiAnswer;
import com.smartgarden.ai.domain.MoistureLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Asks Gemini (Spring AI {@link ChatClient}) for a structured {@link GeminiAnswer}. Spring AI and SDK retries are
 * disabled; instead, when the configured model is overloaded (503), out of quota (429) or gone (404), the same
 * request goes once to each of {@code smartgarden.ai.gemini-fallback-models} (other models have their own capacity
 * and free-tier quota). All attempts share the {@code smartgarden.ai.gemini-timeout} budget.
 */
@Component
public class GeminiAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(GeminiAnalyzer.class);

	/** Failures for which another model may still answer. */
	private static final Set<FallbackReason> TRY_NEXT_MODEL = Set.of(FallbackReason.UNAVAILABLE,
			FallbackReason.QUOTA_EXCEEDED, FallbackReason.MODEL_NOT_FOUND);

	static final String SYSTEM_PROMPT = """
			Eres un agrónomo experto en riego de huertas urbanas y cultivos hortícolas en Colombia, y explicas
			las cosas de forma sencilla a personas sin conocimientos técnicos.
			Recibes datos de un sistema de riego automático con un sensor de humedad de suelo (0–100 %):
			- cultivo seleccionado y su rango de humedad de referencia (perfil),
			- umbrales actuales (mínimo / máximo) con los que el ESP32 enciende y apaga la bomba,
			- humedad actual (solo si hay una lectura válida reciente) y resumen de las últimas 24 horas,
			- velocidad de secado del suelo y riegos de las últimas 24 horas,
			- pronóstico de las próximas 12 horas: probabilidad máxima de lluvia, precipitación, temperatura
			  y humedad del aire,
			- las acciones de un clic que la aplicación puede ofrecer en este momento.
			Tu tarea: recomendar los umbrales de humedad, decidir si conviene pausar el riego por lluvia,
			evaluar el riesgo hídrico y redactar un titular y recomendaciones claras.
			Reglas:
			- recommendedMin y recommendedMax: enteros entre 10 y 90, con recommendedMax - recommendedMin >= 10.
			  Mantente cerca del perfil del cultivo salvo que el clima o el historial justifiquen ajustarlo.
			- pauseForRain = true solo si la lluvia prevista es suficiente para reemplazar el riego.
			- riskLevel: LOW, MEDIUM o HIGH.
			- headline: una frase corta y amable en español (máximo 60 caracteres).
			- summary: en español, máximo 2 frases.
			- recommendationDetails: entre 1 y 4, en español, concretas, con título corto, explicación de 1 o 2
			  frases, categoría (IRRIGATION, WEATHER, SENSOR, CROP) y prioridad (HIGH, MEDIUM, LOW).
			  En "action" usa solo una de las acciones disponibles indicadas en los datos, o null.
			- Si no hay lectura válida del sensor, dilo claramente y no inventes un valor de humedad.
			- El nivel de humedad (óptima, baja, muy seca, alta, saturada o error de sensor) YA viene calculado en los
			  datos. El titular, el resumen y las recomendaciones deben ser coherentes con él: nunca digas que la
			  humedad está baja o que hay que regar si el nivel es ALTA o SATURADA, y nunca digas que no hace falta
			  regar si el nivel es MUY SECA.
			- Compara siempre la humedad actual con el perfil del cultivo, no con valores inventados.
			Responde ÚNICAMENTE con el objeto JSON en el formato solicitado, sin texto adicional.
			""";

	private final ObjectProvider<ChatClient.Builder> chatClientBuilder;

	private final AiProperties props;

	private final String apiKey;

	private final String primaryModel;

	private volatile ChatClient chatClient;

	public GeminiAnalyzer(ObjectProvider<ChatClient.Builder> chatClientBuilder, AiProperties props,
			@Value("${spring.ai.google.genai.api-key:}") String apiKey,
			@Value("${spring.ai.google.genai.chat.model:}") String primaryModel) {
		this.chatClientBuilder = chatClientBuilder;
		this.props = props;
		this.apiKey = apiKey;
		this.primaryModel = primaryModel;
	}

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank() && chatClientBuilder.getIfAvailable() != null;
	}

	/**
	 * @throws GeminiNotConfiguredException if there is no API key
	 * @throws RuntimeException on API errors (quota, auth, model, network, parsing) or timeout
	 */
	public GeminiAnswer recommend(AnalysisInputData input, MoistureLevel level, boolean rainExpected,
			Set<ActionType> availableActions) {
		if (!isConfigured()) {
			throw new GeminiNotConfiguredException();
		}
		String userPrompt = userPrompt(input, level, rainExpected, availableActions);
		if (log.isDebugEnabled()) {
			log.debug("Datos exactos enviados a Gemini (nivel calculado {}):\n{}", level, userPrompt);
		}
		long deadline = System.currentTimeMillis() + props.geminiTimeout().toMillis();
		List<String> models = new ArrayList<>();
		models.add(null); // configured model with the default options
		props.geminiFallbackModels().stream()
			.filter(m -> m != null && !m.isBlank() && !m.equals(primaryModel))
			.distinct()
			.forEach(models::add);
		RuntimeException first = null;
		for (String model : models) {
			long remaining = deadline - System.currentTimeMillis();
			if (remaining <= 0) {
				break;
			}
			try {
				return call(model, userPrompt, remaining);
			}
			catch (RuntimeException ex) {
				FallbackReason reason = FallbackClassifier.classify(ex);
				if (first == null) {
					first = ex;
				}
				else {
					first.addSuppressed(ex);
				}
				if (!TRY_NEXT_MODEL.contains(reason)) {
					break;
				}
				log.info("Gemini model {} failed with {}; trying the next model", name(model), reason);
			}
		}
		throw first != null ? first
				: new IllegalStateException("Gemini did not answer within " + props.geminiTimeout(),
						new TimeoutException());
	}

	/** One model call; {@code model == null} uses the configured model and options. */
	private GeminiAnswer call(String model, String userPrompt, long timeoutMillis) {
		long start = System.currentTimeMillis();
		CompletableFuture<GeminiAnswer> call = CompletableFuture.supplyAsync(() -> {
			ChatClient.ChatClientRequestSpec request = client().prompt().system(SYSTEM_PROMPT).user(userPrompt);
			if (model != null) {
				request = request.options(GoogleGenAiChatOptions.builder()
					.model(model)
					.temperature(0.2)
					.responseMimeType("application/json"));
			}
			return request.call().entity(GeminiAnswer.class);
		}, Thread::startVirtualThread);
		try {
			GeminiAnswer answer = call.get(timeoutMillis, TimeUnit.MILLISECONDS);
			log.info("Gemini ({}) answered in {} ms", name(model), System.currentTimeMillis() - start);
			return answer;
		}
		catch (TimeoutException ex) {
			call.cancel(true);
			throw new IllegalStateException("Gemini did not answer within " + props.geminiTimeout(), ex);
		}
		catch (ExecutionException ex) {
			throw ex.getCause() instanceof RuntimeException re ? re : new IllegalStateException(ex.getCause());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for Gemini", ex);
		}
	}

	private String name(String model) {
		return model == null ? primaryModel : model;
	}

	/** Plain "key: value" lines: no braces, because ChatClient renders user text as a template. */
	static String userPrompt(AnalysisInputData in, MoistureLevel level, boolean rainExpected,
			Set<ActionType> availableActions) {
		StringBuilder sb = new StringBuilder("Datos actuales de la huerta:\n");
		line(sb, "Cultivo", in.cropName());
		line(sb, "Modo de riego", in.mode());
		line(sb, "Perfil del cultivo (humedad %)", in.profileMin() + "–" + in.profileMax());
		line(sb, "Umbrales actuales (humedad %)", in.currentMin() + "–" + in.currentMax());
		line(sb, "Pausa por lluvia activa", in.isRainPause() ? "sí" : "no");
		if (in.hasReading()) {
			line(sb, "Humedad actual del suelo (%)", in.currentMoisture());
			line(sb, "Bomba encendida", in.pumpOn() ? "sí" : "no");
		}
		else if (in.isSensorFault()) {
			line(sb, "Humedad actual del suelo", "desconocida: el sensor envía lecturas no válidas");
		}
		else {
			line(sb, "Humedad actual del suelo", "desconocida: no hay lecturas válidas en los últimos 5 minutos");
		}
		line(sb, "Dispositivo en línea", in.deviceOnline() ? "sí" : "no");
		if (in.samples24h() > 0) {
			line(sb, "Humedad 24 h promedio / mínima / máxima (%)",
					"%.1f / %d / %d".formatted(in.avgMoisture24h(), in.minMoisture24h(), in.maxMoisture24h()));
		}
		else {
			line(sb, "Historial 24 h", "sin datos válidos");
		}
		if (in.dryingRatePerHour() != null) {
			line(sb, "Velocidad de secado (puntos de humedad por hora)", "%.2f".formatted(in.dryingRatePerHour()));
		}
		if (in.irrigations24h() != null) {
			line(sb, "Riegos en las últimas 24 h", in.irrigations24h() + " (" + "%.1f".formatted(
					in.pumpMinutes24h() == null ? 0 : in.pumpMinutes24h()) + " min de bomba)");
		}
		if (in.weatherAvailable()) {
			line(sb, "Probabilidad máxima de lluvia próximas 12 h (%)", "%.0f".formatted(in.rainProbabilityMax12h()));
			line(sb, "Precipitación total próximas 12 h (mm)", "%.1f".formatted(in.precipitationSum12h()));
			line(sb, "Temperatura del aire (°C)", "%.1f".formatted(in.temperature()));
			line(sb, "Humedad relativa del aire (%)", "%.0f".formatted(in.airHumidity()));
		}
		else {
			line(sb, "Pronóstico del clima", "no disponible");
		}
		line(sb, "Acciones disponibles para \"action\"", availableActions.isEmpty() ? "ninguna (usa null)"
				: availableActions.stream().map(a -> a.name() + " (" + a.label() + ")").collect(Collectors.joining(", ")));
		// Level first in a clearly marked block: the model must describe THIS state, not guess it.
		sb.append("\nNivel de humedad calculado por el sistema (obligatorio respetarlo): ")
			.append(level.name())
			.append(" (")
			.append(level.label())
			.append(")\n");
		line(sb, "Cómo debe ser el mensaje", guidance(level));
		line(sb, "Lluvia suficiente para reemplazar el riego en 12 h", rainExpected ? "sí" : "no");
		return sb.toString().replace("{", "(").replace("}", ")");
	}

	/** Qué debe decir el mensaje para cada nivel (evita que el modelo invente el estado del suelo). */
	static String guidance(MoistureLevel level) {
		return switch (level) {
			case SENSOR_ERROR -> "no hay datos válidos del sensor: dilo y no afirmes ninguna humedad";
			case VERY_DRY -> "la tierra está muy seca y por debajo del mínimo: recomienda regar ya";
			case DRY -> "la humedad está por debajo del mínimo: riega pronto o aprovecha la lluvia si se espera";
			case OPTIMAL -> "la humedad está en el rango ideal del cultivo: no hace falta regar";
			case WET -> "la humedad está por encima del máximo: no riegues y deja que baje";
			case SATURATED -> "el suelo está saturado: no riegues, revisa el drenaje o el sensor";
		};
	}

	private static void line(StringBuilder sb, String label, Object value) {
		sb.append("- ").append(label).append(": ").append(value).append('\n');
	}

	private ChatClient client() {
		ChatClient current = chatClient;
		if (current == null) {
			current = chatClientBuilder.getObject().build();
			chatClient = current;
		}
		return current;
	}

}
