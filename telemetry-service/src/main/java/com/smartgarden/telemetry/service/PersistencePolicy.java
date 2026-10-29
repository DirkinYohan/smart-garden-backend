package com.smartgarden.telemetry.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides whether a reading is stored: at most once per interval, and always when pumpOn changes.
 */
public final class PersistencePolicy {

	private PersistencePolicy() {
	}

	public static boolean shouldPersist(Instant lastPersistedAt, Boolean previousPumpOn, Instant receivedAt,
			boolean pumpOn, Duration interval) {
		if (lastPersistedAt == null || previousPumpOn == null) {
			return true;
		}
		if (previousPumpOn != pumpOn) {
			return true;
		}
		return !receivedAt.isBefore(lastPersistedAt.plus(interval));
	}

}
