package com.smartgarden.gateway.security;

import com.smartgarden.gateway.repository.AppUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class UserSessionState {

	static final Duration TTL = Duration.ofSeconds(30);

	private final AppUserRepository users;

	private final Clock clock;

	private final ConcurrentHashMap<String, Snapshot> cache = new ConcurrentHashMap<>();

	public UserSessionState(AppUserRepository users, Clock clock) {
		this.users = users;
		this.clock = clock;
	}

	public Optional<Snapshot> snapshot(String username) {
		if (username == null) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		Snapshot cached = cache.get(username);
		if (cached != null && now.isBefore(cached.fetchedAt().plus(TTL))) {
			return Optional.of(cached);
		}
		Optional<Snapshot> fresh = users.findByUsername(username)
			.map(u -> new Snapshot(u.getTokenVersion(), u.isMustChangePassword(), now));
		fresh.ifPresent(s -> cache.put(username, s));
		if (fresh.isEmpty()) {
			cache.remove(username);
		}
		return fresh;
	}

	public void invalidate(String username) {
		if (username != null) {
			cache.remove(username);
		}
	}

	public void clear() {
		cache.clear();
	}

	public record Snapshot(int tokenVersion, boolean mustChangePassword, Instant fetchedAt) {
	}

}
