package com.smartgarden.gateway.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import com.smartgarden.contracts.ai.v1.AiServiceGrpc;
import com.smartgarden.contracts.irrigation.v1.IrrigationServiceGrpc;
import com.smartgarden.contracts.telemetry.v1.TelemetryServiceGrpc;
import com.smartgarden.gateway.config.GrpcGatewayProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

class StreamRelayOnDemandTest {

	private ManagedChannel channel;

	private StreamRelay relay;

	@BeforeEach
	void setUp() {
		// Nothing listens on port 1: the streams just keep retrying, which is enough here.
		channel = ManagedChannelBuilder.forTarget("127.0.0.1:1").usePlaintext().build();
		GrpcGatewayProperties props = new GrpcGatewayProperties(Duration.ofSeconds(2), Duration.ofSeconds(1),
				Duration.ofSeconds(5), true, true, Duration.ofMillis(200), false, Duration.ofSeconds(10), Duration.ZERO);
		relay = new StreamRelay(TelemetryServiceGrpc.newStub(channel), IrrigationServiceGrpc.newStub(channel),
				AiServiceGrpc.newStub(channel), mock(SimpMessagingTemplate.class), props);
	}

	@AfterEach
	void tearDown() {
		relay.stop();
		channel.shutdownNow();
	}

	@Test
	void streamsStayClosedUntilAWebSocketClientConnects() {
		relay.start();

		assertThat(relay.openStreamCount()).isZero();
	}

	@Test
	void streamsOpenWithTheFirstClientAndCloseAfterTheLastOneLeaves() {
		relay.onClientConnected(connected("s1"));
		relay.onClientConnected(connected("s2"));
		assertThat(relay.openStreamCount()).isEqualTo(4);

		relay.onClientDisconnected(disconnected("s1"));
		assertThat(relay.openStreamCount()).isEqualTo(4);

		relay.onClientDisconnected(disconnected("s2"));
		await().atMost(Duration.ofSeconds(3)).until(() -> relay.openStreamCount() == 0);
	}

	@Test
	void aClientReconnectingWithinTheGracePeriodKeepsTheStreams() throws InterruptedException {
		relay.onClientConnected(connected("s1"));
		relay.onClientDisconnected(disconnected("s1"));
		relay.onClientConnected(connected("s2"));

		Thread.sleep(500);
		assertThat(relay.openStreamCount()).isEqualTo(4);
	}

	private static SessionConnectedEvent connected(String sessionId) {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
		headers.setSessionId(sessionId);
		headers.setLeaveMutable(true);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
		return new SessionConnectedEvent(new Object(), message);
	}

	private static SessionDisconnectEvent disconnected(String sessionId) {
		Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).copyHeaders(Map.of()).build();
		return new SessionDisconnectEvent(new Object(), message, sessionId, CloseStatus.NORMAL);
	}

}
