package com.smartgarden.ai.service;

import com.smartgarden.ai.client.GardenDataClient;
import com.smartgarden.ai.domain.AnalysisSource;
import com.smartgarden.ai.domain.HealthStatus;
import com.smartgarden.ai.domain.RiskLevel;
import com.smartgarden.ai.entity.AnalysisEntity;
import com.smartgarden.ai.repository.AnalysisRepository;
import com.smartgarden.contracts.telemetry.v1.DailyStat;
import io.grpc.StatusRuntimeException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

	private static final Logger log = LoggerFactory.getLogger(StatsService.class);

	private final AnalysisRepository repository;

	private final GardenDataClient garden;

	private final Clock clock;

	public StatsService(AnalysisRepository repository, GardenDataClient garden, Clock clock) {
		this.repository = repository;
		this.garden = garden;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Stats stats(int days) {
		if (days != 7 && days != 30) {
			throw new IllegalArgumentException("days debe ser 7 o 30");
		}
		List<AnalysisEntity> analyses = repository
			.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(clock.instant().minus(Duration.ofDays(days)));
		Map<RiskLevel, Integer> risk = new EnumMap<>(RiskLevel.class);
		Map<HealthStatus, Integer> health = new EnumMap<>(HealthStatus.class);
		int gemini = 0;
		int rainPauses = 0;
		for (AnalysisEntity a : analyses) {
			if (a.getSource() == AnalysisSource.GEMINI) {
				gemini++;
			}
			if (a.isPauseForRain()) {
				rainPauses++;
			}
			risk.merge(a.getRiskLevel(), 1, Integer::sum);
			if (a.getHealthStatus() != null) {
				health.merge(a.getHealthStatus(), 1, Integer::sum);
			}
		}
		List<ThresholdPoint> thresholds = analyses.stream()
			.map(a -> new ThresholdPoint(a.getCreatedAt(), a.getRecommendedMin(), a.getRecommendedMax(),
					a.getSource(), a.getRiskLevel()))
			.toList();
		List<DailyStat> daily;
		try {
			daily = garden.dailyStats(days);
		}
		catch (StatusRuntimeException ex) {
			log.warn("Daily moisture unavailable from telemetry-service: {}", ex.getStatus().getCode());
			daily = List.of();
		}
		return new Stats(days, analyses.size(), gemini, analyses.size() - gemini, risk, health, rainPauses, thresholds,
				daily);
	}

	public record ThresholdPoint(Instant timestamp, int min, int max, AnalysisSource source, RiskLevel riskLevel) {
	}

	public record Stats(int days, int totalAnalyses, int geminiCount, int rulesCount, Map<RiskLevel, Integer> risk,
			Map<HealthStatus, Integer> health, int rainPauses, List<ThresholdPoint> thresholdHistory,
			List<DailyStat> dailyMoisture) {
	}

}
