package com.smartgarden.gateway.web;

import io.grpc.Status;
import org.springframework.http.HttpStatus;

/**
 * gRPC status code → HTTP status.
 */
public final class GrpcStatusMapper {

	private GrpcStatusMapper() {
	}

	public static HttpStatus toHttp(Status.Code code) {
		return switch (code) {
			case INVALID_ARGUMENT, OUT_OF_RANGE -> HttpStatus.BAD_REQUEST;
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case FAILED_PRECONDITION, ALREADY_EXISTS, ABORTED -> HttpStatus.CONFLICT;
			case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
			case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
			case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
			case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
			case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
			case UNIMPLEMENTED -> HttpStatus.NOT_IMPLEMENTED;
			default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}

	/**
	 * Message for the client: the description sent by the service, or a generic Spanish text. Transport failures
	 * (service down, connection refused) carry a local cause and a technical description, so they get the generic one.
	 */
	public static String message(Status status) {
		if (status.getCause() == null && status.getDescription() != null && !status.getDescription().isBlank()
				&& status.getCode() != Status.Code.INTERNAL && status.getCode() != Status.Code.UNKNOWN) {
			return status.getDescription();
		}
		return switch (status.getCode()) {
			case UNAVAILABLE -> "Servicio no disponible temporalmente";
			case DEADLINE_EXCEEDED -> "El servicio tardó demasiado en responder";
			default -> "Error interno del servidor";
		};
	}

}
