package com.smartgarden.irrigation.client;

import com.smartgarden.contracts.common.v1.PumpAction;
import com.smartgarden.contracts.telemetry.v1.ConfigCommand;
import com.smartgarden.contracts.telemetry.v1.PumpCommand;
import com.smartgarden.contracts.telemetry.v1.SendDeviceCommandRequest;
import com.smartgarden.contracts.telemetry.v1.SendDeviceCommandResponse;
import com.smartgarden.contracts.telemetry.v1.TelemetryServiceGrpc.TelemetryServiceBlockingStub;
import com.smartgarden.irrigation.config.IrrigationProperties;
import com.smartgarden.irrigation.domain.ConfigView;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * gRPC client of telemetry-service, used to deliver commands to the ESP32.
 * @throws io.grpc.StatusRuntimeException on any failure (e.g. UNAVAILABLE)
 */
@Component
public class TelemetryClient {

	private final TelemetryServiceBlockingStub stub;

	private final IrrigationProperties props;

	public TelemetryClient(TelemetryServiceBlockingStub stub, IrrigationProperties props) {
		this.stub = stub;
		this.props = props;
	}

	public SendDeviceCommandResponse sendConfig(ConfigView config) {
		return send(SendDeviceCommandRequest.newBuilder()
			.setConfig(ConfigCommand.newBuilder()
				.setMode(config.mode().toProto())
				.setMinMoisture(config.minMoisture())
				.setMaxMoisture(config.maxMoisture())
				.setRainPause(config.rainPause()))
			.build());
	}

	/** Igual que {@link #sendPump(PumpAction, int)} pero con clave de idempotencia. */
	public SendDeviceCommandResponse sendPump(PumpAction action, int durationSec, String requestId) {
		return send(SendDeviceCommandRequest.newBuilder()
			.setPump(PumpCommand.newBuilder().setAction(action).setDurationSec(durationSec))
			.setRequestId(requestId == null ? "" : requestId)
			.build());
	}

	public SendDeviceCommandResponse sendPump(PumpAction action, int durationSec) {
		return send(SendDeviceCommandRequest.newBuilder()
			.setPump(PumpCommand.newBuilder().setAction(action).setDurationSec(durationSec))
			.build());
	}

	private SendDeviceCommandResponse send(SendDeviceCommandRequest request) {
		return stub.withDeadlineAfter(props.telemetryDeadline().toMillis(), TimeUnit.MILLISECONDS)
			.sendDeviceCommand(request);
	}

}
