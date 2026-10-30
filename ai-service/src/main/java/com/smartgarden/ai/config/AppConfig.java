package com.smartgarden.ai.config;

import com.smartgarden.contracts.irrigation.v1.IrrigationServiceGrpc;
import com.smartgarden.contracts.telemetry.v1.TelemetryServiceGrpc;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
public class AppConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	TelemetryServiceGrpc.TelemetryServiceBlockingStub telemetryStub(GrpcChannelFactory channels) {
		return TelemetryServiceGrpc.newBlockingStub(channels.createChannel("telemetry"));
	}

	@Bean
	IrrigationServiceGrpc.IrrigationServiceBlockingStub irrigationStub(GrpcChannelFactory channels) {
		return IrrigationServiceGrpc.newBlockingStub(channels.createChannel("irrigation"));
	}

	
	@Bean
	IrrigationServiceGrpc.IrrigationServiceStub irrigationAsyncStub(GrpcChannelFactory channels) {
		return IrrigationServiceGrpc.newStub(channels.createChannel("irrigation"));
	}

	@Bean
	RestClient openMeteoRestClient(RestClient.Builder builder, WeatherProperties props) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		return builder.baseUrl(props.baseUrl()).requestFactory(requestFactory).build();
	}

}
