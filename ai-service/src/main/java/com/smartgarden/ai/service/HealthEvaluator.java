package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.HealthStatus;
import com.smartgarden.ai.domain.MoistureLevel;
import com.smartgarden.ai.domain.NextAction;

public final class HealthEvaluator {

	static final int CRITICAL_MOISTURE = 15;

	private HealthEvaluator() {
	}

	public static Result evaluate(AnalysisInputData in, boolean rainPauseApplies) {
		return evaluate(MoistureClassifier.classify(in), in, rainPauseApplies);
	}

	public static Result evaluate(MoistureLevel level, AnalysisInputData in, boolean rainPauseApplies) {
		if (level == MoistureLevel.SENSOR_ERROR) {
			return new Result(HealthStatus.CRITICAL, NextAction.CHECK_SENSOR);
		}
		Integer m = in.validMoisture();
		if (m == null) {
			return new Result(HealthStatus.CRITICAL, NextAction.CHECK_SENSOR);
		}

		boolean canPause = rainPauseApplies && m >= CRITICAL_MOISTURE;
		return switch (level) {
			case VERY_DRY -> new Result(HealthStatus.CRITICAL,
					canPause ? NextAction.PAUSED_FOR_RAIN : NextAction.IRRIGATE_NOW);
			case DRY -> new Result(HealthStatus.ATTENTION,
					canPause ? NextAction.PAUSED_FOR_RAIN : NextAction.IRRIGATE_NOW);
			case OPTIMAL -> new Result(HealthStatus.GOOD,
					rainPauseApplies ? NextAction.PAUSED_FOR_RAIN : NextAction.WAIT);
			case WET -> new Result(HealthStatus.ATTENTION, NextAction.WAIT);
			case SATURATED -> new Result(HealthStatus.CRITICAL, NextAction.WAIT);
			case SENSOR_ERROR -> new Result(HealthStatus.CRITICAL, NextAction.CHECK_SENSOR);
		};
	}

	public record Result(HealthStatus healthStatus, NextAction nextAction) {
	}

}
