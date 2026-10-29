package com.smartgarden.ai.service;

import com.smartgarden.common.thresholds.CropThresholdClamp;
import com.smartgarden.ai.client.GardenDataClient;
import com.smartgarden.ai.config.AiProperties;
import com.smartgarden.ai.config.WeatherProperties;
import com.smartgarden.ai.domain.ActionType;
import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.AnalysisSource;
import com.smartgarden.ai.domain.AnalysisView;
import com.smartgarden.ai.domain.DryingRate;
import com.smartgarden.ai.domain.FallbackReason;
import com.smartgarden.ai.domain.IrrigationRecommendation;
import com.smartgarden.ai.domain.MoistureLevel;
import com.smartgarden.ai.domain.Projection;
import com.smartgarden.ai.domain.RecommendationDetail;
import com.smartgarden.ai.entity.AnalysisEntity;
import com.smartgarden.ai.repository.AnalysisRepository;
import com.smartgarden.ai.service.InputCollector.AnalysisContext;
import io.grpc.StatusRuntimeException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs irrigation analyses: collect input → moisture level → drying rate → Gemini (or rule-based fallback) →
 * validate coherence → deterministic projection, health and actions → persist → apply to the irrigation
 * config when the mode is AI → emit on the stream.
 * <p>
 * The moisture level is always calculated in Java before Gemini is called, so the message, the traffic light and the
 * action can never contradict the real soil moisture.
 */
@Service
public class AnalysisService {

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

	public static final String TRIGGER_SCHEDULED = "SCHEDULED";

	public static final String TRIGGER_MANUAL = "MANUAL";

	/** Analysis triggered by a change of crop, mode or thresholds. */
	public static final String TRIGGER_CONFIG = "CONFIG";

	/** At most one automatic analysis every 30 s after a config change (Gemini free-tier quota). */
	public static final Duration CONFIG_ANALYSIS_SPACING = Duration.ofSeconds(30);

	private final InputCollector inputCollector;

	private final GeminiAnalyzer gemini;

	private final GardenDataClient garden;

	private final AnalysisRepository repository;

	private final AiProperties aiProps;

	private final WeatherProperties weatherProps;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	private final Subscribers<AnalysisView> subscribers = new Subscribers<>();

	private final Object analysisLock = new Object();

	private final Object configGate = new Object();

	private Instant lastManualRequest;

	private Instant lastConfigAnalysis;

	/** Configuration the last analysis ran with (or the one it applied), to ignore the echo of our own changes. */
	private volatile String lastAnalyzedFingerprint;

	public AnalysisService(InputCollector inputCollector, GeminiAnalyzer gemini, GardenDataClient garden,
			AnalysisRepository repository, AiProperties aiProps, WeatherProperties weatherProps,
			JsonMapper jsonMapper, Clock clock) {
		this.inputCollector = inputCollector;
		this.gemini = gemini;
		this.garden = garden;
		this.repository = repository;
		this.aiProps = aiProps;
		this.weatherProps = weatherProps;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${smartgarden.ai.analysis-interval:30m}",
			initialDelayString = "${smartgarden.ai.analysis-initial-delay:2m}")
	public void runScheduled() {
		try {
			AnalysisContext ctx = inputCollector.collect();
			if (ctx.input().configAvailable() && !ctx.input().aiEnabled()) {
				log.info("Scheduled analysis skipped: AI is disabled in the irrigation config");
				return;
			}
			analyze(ctx, TRIGGER_SCHEDULED);
		}
		catch (RuntimeException ex) {
			log.error("Scheduled analysis failed", ex);
		}
	}

	/** Immediate analysis, limited to one call per cooldown window (free-tier quota). */
	public AnalysisView requestAnalysis() {
		synchronized (this) {
			Instant now = clock.instant();
			Duration cooldown = aiProps.manualRequestCooldown();
			if (lastManualRequest != null && now.isBefore(lastManualRequest.plus(cooldown))) {
				long wait = Duration.between(now, lastManualRequest.plus(cooldown)).toSeconds() + 1;
				throw new RateLimitedException(
						"Solo se permite un análisis manual por minuto. Intenta de nuevo en " + wait + " s");
			}
			lastManualRequest = now;
		}
		return analyze(inputCollector.collect(), TRIGGER_MANUAL);
	}

