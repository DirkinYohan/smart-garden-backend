package com.smartgarden.gateway.config;

import com.smartgarden.gateway.security.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final SecurityProperties props;

	private final StompAuthChannelInterceptor authInterceptor;

	public WebSocketConfig(SecurityProperties props, StompAuthChannelInterceptor authInterceptor) {
		this.props = props;
		this.authInterceptor = authInterceptor;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOrigins(props.cors().allowedOrigins().toArray(String[]::new));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// STOMP heartbeats every 10 s in both directions: keep the WebSocket busy so proxies and the Azure
		// Container Apps ingress do not close it as idle, and detect dead clients.
		ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();
		heartbeats.setPoolSize(1);
		heartbeats.setThreadNamePrefix("stomp-heartbeat-");
		heartbeats.setDaemon(true);
		heartbeats.initialize();
		registry.enableSimpleBroker("/topic").setHeartbeatValue(new long[] { 10_000, 10_000 })
			.setTaskScheduler(heartbeats);
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(authInterceptor);
	}

}
