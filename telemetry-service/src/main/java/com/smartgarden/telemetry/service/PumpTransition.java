package com.smartgarden.telemetry.service;

public enum PumpTransition {

	STARTED, STOPPED, NONE;

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
