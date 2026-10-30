package com.smartgarden.common.thresholds;

public final class CropThresholdClamp {

	public static final int CROP_MARGIN = 10;

	public static final int MIN_THRESHOLD = 10;

	public static final int MAX_THRESHOLD = 90;

	public static final int MIN_GAP = 10;

	private CropThresholdClamp() {
	}

	public static int[] clamp(int min, int max, int profileMin, int profileMax) {
		int lo = Math.clamp(Math.min(min, max), MIN_THRESHOLD, MAX_THRESHOLD);
		int hi = Math.clamp(Math.max(min, max), MIN_THRESHOLD, MAX_THRESHOLD);
		boolean hasProfile = profileMin > 0 && profileMax > profileMin;
		int floor = hasProfile ? Math.clamp(profileMin - CROP_MARGIN, MIN_THRESHOLD, MAX_THRESHOLD) : MIN_THRESHOLD;
		int ceil = hasProfile ? Math.clamp(profileMax + CROP_MARGIN, MIN_THRESHOLD, MAX_THRESHOLD) : MAX_THRESHOLD;
		if (ceil - floor < MIN_GAP) {

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
