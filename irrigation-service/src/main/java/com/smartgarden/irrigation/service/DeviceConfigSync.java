package com.smartgarden.irrigation.service;

import com.smartgarden.irrigation.client.TelemetryClient;
import com.smartgarden.irrigation.domain.ConfigView;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pushes the configuration to the ESP32 as a retained CONFIG message (via telemetry-service). If telemetry-service
 * or the broker is down, the latest configuration is kept and retried periodically; the change itself is never lost
 * because the database is the source of truth.
 */
@Component
public class DeviceConfigSync {

	private static final Logger log = LoggerFactory.getLogger(DeviceConfigSync.class);

	private final TelemetryClient telemetry;

	private final AtomicReference<ConfigView> pending = new AtomicReference<>();

	public DeviceConfigSync(TelemetryClient telemetry) {
		this.telemetry = telemetry;
	}

	public void publish(ConfigView config) {
		pending.set(config);
		tryPublish();
	}

	@Scheduled(fixedDelayString = "15s", initialDelayString = "15s")
	public void retryPending() {
		if (pending.get() != null) {
			tryPublish();
		}
	}

	public boolean hasPending() {
		return pending.get() != null;
	}

	private synchronized void tryPublish() {
		ConfigView config = pending.get();
		if (config == null) {
			return;
		}
		try {
			telemetry.sendConfig(config);
			pending.compareAndSet(config, null);
			log.info("CONFIG sent to device: mode={} min={} max={} rainPause={}", config.mode(),
					config.minMoisture(), config.maxMoisture(), config.rainPause());
		}
		catch (StatusRuntimeException ex) {
			log.warn("Could not send CONFIG to device ({}); will retry", ex.getStatus().getCode());
		}
	}

}
