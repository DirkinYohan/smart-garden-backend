package com.smartgarden.gateway.web;

import com.smartgarden.gateway.dto.ErrorResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Every error leaves the API as {@code {timestamp, status, error, message, path}}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private final Clock clock;

	public ApiExceptionHandler(Clock clock) {
		this.clock = clock;
	}

	@ExceptionHandler(StatusRuntimeException.class)
	ResponseEntity<ErrorResponse> grpc(StatusRuntimeException ex, HttpServletRequest request) {
		Status status = ex.getStatus();
		HttpStatus http = GrpcStatusMapper.toHttp(status.getCode());
		if (http.is5xxServerError()) {
			log.warn("gRPC call failed for {}: {} {}", request.getRequestURI(), status.getCode(),
					status.getDescription());
		}
		return body(http, GrpcStatusMapper.message(status), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String message = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(FieldError::getDefaultMessage)
			.distinct()
			.collect(Collectors.joining("; "));
		return body(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Solicitud inválida" : message, request);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ErrorResponse> invalidParams(HandlerMethodValidationException ex, HttpServletRequest request) {
		String message = ex.getAllErrors()
			.stream()
			.map(e -> e.getDefaultMessage())
			.distinct()
			.collect(Collectors.joining("; "));
		return body(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Parámetros inválidos" : message, request);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class })
	ResponseEntity<ErrorResponse> unreadable(Exception ex, HttpServletRequest request) {
		return body(HttpStatus.BAD_REQUEST, "Solicitud mal formada o con valores inválidos", request);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErrorResponse> illegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
		return body(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	@ExceptionHandler(com.smartgarden.gateway.security.AccountLockedException.class)
	ResponseEntity<ErrorResponse> locked(com.smartgarden.gateway.security.AccountLockedException ex,
			HttpServletRequest request) {
		return body(HttpStatus.LOCKED, ex.getMessage(), request);
	}

	@ExceptionHandler(com.smartgarden.gateway.security.PasswordPolicyException.class)
	ResponseEntity<ErrorResponse> passwordPolicy(com.smartgarden.gateway.security.PasswordPolicyException ex,
			HttpServletRequest request) {
		return body(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ErrorResponse> authentication(AuthenticationException ex, HttpServletRequest request) {
		return body(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos", request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
		return body(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido", request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> notFound(NoResourceFoundException ex, HttpServletRequest request) {
		return body(HttpStatus.NOT_FOUND, "Recurso no encontrado", request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
		log.error("Unexpected error on {}", request.getRequestURI(), ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request);
	}

	private ResponseEntity<ErrorResponse> body(HttpStatus status, String message, HttpServletRequest request) {
		return ResponseEntity.status(status)
			.body(ErrorResponse.of(clock.instant(), status, message, request.getRequestURI()));
	}

}
