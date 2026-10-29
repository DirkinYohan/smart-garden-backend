package com.smartgarden.gateway.stream;

import com.smartgarden.contracts.ai.v1.AiServiceGrpc.AiServiceStub;
import com.smartgarden.contracts.ai.v1.Analysis;
import com.smartgarden.contracts.ai.v1.StreamAnalysesRequest;
import com.smartgarden.contracts.irrigation.v1.IrrigationConfig;
import com.smartgarden.contracts.irrigation.v1.IrrigationServiceGrpc.IrrigationServiceStub;
import com.smartgarden.contracts.irrigation.v1.StreamConfigChangesRequest;
import com.smartgarden.contracts.telemetry.v1.DeviceStatusEvent;
import com.smartgarden.contracts.telemetry.v1.StreamDeviceStatusRequest;
import com.smartgarden.contracts.telemetry.v1.StreamTelemetryRequest;
import com.smartgarden.contracts.telemetry.v1.TelemetryReading;
import com.smartgarden.contracts.telemetry.v1.TelemetryServiceGrpc.TelemetryServiceStub;
import com.smartgarden.gateway.config.GrpcGatewayProperties;
import com.smartgarden.gateway.grpc.DtoMapper;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Relays the gRPC server streams to STOMP topics:
 * <ul>
 * <li>StreamTelemetry → /topic/telemetry</li>
 * <li>StreamDeviceStatus → /topic/device-status</li>
 * <li>StreamConfigChanges → /topic/config</li>
 * <li>StreamAnalyses → /topic/analysis</li>
 * </ul>
 * Calls use {@code withWaitForReady()} so they wait for a restarting service instead of failing immediately.
 */
@Component
public class StreamRelay {

	private final TelemetryServiceStub telemetry;

	private final IrrigationServiceStub irrigation;

	private final AiServiceStub ai;

	private final SimpMessagingTemplate broker;

	private final GrpcGatewayProperties props;

	private final ScheduledExecutorService scheduler = Executors
		.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("grpc-stream-reconnect").daemon().factory());

	private final List<ResilientStream<?>> streams = new ArrayList<>();

	public StreamRelay(TelemetryServiceStub telemetry, IrrigationServiceStub irrigation, AiServiceStub ai,
			SimpMessagingTemplate broker, GrpcGatewayProperties props) {
		this.telemetry = telemetry;
		this.irrigation = irrigation;
		this.ai = ai;
		this.broker = broker;
		this.props = props;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		if (!props.streamsEnabled()) {
			return;
		}
		streams.add(new ResilientStream<TelemetryReading>("telemetry",
				(ctx, obs) -> ctx.run(() -> telemetry.withWaitForReady()
					.streamTelemetry(StreamTelemetryRequest.getDefaultInstance(), obs)),
				r -> broker.convertAndSend("/topic/telemetry", DtoMapper.reading(r)), scheduler,
				props.streamInitialBackoff(), props.streamMaxBackoff()));
		streams.add(new ResilientStream<DeviceStatusEvent>("device-status",
				(ctx, obs) -> ctx.run(() -> telemetry.withWaitForReady()
					.streamDeviceStatus(StreamDeviceStatusRequest.getDefaultInstance(), obs)),
				e -> broker.convertAndSend("/topic/device-status", DtoMapper.deviceStatus(e)), scheduler,
				props.streamInitialBackoff(), props.streamMaxBackoff()));
		streams.add(new ResilientStream<IrrigationConfig>("config",
				(ctx, obs) -> ctx.run(() -> irrigation.withWaitForReady()
					.streamConfigChanges(StreamConfigChangesRequest.getDefaultInstance(), obs)),
				c -> broker.convertAndSend("/topic/config", DtoMapper.config(c)), scheduler,
				props.streamInitialBackoff(), props.streamMaxBackoff()));
		streams.add(new ResilientStream<Analysis>("analysis",
				(ctx, obs) -> ctx.run(
						() -> ai.withWaitForReady().streamAnalyses(StreamAnalysesRequest.getDefaultInstance(), obs)),
				a -> broker.convertAndSend("/topic/analysis", DtoMapper.analysis(a)), scheduler,
				props.streamInitialBackoff(), props.streamMaxBackoff()));
		streams.forEach(ResilientStream::start);
	}

	@PreDestroy
	public void stop() {
		streams.forEach(ResilientStream::stop);
		scheduler.shutdownNow();
	}

}