	/**
	 * Analysis triggered by a change of crop, mode or thresholds (see {@code ConfigChangeWatcher}). It does not consume
	 * the manual cooldown, but {@link #CONFIG_ANALYSIS_SPACING} keeps the Gemini quota under control. The crop and the
	 * thresholds are always re-read from irrigation-service right before analysing.
	 *
	 * @return the new analysis, or {@code null} when it was skipped (no change, spacing or AI disabled)
	 */
	public AnalysisView requestConfigAnalysis() {
		AnalysisContext ctx;
		synchronized (configGate) {
			// The spacing delays the analysis, it never drops it: two quick crop changes still produce the second
			// analysis (it just waits a few seconds for the quota window).
			awaitConfigSlot();
			ctx = inputCollector.collect();
			AnalysisInputData input = ctx.input();
			if (input.configAvailable() && !input.aiEnabled()) {
				log.info("Config analysis skipped: AI is disabled in the irrigation config");
				return null;
			}
			String fingerprint = fingerprint(input.cropId(), input.mode(), input.currentMin(), input.currentMax(),
					input.isRainPause(), input.aiEnabled());
			if (fingerprint.equals(lastAnalyzedFingerprint)) {
				log.debug("Config analysis skipped: configuration already analysed ({})", fingerprint);
				return null;
			}
			lastConfigAnalysis = clock.instant();
			lastAnalyzedFingerprint = fingerprint;
			log.info("Configuration changed ({}); running an immediate analysis", fingerprint);
		}
		return analyze(ctx, TRIGGER_CONFIG);
	}

