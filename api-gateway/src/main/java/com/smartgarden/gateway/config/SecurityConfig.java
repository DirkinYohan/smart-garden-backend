package com.smartgarden.gateway.config;

import com.smartgarden.gateway.security.JwtAuthenticationFilter;
import com.smartgarden.gateway.security.JwtService;
import com.smartgarden.gateway.security.RestAuthenticationEntryPoint;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	private static final int BCRYPT_STRENGTH = 12;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
			RestAuthenticationEntryPoint entryPoint, com.smartgarden.gateway.security.UserSessionState sessionState,
			tools.jackson.databind.json.JsonMapper jsonMapper, Clock clock) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.cors(cors -> {
			})
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(entryPoint))
			.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.OPTIONS, "/**")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
				.permitAll()
				.requestMatchers("/ws", "/ws/**")
				.permitAll()
				.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
				.permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
					"/actuator/metrics", "/actuator/metrics/**")
				.permitAll()
				.requestMatchers("/error")
				.permitAll()
				.anyRequest()
				.authenticated())
			.addFilterBefore(new JwtAuthenticationFilter(jwtService, sessionState, jsonMapper, clock),
					UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(props.cors().allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
