package com.smartgarden.common.security;

import io.grpc.Metadata;

/**
 * Autenticación entre servicios: un testigo compartido viaja en los metadatos gRPC. Es defensa en profundidad sobre la
 * red privada de Docker (sustituto portable de mTLS dentro del compose; con mTLS se puede retirar).
 *
 * <p>
 * Si el testigo está vacío (desarrollo local, tests) los interceptores no hacen nada.
 */
public final class InternalToken {

	public static final Metadata.Key<String> HEADER = Metadata.Key.of("x-internal-token",
			Metadata.ASCII_STRING_MARSHALLER);

	private InternalToken() {
	}

	public static boolean isEnabled(String token) {
		return token != null && !token.isBlank();
	}

}
