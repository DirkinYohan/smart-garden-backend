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

@Configuration
public class GrpcClientsConfig {

	@Value("${smartgarden.internal-token:}")
	private String internalToken;

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
