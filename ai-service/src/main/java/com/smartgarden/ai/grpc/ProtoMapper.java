package com.smartgarden.ai.grpc;

import com.google.protobuf.Timestamp;
import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.AnalysisSource;
import com.smartgarden.ai.domain.AnalysisView;
import com.smartgarden.ai.domain.MoistureLevel;
import com.smartgarden.ai.domain.WeatherSnapshot;
import com.smartgarden.contracts.ai.v1.Analysis;
import com.smartgarden.contracts.ai.v1.ActionType;
import com.smartgarden.contracts.ai.v1.AnalysisInput;
import com.smartgarden.contracts.ai.v1.Confidence;
import com.smartgarden.contracts.ai.v1.DailyMoisture;
import com.smartgarden.contracts.ai.v1.InputSnapshot;
import com.smartgarden.contracts.ai.v1.Priority;
import com.smartgarden.contracts.ai.v1.Projection;
import com.smartgarden.contracts.ai.v1.ProjectionPoint;
import com.smartgarden.contracts.ai.v1.RecommendationCategory;
import com.smartgarden.contracts.ai.v1.RecommendationDetail;
import com.smartgarden.contracts.ai.v1.Stats;
import com.smartgarden.contracts.ai.v1.SuggestedAction;
import com.smartgarden.contracts.ai.v1.ThresholdPoint;
import com.smartgarden.ai.service.StatsService;
import com.smartgarden.contracts.ai.v1.CurrentWeather;
import com.smartgarden.contracts.ai.v1.HourlyForecast;
import com.smartgarden.contracts.ai.v1.RiskLevel;
import com.smartgarden.contracts.ai.v1.Weather;
import java.time.Instant;

final class ProtoMapper {

	private ProtoMapper() {
	}

	static Timestamp timestamp(Instant instant) {
		return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
	}

	static Analysis analysis(AnalysisView v) {
		Analysis.Builder b = Analysis.newBuilder()
			.setId(v.id().toString())
			.setCreatedAt(timestamp(v.createdAt()))
			.setSource(v.source() == AnalysisSource.GEMINI ? com.smartgarden.contracts.ai.v1.AnalysisSource.ANALYSIS_SOURCE_GEMINI
					: com.smartgarden.contracts.ai.v1.AnalysisSource.ANALYSIS_SOURCE_RULES)
			.setRecommendedMin(v.recommendedMin())
			.setRecommendedMax(v.recommendedMax())
			.setPauseForRain(v.pauseForRain())
			.setRiskLevel(switch (v.riskLevel()) {
				case LOW -> RiskLevel.RISK_LEVEL_LOW;
				case MEDIUM -> RiskLevel.RISK_LEVEL_MEDIUM;
				case HIGH -> RiskLevel.RISK_LEVEL_HIGH;
			})
			.setSummary(v.summary())
			.addAllRecommendations(v.recommendations())
			.setApplied(v.applied())
			.setTrigger(v.trigger())
			.setInput(input(v.input()));
		if (v.hoursUntilNextIrrigation() != null) {
			b.setHoursUntilNextIrrigation(v.hoursUntilNextIrrigation());
		}
		if (v.fallbackReason() != null) {
			b.setFallbackReason(v.fallbackReason());
		}
		if (v.fallbackDetail() != null) {
			b.setFallbackDetail(v.fallbackDetail());
		}
		if (!v.legacy()) {
			if (v.headline() != null) {
				b.setHeadline(v.headline());
			}
			b.setHealthStatus(com.smartgarden.contracts.ai.v1.HealthStatus.valueOf("HEALTH_STATUS_" + v.healthStatus().name()));
			if (v.nextAction() != null) {
				b.setNextAction(com.smartgarden.contracts.ai.v1.NextAction.valueOf("NEXT_ACTION_" + v.nextAction().name()));
			}
			v.recommendationDetails().forEach(d -> b.addRecommendationDetails(detail(d)));
			b.setInputSnapshot(snapshot(v.input(), v.moistureLevel()));
			if (v.moistureLevel() != null) {
				b.setMoistureLevel(level(v.moistureLevel()));
			}
			if (v.projection() != null) {
				b.setProjection(projection(v.projection()));
			}
		}
		return b.build();
	}

