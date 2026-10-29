package com.smartgarden.irrigation.config;

import com.smartgarden.common.security.InternalTokenClientInterceptor;
import io.grpc.ClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GlobalClientInterceptor;

/**
 * Añade el testigo interno (cabecera `x-internal-token`) a <b>todas</b> las llamadas gRPC salientes de este servicio.
 * Sin él, los demás servicios responden UNAUTHENTICATED y el análisis/riego dejan de funcionar.
 */
@Configuration
public class InternalAuthClientConfig {

	@Bean
	@GlobalClientInterceptor
	ClientInterceptor internalTokenClientInterceptor(@Value("${smartgarden.internal-token:}") String token) {
		return new InternalTokenClientInterceptor(token);
	}

}
