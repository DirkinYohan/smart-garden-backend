package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.ActionType;
import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.Priority;
import com.smartgarden.ai.domain.RecommendationCategory;
import com.smartgarden.ai.domain.RecommendationDetail;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which one-click actions make sense for the current configuration, and removes any other action from the
 * recommendations (whoever wrote them).
 * <ul>
 * <li>APPLY_THRESHOLDS: mode is not AI and the recommended thresholds differ from the current ones.</li>
 * <li>ENABLE_AI_MODE: mode is not AI.</li>
 * <li>ENABLE_RAIN_PAUSE: mode is not AI, the pause is off and rain is expected.</li>
 * <li>DISABLE_RAIN_PAUSE: mode is not AI, the pause is on and no rain is expected (max probability &lt; 40 % and
 * &lt; 0.5 mm).</li>
 * <li>CHECK_SENSOR: the sensor is failing or there is no valid recent reading (also forced into the list).</li>
 * </ul>
 * In mode AI the IA itself sets thresholds and rain pause, so only CHECK_SENSOR is offered.
 */
public final class ActionAdvisor {

	static final double NO_RAIN_PROBABILITY = 40;

	static final double NO_RAIN_MM = 0.5;

	private ActionAdvisor() {
	}

	/** Actions Gemini may choose from (APPLY_THRESHOLDS is re-checked once thresholds are known). */
	public static Set<ActionType> candidates(AnalysisInputData in, double rainThreshold) {
		Set<ActionType> set = EnumSet.noneOf(ActionType.class);
		if (needsSensorCheck(in)) {
			set.add(ActionType.CHECK_SENSOR);
		}
		if (!in.configAvailable() || "AI".equals(in.mode())) {
			return set;
		}
		set.add(ActionType.APPLY_THRESHOLDS);
		set.add(ActionType.ENABLE_AI_MODE);
		boolean rainExpected = in.rainExpected(rainThreshold);
		if (!in.isRainPause() && rainExpected) {
			set.add(ActionType.ENABLE_RAIN_PAUSE);
		}
		if (in.isRainPause() && in.weatherAvailable() && in.rainProbabilityMax12h() < NO_RAIN_PROBABILITY
				&& in.precipitationSum12h() < NO_RAIN_MM) {
			set.add(ActionType.DISABLE_RAIN_PAUSE);
		}
		return set;
	}

	public static Set<ActionType> eligible(AnalysisInputData in, double rainThreshold, int recommendedMin,
			int recommendedMax) {
		Set<ActionType> set = candidates(in, rainThreshold);
		if (recommendedMin == in.currentMin() && recommendedMax == in.currentMax()) {
			set.remove(ActionType.APPLY_THRESHOLDS);
		}
		return set;
	}

	/**
	 * Keeps only eligible actions (with the canonical Spanish label), and makes sure a sensor problem always comes
	 * first with its CHECK_SENSOR action. At most 4 recommendations.
	 */
	public static List<RecommendationDetail> attach(List<RecommendationDetail> details, Set<ActionType> eligible) {
		List<RecommendationDetail> result = new ArrayList<>();
		Set<ActionType> used = EnumSet.noneOf(ActionType.class);
		for (RecommendationDetail d : details) {
			ActionType type = d.action() == null ? null : d.action().type();
			if (type != null && eligible.contains(type) && used.add(type)) {
				result.add(d.withAction(type.toAction()));
			}
			else {
				result.add(d.withAction(null));
			}
		}
		if (eligible.contains(ActionType.CHECK_SENSOR) && !used.contains(ActionType.CHECK_SENSOR)) {
			result.addFirst(new RecommendationDetail("Revisa el sensor",
					"No hay lecturas válidas recientes del sensor de humedad. Verifica que esté enterrado, conectado y que el ESP32 tenga Wi-Fi.",
					RecommendationCategory.SENSOR, Priority.HIGH, ActionType.CHECK_SENSOR.toAction()));
		}
		return List.copyOf(result.subList(0, Math.min(result.size(), RecommendationValidator.MAX_RECOMMENDATIONS)));
	}

	static boolean needsSensorCheck(AnalysisInputData in) {
		return in.isSensorFault() || !in.hasReading();
	}

}
