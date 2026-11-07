package com.smartgarden.telemetry.mqtt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.smartgarden.telemetry.mqtt.DeviceCommandFactory.DeviceCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetainedConfigRefresherTest {

	private static final DeviceCommand CONFIG = new DeviceCommand("{\"type\":\"CONFIG\"}", true);

	private static final DeviceCommand PUMP = new DeviceCommand("{\"type\":\"PUMP\"}", false);

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-01T10:00:00Z"));

	private MqttCommandPublisher publisher;

	private RetainedConfigRefresher refresher;

	@BeforeEach
	void setUp() {
		Clock clock = new Clock() {
			@Override
			public ZoneOffset getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(java.time.ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return now.get();
			}
		};
		refresher = new RetainedConfigRefresher(clock, Duration.ofHours(12));
		publisher = mock(MqttCommandPublisher.class);
		refresher.attach(publisher);
	}

	@Test
	void nothingToRefreshBeforeTheFirstConfig() {
		refresher.refreshPeriodically();
		refresher.onDeviceOnline();

		verify(publisher, never()).publish(any());
	}

	@Test
	void periodicRefreshRepublishesTheLastRetainedConfig() {
		refresher.remember(CONFIG);
		refresher.remember(PUMP);

		refresher.refreshPeriodically();

		verify(publisher, times(1)).publish(CONFIG);
		verify(publisher, never()).publish(PUMP);
	}

	@Test
	void deviceOnlineRepublishesOnlyWhenTheConfigIsOld() {
		refresher.remember(CONFIG);

		now.set(now.get().plus(Duration.ofHours(1)));
		refresher.onDeviceOnline();
		verify(publisher, never()).publish(any());

		now.set(now.get().plus(Duration.ofHours(12)));
		refresher.onDeviceOnline();
		verify(publisher, times(1)).publish(CONFIG);
	}

}
