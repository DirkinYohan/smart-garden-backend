package com.smartgarden.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-process periodic analysis (local and Docker). In Azure Container Apps the service scales to zero, so
 * {@code @Scheduled} cannot run; there it is disabled ({@code AI_SCHEDULER_ENABLED=false}) and a Container Apps
 * Job calls the {@code RunScheduledAnalysis} RPC instead.
 */
@Component
@ConditionalOnProperty(name = "smartgarden.ai.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisScheduler {

	private final AnalysisService analyses;

	public AnalysisScheduler(AnalysisService analyses) {
		this.analyses = analyses;
	}

	@Scheduled(fixedDelayString = "${smartgarden.ai.analysis-interval:30m}",
			initialDelayString = "${smartgarden.ai.analysis-initial-delay:2m}")
	public void tick() {
		analyses.runScheduled();
	}

}