	/** Sleeps until {@link #CONFIG_ANALYSIS_SPACING} has elapsed since the previous automatic analysis. */
	private void awaitConfigSlot() {
		long waitMillis = spacingWaitMillis(lastConfigAnalysis, clock.instant());
		if (waitMillis <= 0) {
			return;
		}
		log.info("Waiting {} s for the automatic-analysis window", waitMillis / 1000);
		try {
			Thread.sleep(waitMillis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/** Milliseconds left before another automatic analysis is allowed (0 = go ahead). */
	static long spacingWaitMillis(Instant lastAnalysis, Instant now) {
		if (lastAnalysis == null) {
			return 0;
		}
		long elapsed = Duration.between(lastAnalysis, now).toMillis();
		return Math.max(0, CONFIG_ANALYSIS_SPACING.toMillis() - elapsed);
	}

	AnalysisView analyze(AnalysisContext ctx, String trigger) {
		synchronized (analysisLock) {
			Instant now = ctx.now();
			double rainThreshold = weatherProps.rainPauseThreshold();
			DryingRate dryingRate = DryingRateEstimator.estimate(ctx.samples(), now);
			AnalysisInputData input = ctx.input().withDryingRate(dryingRate.ratePerHour());
			MoistureLevel level = MoistureClassifier.classify(input);
			boolean rainExpected = input.rainExpected(rainThreshold);

			IrrigationRecommendation recommendation;
			AnalysisSource source;
			FallbackReason fallbackReason = null;
			String fallbackDetail = null;
			try {
				recommendation = RecommendationValidator.sanitize(gemini.recommend(input, level, rainExpected,
						ActionAdvisor.candidates(input, rainThreshold)));
				// The text must not contradict the level calculated in Java (e.g. "humedad baja" with 99 %).
				CoherenceValidator.check(level, rainExpected, recommendation);
				recommendation = clampToCrop(recommendation, input).withRisk(level.risk());
				source = AnalysisSource.GEMINI;
			}
			catch (RuntimeException ex) {
				fallbackReason = FallbackClassifier.classify(ex);
				fallbackDetail = FallbackClassifier.detail(ex);
				log.warn("Gemini fallback: {} ({}); using rule-based analysis", fallbackReason, fallbackDetail);
				recommendation = RuleBasedAnalyzer.analyze(input, rainThreshold).withRisk(level.risk());
				source = AnalysisSource.RULES;
			}

			// Deterministic parts (never decided by Gemini)
			Projection projection = MoistureProjector.project(input.validMoisture(), dryingRate, ctx.weather(),
					input.minThreshold(), now);
			boolean rainPauseApplies = rainExpected
					&& (input.isRainPause() || Boolean.TRUE.equals(recommendation.pauseForRain()));
			HealthEvaluator.Result health = HealthEvaluator.evaluate(level, input, rainPauseApplies);
			Set<ActionType> eligible = ActionAdvisor.eligible(input, rainThreshold, recommendation.recommendedMin(),
					recommendation.recommendedMax());
			recommendation = recommendation.withDetails(ActionAdvisor.attach(recommendation.details(), eligible))
				.withHours(MoistureProjector.hoursUntil(projection, now));
			if (!input.hasReading()) {
				recommendation = recommendation.withHeadline(input.isSensorFault() ? "Revisa el sensor de humedad"
						: "No hay lecturas recientes del sensor");
			}

			AnalysisEntity entity = new AnalysisEntity(UUID.randomUUID(), now, source, trigger,
					recommendation.recommendedMin(), recommendation.recommendedMax(),
					Boolean.TRUE.equals(recommendation.pauseForRain()), recommendation.hoursUntilNextIrrigation(),
					recommendation.riskLevel(), recommendation.summary(),
					jsonMapper.writeValueAsString(recommendation.recommendations()),
					fallbackReason == null ? null : fallbackReason.name(), jsonMapper.writeValueAsString(input));
			entity.setMoistureLevel(level);
			entity.setInsights(recommendation.headline(), health.healthStatus(), health.nextAction(),
					jsonMapper.writeValueAsString(recommendation.details()),
					jsonMapper.writeValueAsString(projection), fallbackDetail);
			entity = repository.save(entity);

			boolean applied = false;
			if (input.configAvailable() && "AI".equals(input.mode()) && input.aiEnabled()) {
				applied = applyToIrrigation(entity, recommendation);
			}
			if (applied) {
				// The echo of our own change must not trigger another analysis.
				lastAnalyzedFingerprint = fingerprint(input.cropId(), input.mode(), recommendation.recommendedMin(),
						recommendation.recommendedMax(), Boolean.TRUE.equals(recommendation.pauseForRain()), true);
			}
			AnalysisView view = toView(entity);
			log.info("Analysis {} ({}, {}): moisture={} level={} crop={} min={} max={} health={} next={} risk={} applied={}",
					view.id(), source, trigger, input.validMoisture(), level, input.cropName(),
					view.recommendedMin(), view.recommendedMax(), view.healthStatus(), view.nextAction(),
					view.riskLevel(), view.applied());
			subscribers.publish(view);
			return view;
		}
	}

	/** AI thresholds are clamped to the crop profile (±10 points) so they change with the crop. */
	static IrrigationRecommendation clampToCrop(IrrigationRecommendation recommendation, AnalysisInputData input) {
		int[] clamped = CropThresholdClamp.clamp(recommendation.recommendedMin(), recommendation.recommendedMax(),
				input.profileMin(), input.profileMax());
		if (clamped[0] == recommendation.recommendedMin() && clamped[1] == recommendation.recommendedMax()) {
			return recommendation;
		}
		log.info("AI thresholds {}-{} clamped to the crop profile {}: {}-{}", recommendation.recommendedMin(),
				recommendation.recommendedMax(), input.cropName(), clamped[0], clamped[1]);
		return recommendation.withThresholds(clamped[0], clamped[1]);
	}

	static String fingerprint(Long cropId, String mode, int min, int max, boolean rainPause, boolean aiEnabled) {
		return "%s|%s|%d|%d|%s|%s".formatted(cropId == null ? 0 : cropId, mode, min, max, rainPause, aiEnabled);
	}

	private boolean applyToIrrigation(AnalysisEntity entity, IrrigationRecommendation r) {
		try {
			boolean applied = garden
				.applyRecommendation(r.recommendedMin(), r.recommendedMax(), Boolean.TRUE.equals(r.pauseForRain()),
						entity.getId().toString())
				.getApplied();
			if (applied) {
				entity.markApplied();
				repository.save(entity);
			}
			return applied;
		}
		catch (StatusRuntimeException ex) {
			log.warn("Could not apply analysis {} to irrigation-service: {}", entity.getId(), ex.getStatus());
			return false;
		}
	}

	public AnalysisView latest() {
		return repository.findTopByOrderByCreatedAtDesc()
			.map(this::toView)
			.orElseThrow(() -> new NoSuchElementException("Aún no hay análisis disponibles"));
	}

	public List<AnalysisView> list(int limit) {
		return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream().map(this::toView).toList();
	}

	public Subscribers<AnalysisView> subscribers() {
		return subscribers;
	}

	AnalysisView toView(AnalysisEntity e) {
		List<RecommendationDetail> details = e.getRecommendationDetails() == null ? List.of()
				: jsonMapper.readValue(e.getRecommendationDetails(), new TypeReference<List<RecommendationDetail>>() {
				});
		Projection projection = e.getProjection() == null ? null
				: jsonMapper.readValue(e.getProjection(), Projection.class);
		return new AnalysisView(e.getId(), e.getCreatedAt(), e.getSource(), e.getTrigger(), e.getRecommendedMin(),
				e.getRecommendedMax(), e.isPauseForRain(), e.getHoursUntilNextIrrigation(), e.getRiskLevel(),
				e.getSummary(), jsonMapper.readValue(e.getRecommendations(), new TypeReference<List<String>>() {
				}), e.isApplied(), e.getFallbackReason(),
				jsonMapper.readValue(e.getInputSnapshot(), AnalysisInputData.class), e.getHeadline(),
				e.getHealthStatus(), e.getNextAction(), details, projection, e.getFallbackDetail(),
				e.getMoistureLevel());
	}

}
