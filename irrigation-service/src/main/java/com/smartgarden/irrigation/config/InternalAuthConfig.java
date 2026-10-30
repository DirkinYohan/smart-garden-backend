package com.smartgarden.irrigation.config;

import com.smartgarden.common.security.InternalTokenServerInterceptor;
import io.grpc.ServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;

@Configuration
public class InternalAuthConfig {

	@Bean
	@GlobalServerInterceptor
	ServerInterceptor internalTokenInterceptor(@Value("${smartgarden.internal-token:}") String token) {
		return new InternalTokenServerInterceptor(token);
	}

}
