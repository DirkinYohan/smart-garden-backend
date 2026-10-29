package com.smartgarden.common.security;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Añade el testigo interno a cada llamada gRPC saliente. Si no hay testigo configurado no hace nada (desarrollo y
 * tests).
 */
public class InternalTokenClientInterceptor implements ClientInterceptor {

	private final String token;

	public InternalTokenClientInterceptor(String token) {
		this.token = token;
	}

	@Override
	public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
			CallOptions callOptions, Channel next) {
		return new SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
			@Override
			public void start(Listener<RespT> listener, Metadata headers) {
				if (InternalToken.isEnabled(token)) {
					headers.put(InternalToken.HEADER, token);
				}
				super.start(listener, headers);
			}
		};
	}

}
