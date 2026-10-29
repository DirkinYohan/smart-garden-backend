package com.smartgarden.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GrpcStatusMapperTest {

	@Test
	void mapsRequiredStatusCodes() {
		assertThat(GrpcStatusMapper.toHttp(Status.Code.INVALID_ARGUMENT)).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(GrpcStatusMapper.toHttp(Status.Code.NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(GrpcStatusMapper.toHttp(Status.Code.FAILED_PRECONDITION)).isEqualTo(HttpStatus.CONFLICT);
		assertThat(GrpcStatusMapper.toHttp(Status.Code.UNAVAILABLE)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(GrpcStatusMapper.toHttp(Status.Code.RESOURCE_EXHAUSTED)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(GrpcStatusMapper.toHttp(Status.Code.INTERNAL)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	void keepsServiceDescriptionButHidesTransportDetails() {
		assertThat(GrpcStatusMapper.message(Status.FAILED_PRECONDITION.withDescription("Solo en modo MANUAL")))
			.isEqualTo("Solo en modo MANUAL");
		assertThat(GrpcStatusMapper
			.message(Status.UNAVAILABLE.withDescription("io exception").withCause(new IOException("refused"))))
			.isEqualTo("Servicio no disponible temporalmente");
		assertThat(GrpcStatusMapper.message(Status.INTERNAL.withDescription("NullPointerException at ...")))
			.isEqualTo("Error interno del servidor");
	}

}
