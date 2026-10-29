package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.MoistureLevel;

/**
 * Clasifica la humedad actual respecto al rango del cultivo activo. Se calcula en Java antes de llamar a Gemini para
 * que los mensajes no dependan de la interpretación libre del modelo.
 * <ul>
 * <li>VERY_DRY: humedad &lt; min - 10</li>
 * <li>DRY: min - 10 &lt;= humedad &lt; min</li>
 * <li>OPTIMAL: min &lt;= humedad &lt;= max</li>
 * <li>WET: max &lt; humedad &lt;= max + 15</li>
 * <li>SATURATED: humedad &gt; max + 15 o humedad &gt;= 95</li>
 * </ul>
 */
public final class MoistureClassifier {

	/** Puntos por debajo del mínimo que separan "seca" de "muy seca". */
	public static final int VERY_DRY_MARGIN = 10;

	/** Puntos por encima del máximo que separan "alta" de "saturada". */
	public static final int SATURATED_MARGIN = 15;

	/** A partir de este porcentaje la tierra se considera saturada aunque el rango del cultivo sea muy alto. */
	public static final int SATURATED_FLOOR = 95;

	private MoistureClassifier() {
	}

	/** Nivel de la lectura actual; {@link MoistureLevel#SENSOR_ERROR} cuando no hay dato válido y reciente. */
	public static MoistureLevel classify(AnalysisInputData in) {
		if (in.isSensorFault() || !in.hasReading()) {
			return MoistureLevel.SENSOR_ERROR;
		}
		return classify(in.validMoisture(), in.minThreshold(), in.maxThreshold());
	}

	/** Nivel para una humedad concreta dentro de un rango [min, max]. */
	public static MoistureLevel classify(Integer moisture, int min, int max) {
		if (moisture == null) {
			return MoistureLevel.SENSOR_ERROR;
		}
		int lo = Math.min(min, max);
		int hi = Math.max(min, max);
		// Un rango vacío (sin perfil ni umbrales) no debe clasificar todo como "saturado".
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
