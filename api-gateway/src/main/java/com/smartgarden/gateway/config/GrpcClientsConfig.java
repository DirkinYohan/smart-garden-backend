package com.smartgarden.gateway.config;

import com.smartgarden.contracts.ai.v1.AiServiceGrpc;
import com.smartgarden.contracts.irrigation.v1.IrrigationServiceGrpc;
import com.smartgarden.contracts.telemetry.v1.TelemetryServiceGrpc;
import com.smartgarden.common.security.InternalTokenClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractStub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * One channel per downstream service (configured under spring.grpc.client.channel.*). Blocking stubs for REST calls,
 * async stubs for the server streams relayed to WebSocket.
 */
@Configuration
public class GrpcClientsConfig {

	/** Testigo interno (INTERNAL_TOKEN); vacío = sin autenticación entre servicios. */
	@Value("${smartgarden.internal-token:}")
	private String internalToken;

	/**
	 * Aplica el testigo interno a un stub. <b>Obligatorio en los seis beans</b> (los tres blocking y los tres async):
	 * los stubs de streaming son los que alimentan el WebSocket, y sin el testigo el relay recibe UNAUTHENTICATED y
	 * la interfaz deja de actualizarse en tiempo real.
	 */
	private <T extends AbstractStub<T>> T withToken(T stub) {
		return stub.withInterceptors(new InternalTokenClientInterceptor(internalToken));
	}

	@Bean
	ManagedChannel telemetryChannel(GrpcChannelFactory channels) {
		return channels.createChannel("telemetry");
	}

	@Bean
	ManagedChannel irrigationChannel(GrpcChannelFactory channels) {
		return channels.createChannel("irrigation");
	}

	@Bean
	ManagedChannel aiChannel(GrpcChannelFactory channels) {
		return channels.createChannel("ai");
	}

	@Bean
	TelemetryServiceGrpc.TelemetryServiceBlockingStub telemetryBlockingStub(ManagedChannel telemetryChannel) {
		return withToken(TelemetryServiceGrpc.newBlockingStub(telemetryChannel));
	}

	@Bean
	TelemetryServiceGrpc.TelemetryServiceStub telemetryAsyncStub(ManagedChannel telemetryChannel) {
		return withToken(TelemetryServiceGrpc.newStub(telemetryChannel));
	}

	@Bean
	IrrigationServiceGrpc.IrrigationServiceBlockingStub irrigationBlockingStub(ManagedChannel irrigationChannel) {
		return withToken(IrrigationServiceGrpc.newBlockingStub(irrigationChannel));
	}

	@Bean
	IrrigationServiceGrpc.IrrigationServiceStub irrigationAsyncStub(ManagedChannel irrigationChannel) {
		return withToken(IrrigationServiceGrpc.newStub(irrigationChannel));
	}

	@Bean
	AiServiceGrpc.AiServiceBlockingStub aiBlockingStub(ManagedChannel aiChannel) {
		return withToken(AiServiceGrpc.newBlockingStub(aiChannel));
	}

	@Bean
	AiServiceGrpc.AiServiceStub aiAsyncStub(ManagedChannel aiChannel) {
		return withToken(AiServiceGrpc.newStub(aiChannel));
	}

}