	static RecommendationDetail detail(com.smartgarden.ai.domain.RecommendationDetail d) {
		RecommendationDetail.Builder b = RecommendationDetail.newBuilder()
			.setTitle(d.title())
			.setDetail(d.detail())
			.setCategory(RecommendationCategory.valueOf("RECOMMENDATION_CATEGORY_" + d.category().name()))
			.setPriority(Priority.valueOf("PRIORITY_" + d.priority().name()));
		if (d.action() != null) {
			b.setAction(SuggestedAction.newBuilder()
				.setType(ActionType.valueOf("ACTION_TYPE_" + d.action().type().name()))
				.setLabel(d.action().label()));
		}
		return b.build();
	}

	static com.smartgarden.contracts.ai.v1.MoistureLevel level(MoistureLevel level) {
		return com.smartgarden.contracts.ai.v1.MoistureLevel.valueOf("MOISTURE_LEVEL_" + level.name());
	}

	static InputSnapshot snapshot(AnalysisInputData in, MoistureLevel level) {
		InputSnapshot.Builder b = InputSnapshot.newBuilder()
			.setIrrigations24H(in.irrigations24h() == null ? 0 : in.irrigations24h())
			.setPumpMinutes24H(in.pumpMinutes24h() == null ? 0 : in.pumpMinutes24h())
			.setCropName(in.cropName())
			.setCropMin(in.profileMin())
			.setCropMax(in.profileMax())
			.setCurrentMin(in.currentMin())
			.setCurrentMax(in.currentMax())
			.setMode(in.mode())
			.setSensorOk(in.hasReading() && !in.isSensorFault())
			.setRainPause(in.isRainPause())
			.setCropId(in.cropId() == null ? 0 : in.cropId());
		if (level != null) {
			b.setMoistureLevel(level(level));
		}
		if (in.validMoisture() != null) {
			b.setCurrentMoisture(in.validMoisture());
		}
		if (in.readingAgeMinutes() != null) {
			b.setReadingAgeMinutes(in.readingAgeMinutes());
		}
		if (in.samples24h() > 0) {
			b.setAvgMoisture24H(in.avgMoisture24h())
				.setMinMoisture24H(in.minMoisture24h())
				.setMaxMoisture24H(in.maxMoisture24h());
		}
		if (in.dryingRatePerHour() != null) {
			b.setDryingRatePerHour(in.dryingRatePerHour());
		}
		if (in.weatherAvailable()) {
			b.setRainProbability12H(in.rainProbabilityMax12h())
				.setPrecipitation12H(in.precipitationSum12h())
				.setTemperature(in.temperature())
				.setAirHumidity(in.airHumidity());
		}
		return b.build();
	}

	static Projection projection(com.smartgarden.ai.domain.Projection p) {
		Projection.Builder b = Projection.newBuilder()
			.setConfidence(Confidence.valueOf("CONFIDENCE_" + p.confidence().name()));
		p.points()
			.forEach(pt -> b.addPoints(
					ProjectionPoint.newBuilder().setTimestamp(timestamp(pt.timestamp())).setMoisture(pt.moisture())));
		if (p.dryingRatePerHour() != null) {
			b.setDryingRatePerHour(p.dryingRatePerHour());
		}
		if (p.estimatedNextIrrigationAt() != null) {
			b.setEstimatedNextIrrigationAt(timestamp(p.estimatedNextIrrigationAt()));
		}
		return b.build();
	}

