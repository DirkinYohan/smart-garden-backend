package com.smartgarden.telemetry.grpc;

import io.grpc.Status;
import io.grpc.StatusException;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

/**
 * Maps service exceptions to gRPC status codes (picked up automatically by Spring gRPC).
 */
@Component
public class GrpcErrorHandler implements GrpcExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GrpcErrorHandler.class);

	@Override
	public StatusException handleException(Throwable ex) {
		if (ex instanceof IllegalArgumentException) {
			return Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asException();
		}
		if (ex instanceof NoSuchElementException) {
			return Status.NOT_FOUND.withDescription(ex.getMessage()).asException();
		}
		if (ex instanceof IllegalStateException) {
			return Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asException();
		}
		if (ex instanceof MessagingException) {
			log.warn("MQTT broker unavailable: {}", ex.getMessage());
			return Status.UNAVAILABLE.withDescription("El broker MQTT no está disponible").withCause(ex).asException();
		}
		log.error("Unexpected gRPC error", ex);
		return Status.INTERNAL.withDescription("Error interno").withCause(ex).asException();
	}

}
