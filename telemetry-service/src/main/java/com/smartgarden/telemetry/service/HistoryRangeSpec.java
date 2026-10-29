package com.smartgarden.telemetry.service;

import com.smartgarden.contracts.telemetry.v1.HistoryRange;
import java.time.Duration;

public enum HistoryRangeSpec {

	LAST_24H(Duration.ofHours(24), Duration.ofMinutes(5)),
	LAST_7D(Duration.ofDays(7), Duration.ofHours(1)),
	LAST_30D(Duration.ofDays(30), Duration.ofHours(6));

	private final Duration window;

	private final Duration interval;

	HistoryRangeSpec(Duration window, Duration interval) {
		this.window = window;
		this.interval = interval;
	}

	public Duration window() {
		return window;
	}

	public Duration interval() {
		return interval;
	}

	public static HistoryRangeSpec from(HistoryRange range) {
		return switch (range) {
			case HISTORY_RANGE_24H -> LAST_24H;
			case HISTORY_RANGE_7D -> LAST_7D;
			case HISTORY_RANGE_30D -> LAST_30D;
			default -> throw new IllegalArgumentException("range is required (24h, 7d or 30d)");
		};
	}

}
