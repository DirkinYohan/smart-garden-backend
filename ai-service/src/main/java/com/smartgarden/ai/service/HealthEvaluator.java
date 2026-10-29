package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.HealthStatus;
import com.smartgarden.ai.domain.MoistureLevel;
import com.smartgarden.ai.domain.NextAction;

/**
 * Deterministic garden state and next action (Gemini never decides these). The state comes from the
 * {@link MoistureLevel} calculated by {@link MoistureClassifier}, so the message, the traffic light and the action can
 * never disagree.
 *
 * <table>
 * <tr><th>Nivel</th><th>healthStatus</th><th>nextAction</th></tr>
 * <tr><td>SENSOR_ERROR</td><td>CRITICAL</td><td>CHECK_SENSOR</td></tr>
 * <tr><td>VERY_DRY</td><td>CRITICAL</td><td>PAUSED_FOR_RAIN si la pausa aplica y humedad ≥ 15 %, si no
 * IRRIGATE_NOW</td></tr>
 * <tr><td>DRY</td><td>ATTENTION</td><td>PAUSED_FOR_RAIN si la pausa aplica y humedad ≥ 15 %, si no
 * IRRIGATE_NOW</td></tr>
 * <tr><td>OPTIMAL</td><td>GOOD</td><td>PAUSED_FOR_RAIN si la pausa aplica, si no WAIT</td></tr>
 * <tr><td>WET</td><td>ATTENTION</td><td>WAIT (no regar)</td></tr>
 * <tr><td>SATURATED</td><td>CRITICAL</td><td>WAIT (no regar)</td></tr>
 * </table>
 * "Rain pause applies" = rain is expected (≥ threshold probability and some precipitation in 12 h) and the pause is
 * active (config rainPause, or a recommendation to pause). Below 15 % the ESP32 waters anyway.
 */
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
		// Below 15 % the ESP32 irrigates even when the rain pause is on.
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
