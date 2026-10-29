package com.smartgarden.telemetry.service;

import com.smartgarden.telemetry.config.TelemetryProperties;
import com.smartgarden.telemetry.domain.TelemetrySnapshot;
import com.smartgarden.telemetry.entity.TelemetryReadingEntity;
import com.smartgarden.telemetry.repository.TelemetryReadingRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Entry point for every valid reading: throttled persistence, irrigation-event derivation, device status and
 * real-time fan-out.
 */
@Service
public class TelemetryIngestionService {

	private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionService.class);

	private final TelemetryReadingRepository repository;

	private final IrrigationEventService irrigationEvents;

	private final DeviceStatusTracker statusTracker;

	private final TelemetryProperties props;

	private final Subscribers<TelemetrySnapshot> telemetrySubscribers = new Subscribers<>();

	private volatile TelemetrySnapshot latest;

	private volatile TelemetrySnapshot latestValid;

	private Instant lastPersistedAt;

	public TelemetryIngestionService(TelemetryReadingRepository repository, IrrigationEventService irrigationEvents,
			DeviceStatusTracker statusTracker, TelemetryProperties props) {
		this.repository = repository;
		this.irrigationEvents = irrigationEvents;
		this.statusTracker = statusTracker;
		this.props = props;
	}

	public synchronized void ingest(TelemetrySnapshot reading) {
		TelemetrySnapshot previous = latest;
		Boolean previousPumpOn = previous == null ? null : previous.pumpOn();
		latest = reading;
		if (reading.sensorOk()) {
			latestValid = reading;
		}
		else {
			log.debug("Invalid reading (sensorOk=false, raw={}): excluded from aggregates", reading.raw());
		}

		if (PersistencePolicy.shouldPersist(lastPersistedAt, previousPumpOn, reading.receivedAt(), reading.pumpOn(),
				props.persistInterval())) {
			repository.save(TelemetryReadingEntity.from(reading));
			lastPersistedAt = reading.receivedAt();
			log.debug("Persisted reading moisture={}% pumpOn={}", reading.moisture(), reading.pumpOn());
		}
		irrigationEvents.onReading(reading);
		statusTracker.onReading(reading);
		telemetrySubscribers.publish(reading);
	}

	/** Latest reading in memory, falling back to the database after a restart. */
	public Optional<TelemetrySnapshot> latest() {
		TelemetrySnapshot current = latest;
		if (current != null) {
			return Optional.of(current);
		}
		return repository.findTopByOrderByReceivedAtDesc().map(TelemetryReadingEntity::toSnapshot);
	}

	/** Latest reading with sensorOk = true (in memory, falling back to the database). */
	public Optional<TelemetrySnapshot> latestValid() {
		TelemetrySnapshot current = latestValid;
		if (current != null) {
			return Optional.of(current);
		}
		return repository.findTopBySensorOkTrueOrderByReceivedAtDesc().map(TelemetryReadingEntity::toSnapshot);
	}

	public Subscribers<TelemetrySnapshot> subscribers() {
		return telemetrySubscribers;
	}

}
