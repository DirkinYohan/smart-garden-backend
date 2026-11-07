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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Relays the gRPC server streams to the STOMP topics.
 * <p>
 * Always-on mode (local / Docker): the streams open at startup. On-demand mode
 * ({@code smartgarden.grpc.streams-on-demand=true}, Azure Container Apps): they open when the first WebSocket
 * client connects and close {@code streams-idle-grace} after the last one leaves, so irrigation-service and
 * ai-service can scale to zero while nobody is looking at the web.
 */
@Component
public class StreamRelay {

	private static final Logger log = LoggerFactory.getLogger(StreamRelay.class);

	private final TelemetryServiceStub telemetry;

	private final IrrigationServiceStub irrigation;

	private final AiServiceStub ai;

	private final SimpMessagingTemplate broker;

	private final GrpcGatewayProperties props;

	private final ScheduledExecutorService scheduler = Executors
		.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("grpc-stream-reconnect").daemon().factory());

	private final Set<String> sessions = ConcurrentHashMap.newKeySet();

	private final List<ResilientStream<?>> streams = new ArrayList<>();

	private ScheduledFuture<?> pendingStop;

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
		if (props.streamsEnabled() && !props.streamsOnDemand()) {
			openStreams();
		}
	}

	@EventListener
	public void onClientConnected(SessionConnectedEvent event) {
		if (!onDemand()) {
			return;
		}
		String id = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
		if (id != null) {
			sessions.add(id);
		}
		synchronized (this) {
			cancelPendingStop();
			if (streams.isEmpty()) {
				log.info("First WebSocket client connected; opening the gRPC streams");
				openStreams();
			}
		}
	}

	@EventListener
	public void onClientDisconnected(SessionDisconnectEvent event) {
		if (!onDemand()) {
			return;
		}
		sessions.remove(event.getSessionId());
		synchronized (this) {
			if (sessions.isEmpty() && !streams.isEmpty() && pendingStop == null) {
				pendingStop = scheduler.schedule(this::closeIfIdle, props.streamsIdleGrace().toMillis(),
						TimeUnit.MILLISECONDS);
			}
		}
	}

	/** Number of open relay streams (0 while idle in on-demand mode). */
	public synchronized int openStreamCount() {
		return streams.size();
	}

	private boolean onDemand() {
		return props.streamsEnabled() && props.streamsOnDemand();
	}

	private synchronized void closeIfIdle() {
		pendingStop = null;
		if (sessions.isEmpty() && !streams.isEmpty()) {
			log.info("No WebSocket clients for {} s; closing the gRPC streams", props.streamsIdleGrace().toSeconds());
			closeStreams();
		}
	}

	private void cancelPendingStop() {
		if (pendingStop != null) {
			pendingStop.cancel(false);
			pendingStop = null;
		}
	}

	private synchronized void openStreams() {
		if (!streams.isEmpty()) {
			return;
		}
		streams.add(new ResilientStream<TelemetryReading>("telemetry",
				(ctx, obs) -> ctx.run(() -> telemetry.withWaitForReady()
					.streamTelemetry(StreamTelemetryRequest.getDefaultInstance(), obs)),
				r -> broker.convertAndSend("/topic/telemetry", DtoMapper.reading(r)), scheduler,
				props.streamInitialBackoff(), props.streamMaxBackoff(), props.streamRotateAfter()));
		streams.add(new ResilientStream<DeviceStatusEvent>("device-status",
				(ctx, obs) -> ctx.run(() -> telemetry.withWaitForReady()
					.streamDeviceStatus(StreamDeviceStatusRequest.getDefaultInstance(), obs)),
				e -> broker.convertAndSend("/topic/device-status", DtoMapper.deviceStatus(e)), scheduler,
				props.streamInitialBackoff(), props.streamMaxBackoff(), props.streamRotateAfter()));
		streams.add(new ResilientStream<IrrigationConfig>("config",
				(ctx, obs) -> ctx.run(() -> irrigation.withWaitForReady()
					.streamConfigChanges(StreamConfigChangesRequest.getDefaultInstance(), obs)),
				c -> broker.convertAndSend("/topic/config", DtoMapper.config(c)), scheduler,
				props.streamInitialBackoff(), props.streamMaxBackoff(), props.streamRotateAfter()));
		streams.add(new ResilientStream<Analysis>("analysis",
				(ctx, obs) -> ctx.run(
						() -> ai.withWaitForReady().streamAnalyses(StreamAnalysesRequest.getDefaultInstance(), obs)),
				a -> broker.convertAndSend("/topic/analysis", DtoMapper.analysis(a)), scheduler,
				props.streamInitialBackoff(), props.streamMaxBackoff(), props.streamRotateAfter()));
		streams.forEach(ResilientStream::start);
	}

	private synchronized void closeStreams() {
		streams.forEach(ResilientStream::stop);
		streams.clear();
	}

	@PreDestroy
	public void stop() {
		synchronized (this) {
			cancelPendingStop();
			closeStreams();
		}
		scheduler.shutdownNow();
	}

}