	static Stats stats(StatsService.Stats s) {
		Stats.Builder b = Stats.newBuilder()
			.setDays(s.days())
			.setTotalAnalyses(s.totalAnalyses())
			.setGeminiCount(s.geminiCount())
			.setRulesCount(s.rulesCount())
			.setRiskLow(s.risk().getOrDefault(com.smartgarden.ai.domain.RiskLevel.LOW, 0))
			.setRiskMedium(s.risk().getOrDefault(com.smartgarden.ai.domain.RiskLevel.MEDIUM, 0))
			.setRiskHigh(s.risk().getOrDefault(com.smartgarden.ai.domain.RiskLevel.HIGH, 0))
			.setHealthGood(s.health().getOrDefault(com.smartgarden.ai.domain.HealthStatus.GOOD, 0))
			.setHealthAttention(s.health().getOrDefault(com.smartgarden.ai.domain.HealthStatus.ATTENTION, 0))
			.setHealthCritical(s.health().getOrDefault(com.smartgarden.ai.domain.HealthStatus.CRITICAL, 0))
			.setRainPauses(s.rainPauses());
		s.thresholdHistory()
			.forEach(t -> b.addThresholdHistory(ThresholdPoint.newBuilder()
				.setTimestamp(timestamp(t.timestamp()))
				.setMin(t.min())
				.setMax(t.max())
				.setSource(t.source() == AnalysisSource.GEMINI
						? com.smartgarden.contracts.ai.v1.AnalysisSource.ANALYSIS_SOURCE_GEMINI
						: com.smartgarden.contracts.ai.v1.AnalysisSource.ANALYSIS_SOURCE_RULES)
				.setRiskLevel(RiskLevel.valueOf("RISK_LEVEL_" + t.riskLevel().name()))));
		s.dailyMoisture().forEach(d -> {
			DailyMoisture.Builder m = DailyMoisture.newBuilder()
				.setDate(d.getDate())
				.setIrrigations(d.getIrrigations())
				.setPumpMinutes(d.getPumpMinutes());
			if (d.hasAvgMoisture()) {
				m.setAvg(d.getAvgMoisture()).setMin(d.getMinMoisture()).setMax(d.getMaxMoisture());
			}
			b.addDailyMoisture(m);
		});
		return b.build();
	}

	static AnalysisInput input(AnalysisInputData in) {
		return AnalysisInput.newBuilder()
			.setCropName(in.cropName())
			.setMode(in.mode())
			.setCurrentMin(in.currentMin())
			.setCurrentMax(in.currentMax())
			.setProfileMin(in.profileMin())
			.setProfileMax(in.profileMax())
			.setHasReading(in.hasReading())
			.setCurrentMoisture(in.currentMoisture())
			.setAvgMoisture24H(in.avgMoisture24h())
			.setMinMoisture24H(in.minMoisture24h())
			.setMaxMoisture24H(in.maxMoisture24h())
			.setWeatherAvailable(in.weatherAvailable())
			.setRainProbabilityMax12H(in.rainProbabilityMax12h())
			.setPrecipitationSum12H(in.precipitationSum12h())
			.setTemperature(in.temperature())
			.setAirHumidity(in.airHumidity())
			.setCropId(in.cropId() == null ? 0 : in.cropId())
			.build();
	}

	static Weather weather(WeatherSnapshot w) {
		Weather.Builder b = Weather.newBuilder()
			.setFetchedAt(timestamp(w.fetchedAt()))
			.setLatitude(w.latitude())
			.setLongitude(w.longitude())
			.setCurrent(CurrentWeather.newBuilder()
				.setTime(timestamp(w.current().time()))
				.setTemperature(w.current().temperature())
				.setRelativeHumidity(w.current().relativeHumidity())
				.setPrecipitation(w.current().precipitation())
				.setWeatherCode(w.current().weatherCode()))
			.setMaxRainProbability12H(w.maxRainProbability12h())
			.setTotalPrecipitation12H(w.totalPrecipitation12h());
		w.hourly()
			.forEach(h -> b.addHourly(HourlyForecast.newBuilder()
				.setTime(timestamp(h.time()))
				.setTemperature(h.temperature())
				.setRelativeHumidity(h.relativeHumidity())
				.setPrecipitationProbability(h.precipitationProbability())
				.setPrecipitation(h.precipitation())));
		return b.build();
	}

}
