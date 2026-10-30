package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.MoistureLevel;

public final class MoistureClassifier {

	public static final int VERY_DRY_MARGIN = 10;

	public static final int SATURATED_MARGIN = 15;

	public static final int SATURATED_FLOOR = 95;

	private MoistureClassifier() {
	}

	public static MoistureLevel classify(AnalysisInputData in) {
		if (in.isSensorFault() || !in.hasReading()) {
			return MoistureLevel.SENSOR_ERROR;
		}
		return classify(in.validMoisture(), in.minThreshold(), in.maxThreshold());
	}

	public static MoistureLevel classify(Integer moisture, int min, int max) {
		if (moisture == null) {
			return MoistureLevel.SENSOR_ERROR;
		}
		int lo = Math.min(min, max);
		int hi = Math.max(min, max);

		if (hi <= 0) {
			lo = AnalysisInputData.DEFAULT_MIN;
			hi = AnalysisInputData.DEFAULT_MAX;
		}
		if (moisture >= SATURATED_FLOOR || moisture > hi + SATURATED_MARGIN) {
			return MoistureLevel.SATURATED;
		}
		if (moisture > hi) {
			return MoistureLevel.WET;
		}
		if (moisture >= lo) {
			return MoistureLevel.OPTIMAL;
		}
		if (moisture >= lo - VERY_DRY_MARGIN) {
			return MoistureLevel.DRY;
		}
		return MoistureLevel.VERY_DRY;
	}

}
