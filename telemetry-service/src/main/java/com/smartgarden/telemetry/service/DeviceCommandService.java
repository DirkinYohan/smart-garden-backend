package com.smartgarden.telemetry.service;

import com.smartgarden.contracts.common.v1.PumpAction;
import com.smartgarden.telemetry.domain.DeviceMode;
import com.smartgarden.telemetry.mqtt.DeviceCommandFactory;
import com.smartgarden.telemetry.mqtt.DeviceCommandFactory.DeviceCommand;
import com.smartgarden.telemetry.mqtt.MqttCommandPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Publica comandos al ESP32. Es <b>idempotente</b> por `request_id`: si llega dos veces el mismo identificador dentro de la
 * ventana de deduplicación (reintento de red, doble clic), el segundo no vuelve a encender la bomba y devuelve lo que ya
 * se publicó. Sin identificador se publica siempre (comportamiento clásico).
 */
@Service
public class DeviceCommandService {

	/** Tiempo durante el que se recuerda un comando ya publicado. */
	static final Duration DEDUPE_WINDOW = Duration.ofMinutes(10);

	/** Tope de entradas para que el mapa no crezca sin control. */
	private static final int MAX_ENTRIES = 500;

	private final DeviceCommandFactory factory;

	private final MqttCommandPublisher publisher;

	private final Map<String, SentCommand> recent = new ConcurrentHashMap<>();

	private final Map<String, Instant> sentAt = new ConcurrentHashMap<>();

	@Autowired
	public DeviceCommandService(DeviceCommandFactory factory, MqttCommandPublisher publisher) {
		this.factory = factory;
		this.publisher = publisher;
	}

	public SentCommand sendPump(PumpAction action, int durationSec, String requestId) {
		return send(requestId, factory.pump(action, durationSec));
	}

	public SentCommand sendPump(PumpAction action, int durationSec) {
		return sendPump(action, durationSec, null);
	}

	public SentCommand sendConfig(DeviceMode mode, int minMoisture, int maxMoisture, boolean rainPause,
			String requestId) {
		return send(requestId, factory.config(mode, minMoisture, maxMoisture, rainPause));
	}

	public SentCommand sendConfig(DeviceMode mode, int minMoisture, int maxMoisture, boolean rainPause) {
		return sendConfig(mode, minMoisture, maxMoisture, rainPause, null);
	}

	private SentCommand send(String requestId, DeviceCommand command) {
		if (requestId == null || requestId.isBlank()) {
			return publish(command);
		}
		prune();
		SentCommand already = recent.get(requestId);
		if (already != null) {
			return already;
		}
		SentCommand sent = publish(command);
		recent.put(requestId, sent);
		sentAt.put(requestId, Instant.now());
		return sent;
	}

	private SentCommand publish(DeviceCommand command) {
		String topic = publisher.publish(command);
		return new SentCommand(topic, command.payload(), command.retained());
	}

	/** Olvida las claves caducadas y, si aún así hay demasiadas, vacía el mapa. */
	private void prune() {
		Instant limit = Instant.now().minus(DEDUPE_WINDOW);
		sentAt.entrySet().removeIf((e) -> {
			if (e.getValue().isBefore(limit)) {
				recent.remove(e.getKey());
				return true;
			}
			return false;
		});
		if (recent.size() > MAX_ENTRIES) {
			recent.clear();
			sentAt.clear();
		}
	}

	public record SentCommand(String topic, String payload, boolean retained) {
	}

}
