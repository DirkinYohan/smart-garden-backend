package com.smartgarden.common.security;

import io.grpc.Metadata;

public final class InternalToken {

	public static final Metadata.Key<String> HEADER = Metadata.Key.of("x-internal-token",
			Metadata.ASCII_STRING_MARSHALLER);

	private InternalToken() {
	}

	public static boolean isEnabled(String token) {
		return token != null && !token.isBlank();
	}

}
