package com.smartgarden.ai.domain;

/**
 * Nivel de humedad del suelo respecto al rango del cultivo activo [min, max]. Se calcula en Java (nunca lo decide
 * Gemini) y es la única fuente de verdad para los mensajes, la salud, el riesgo y la próxima acción.
 *
 * <table>
 * <tr><th>Nivel</th><th>Condición</th><th>healthStatus</th><th>Acción</th></tr>
 * <tr><td>SENSOR_ERROR</td><td>sensorOk = false o sin lectura válida en 5 min</td><td>CRITICAL</td>
 * <td>CHECK_SENSOR</td></tr>
 * <tr><td>VERY_DRY</td><td>humedad &lt; min - 10</td><td>CRITICAL</td><td>IRRIGATE_NOW</td></tr>
 * <tr><td>DRY</td><td>min - 10 &lt;= humedad &lt; min</td><td>ATTENTION</td><td>IRRIGATE_NOW o
 * PAUSED_FOR_RAIN</td></tr>
 * <tr><td>OPTIMAL</td><td>min &lt;= humedad &lt;= max</td><td>GOOD</td><td>WAIT</td></tr>
 * <tr><td>WET</td><td>max &lt; humedad &lt;= max + 15</td><td>ATTENTION</td><td>WAIT (no regar)</td></tr>
 * <tr><td>SATURATED</td><td>humedad &gt; max + 15 o humedad &gt;= 95</td><td>CRITICAL</td><td>WAIT (no regar)</td>
 * </tr>
 * </table>
 */
public enum MoistureLevel {

	/** El sensor falla o no hay lecturas válidas recientes: no se puede afirmar nada sobre la humedad. */
	SENSOR_ERROR("error de sensor"),
	/** Muy seca: más de 10 puntos por debajo del mínimo del cultivo. */
	VERY_DRY("muy seca"),
	/** Baja: por debajo del mínimo del cultivo. */
	DRY("baja"),
	/** Ideal: dentro del rango del cultivo. */
	OPTIMAL("ideal"),
	/** Alta: por encima del máximo del cultivo (hasta 15 puntos). */
	WET("alta"),
	/** Saturada: muy por encima del máximo o por encima del 95 %. */
	SATURATED("saturada");

	private final String label;

	MoistureLevel(String label) {
		this.label = label;
	}

	/** Etiqueta corta en español para los mensajes y la UI. */
	public String label() {
		return label;
	}

	public boolean isDry() {
		return this == VERY_DRY || this == DRY;
	}

	public boolean isWet() {
		return this == WET || this == SATURATED;
	}

	/** No tiene sentido afirmar "humedad baja" ni "saturada" cuando el sensor no da datos. */
	public boolean isKnown() {
		return this != SENSOR_ERROR;
	}

	/** Riesgo hídrico derivado del nivel (determinista, nunca lo decide Gemini). */
	public RiskLevel risk() {
		return switch (this) {
			case VERY_DRY, SATURATED, SENSOR_ERROR -> RiskLevel.HIGH;
			case DRY, WET -> RiskLevel.MEDIUM;
			case OPTIMAL -> RiskLevel.LOW;
		};
	}

}
