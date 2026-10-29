package com.smartgarden.irrigation.config;

import com.smartgarden.contracts.telemetry.v1.TelemetryServiceGrpc;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/** Channel "telemetry" is configured under spring.grpc.client.channel.telemetry (h2c by default). */
	@Bean
	TelemetryServiceGrpc.TelemetryServiceBlockingStub telemetryStub(GrpcChannelFactory channels) {
		return TelemetryServiceGrpc.newBlockingStub(channels.createChannel("telemetry"));
	}

}
