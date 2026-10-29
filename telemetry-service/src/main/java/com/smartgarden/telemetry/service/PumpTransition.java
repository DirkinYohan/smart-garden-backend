package com.smartgarden.telemetry.service;

/**
 * Change of the pump state between two consecutive readings.
 */
public enum PumpTransition {

	STARTED, STOPPED, NONE;

	/**
	 * @param previousPumpOn pump state of the previous reading, or {@code null} if unknown (first reading after
	 * startup with no open event)
	 */
	public static PumpTransition detect(Boolean previousPumpOn, boolean currentPumpOn) {
		boolean wasOn = Boolean.TRUE.equals(previousPumpOn);
		if (currentPumpOn && !wasOn) {
			return STARTED;
		}
		if (!currentPumpOn && wasOn) {
			return STOPPED;
		}
		return NONE;
	}

}
