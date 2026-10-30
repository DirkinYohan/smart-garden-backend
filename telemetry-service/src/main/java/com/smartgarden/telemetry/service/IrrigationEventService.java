package com.smartgarden.telemetry.service;

import com.smartgarden.telemetry.domain.TelemetrySnapshot;
import com.smartgarden.telemetry.entity.IrrigationEventEntity;
import com.smartgarden.telemetry.repository.IrrigationEventRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IrrigationEventService {

	private static final Logger log = LoggerFactory.getLogger(IrrigationEventService.class);

	private final IrrigationEventRepository repository;

	private final Map<String, Boolean> lastPumpState = new ConcurrentHashMap<>();

	public IrrigationEventService(IrrigationEventRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public PumpTransition onReading(TelemetrySnapshot reading) {
		Boolean previous = lastPumpState.computeIfAbsent(reading.deviceId(), this::initialState);
		PumpTransition transition = PumpTransition.detect(previous, reading.pumpOn());
		switch (transition) {
			case STARTED -> {

				repository.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(reading.deviceId())
					.ifPresent(open -> open.close(reading.receivedAt(), reading.moisture()));
				IrrigationEventEntity event = repository.save(IrrigationEventEntity.open(reading.deviceId(),
						reading.receivedAt(), reading.moisture(), reading.mode()));
				log.info("Irrigation started (event {}) moisture={}% mode={}", event.getId(), reading.moisture(),
						reading.mode());
			}
			case STOPPED -> repository.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(reading.deviceId())
				.ifPresent(open -> {
					open.close(reading.receivedAt(), reading.moisture());
					log.info("Irrigation finished (event {}) after {}s, moisture {}% -> {}%", open.getId(),
							open.getDurationSec(), open.getStartMoisture(), reading.moisture());
				});
			case NONE -> {
			}
		}
		lastPumpState.put(reading.deviceId(), reading.pumpOn());
		return transition;
	}

	private Boolean initialState(String deviceId) {
		return repository.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(deviceId).isPresent() ? true
				: null;
	}

	@Transactional(readOnly = true)
	public List<IrrigationEventEntity> latest(int limit) {
		return repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit));
	}

}
