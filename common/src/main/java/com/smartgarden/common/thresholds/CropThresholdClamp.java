package com.smartgarden.common.thresholds;

/**
 * Acota los umbrales recomendados por la IA al perfil del cultivo: como máximo ±10 puntos del rango del perfil,
 * dentro de 10–90 y con {@code max - min >= 10}. Así las recomendaciones cambian según el cultivo y nunca se salen de
 * lo razonable para él.
 *
 * <p>
 * Vive en el módulo compartido para que ai-service e irrigation-service apliquen <b>exactamente</b> la misma regla: es
 * una regla de dominio pura (sin estado, sin dependencias), no un modelo de datos compartido.
 */
public final class CropThresholdClamp {

	/** Puntos que la recomendación puede separarse del rango del perfil del cultivo. */
	public static final int CROP_MARGIN = 10;

	public static final int MIN_THRESHOLD = 10;

	public static final int MAX_THRESHOLD = 90;

	public static final int MIN_GAP = 10;

	private CropThresholdClamp() {
	}

	/**
	 * @param profileMin límite inferior del perfil del cultivo (0 o inválido = sin perfil)
	 * @param profileMax límite superior del perfil del cultivo
	 * @return {@code [min, max]} acotados
	 */
	public static int[] clamp(int min, int max, int profileMin, int profileMax) {
		int lo = Math.clamp(Math.min(min, max), MIN_THRESHOLD, MAX_THRESHOLD);
		int hi = Math.clamp(Math.max(min, max), MIN_THRESHOLD, MAX_THRESHOLD);
		boolean hasProfile = profileMin > 0 && profileMax > profileMin;
		int floor = hasProfile ? Math.clamp(profileMin - CROP_MARGIN, MIN_THRESHOLD, MAX_THRESHOLD) : MIN_THRESHOLD;
		int ceil = hasProfile ? Math.clamp(profileMax + CROP_MARGIN, MIN_THRESHOLD, MAX_THRESHOLD) : MAX_THRESHOLD;
		if (ceil - floor < MIN_GAP) {
			// Perfil absurdamente estrecho: se mantiene el hueco mínimo dentro de 10–90.
			ceil = Math.min(MAX_THRESHOLD, floor + MIN_GAP);
			floor = Math.max(MIN_THRESHOLD, ceil - MIN_GAP);
		}
		int newMin = Math.clamp(lo, floor, ceil);
		int newMax = Math.clamp(hi, floor, ceil);
		if (newMax - newMin < MIN_GAP) {
			if (newMin + MIN_GAP <= ceil) {
				newMax = newMin + MIN_GAP;
			}
			else {
				newMax = ceil;
				newMin = ceil - MIN_GAP;
			}
		}
		return new int[] { newMin, newMax };
	}

}
