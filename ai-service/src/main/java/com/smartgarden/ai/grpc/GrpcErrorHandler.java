package com.smartgarden.ai.grpc;

import com.smartgarden.ai.service.RateLimitedException;
import com.smartgarden.ai.service.WeatherUnavailableException;
import io.grpc.Status;
import io.grpc.StatusException;
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
		if (ex instanceof RateLimitedException) {
			return Status.RESOURCE_EXHAUSTED.withDescription(ex.getMessage()).asException();
		}
		if (ex instanceof WeatherUnavailableException) {
			return Status.UNAVAILABLE.withDescription(ex.getMessage()).asException();
		}
		if (ex instanceof NoSuchElementException) {
			return Status.NOT_FOUND.withDescription(ex.getMessage()).asException();
		}
		if (ex instanceof IllegalArgumentException) {
			return Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asException();
		}
		if (ex instanceof IllegalStateException) {
			return Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asException();
		}
		log.error("Unexpected gRPC error", ex);
		return Status.INTERNAL.withDescription("Error interno").asException();
	}

}
