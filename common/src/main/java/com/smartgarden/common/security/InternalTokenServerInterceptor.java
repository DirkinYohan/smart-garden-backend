package com.smartgarden.common.security;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Rechaza las llamadas gRPC que no traen el testigo interno correcto: solo los otros servicios del despliegue pueden
 * invocar este servicio, aunque alguien alcance el puerto dentro de la red.
 */
public class InternalTokenServerInterceptor implements ServerInterceptor {

	private final String expected;

	public InternalTokenServerInterceptor(String expected) {
		this.expected = expected;
	}

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {
		if (!InternalToken.isEnabled(expected)) {
			return next.startCall(call, headers);
		}
		if (expected.equals(headers.get(InternalToken.HEADER))) {
			return next.startCall(call, headers);
		}
		call.close(Status.UNAUTHENTICATED.withDescription("Testigo interno ausente o inválido"), new Metadata());
		return new ServerCall.Listener<>() {
		};
	}

}
