package com.smartgarden.gateway.security;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP CONNECT frame with {@code Authorization: Bearer <jwt>} (native header). It applies the same
 * rules as the REST filter: the token's {@code tv} must match the user's current token_version, and a user who must
 * change the password cannot open the WebSocket. Only subscriptions to the known read-only topics are allowed.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

	static final String PASSWORD_CHANGE_REQUIRED = "Debes cambiar la contraseña antes de continuar";

	static final Set<String> TOPICS = Set.of("/topic/telemetry", "/topic/config", "/topic/analysis",
			"/topic/device-status");

	private final JwtService jwtService;

	private final UserSessionState sessionState;

	public StompAuthChannelInterceptor(JwtService jwtService, UserSessionState sessionState) {
		this.jwtService = jwtService;
		this.sessionState = sessionState;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}
		StompCommand command = accessor.getCommand();
		switch (command) {
			case CONNECT -> {
				String header = accessor.getFirstNativeHeader("Authorization");
				if (header == null || !header.startsWith("Bearer ")) {
					throw new MessageDeliveryException("Se requiere el header Authorization: Bearer <token>");
				}
				JwtService.TokenClaims claims;
				try {
					claims = jwtService.parse(header.substring(7).trim());
				}
				catch (JwtService.InvalidTokenException ex) {
					log.debug("Rejected STOMP CONNECT: {}", ex.getMessage());
					throw new MessageDeliveryException(ex.getMessage());
				}
				UserSessionState.Snapshot state = sessionState.snapshot(claims.username()).orElse(null);
				if (state == null || state.tokenVersion() != claims.tokenVersion()) {
					throw new MessageDeliveryException("Tu sesión se cerró porque la contraseña cambió");
				}
				if (state.mustChangePassword()) {
					throw new MessageDeliveryException(PASSWORD_CHANGE_REQUIRED);
				}
				accessor.setUser(jwtService.authentication(claims));
			}
			case SUBSCRIBE -> {
				if (accessor.getUser() == null) {
					throw new MessageDeliveryException("No autenticado");
				}
				if (!TOPICS.contains(accessor.getDestination())) {
					throw new MessageDeliveryException("Destino no permitido: " + accessor.getDestination());
				}
			}
			case SEND -> throw new MessageDeliveryException("Los clientes no pueden enviar mensajes");
			default -> {
			}
		}
		return message;
	}

}
