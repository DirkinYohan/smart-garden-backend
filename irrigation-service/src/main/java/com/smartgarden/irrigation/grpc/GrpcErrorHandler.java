package com.smartgarden.irrigation.grpc;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;

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
		if (ex instanceof StatusRuntimeException downstream) {

			log.warn("Downstream gRPC call failed: {}", downstream.getStatus());
			Status status = downstream.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED ? Status.UNAVAILABLE
					: downstream.getStatus();
			return status.withDescription("No se pudo contactar el dispositivo: " + status.getCode()).asException();
		}
		if (ex instanceof org.springframework.dao.DataIntegrityViolationException) {
			return Status.INVALID_ARGUMENT.withDescription("Datos inválidos o duplicados").asException();
		}
		log.error("Unexpected gRPC error", ex);
		return Status.INTERNAL.withDescription("Error interno").asException();
	}

}
