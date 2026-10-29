package com.smartgarden.ai.service;

import com.smartgarden.ai.client.OpenMeteoClient;
import com.smartgarden.ai.config.WeatherProperties;
import com.smartgarden.ai.domain.WeatherSnapshot;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Open-Meteo forecast with a small in-memory cache (shared by analyses and GetWeather).
 */
@Service
public class WeatherService {

	private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

	private final OpenMeteoClient client;

	private final WeatherProperties props;

	private final Clock clock;

	private volatile WeatherSnapshot cached;

	public WeatherService(OpenMeteoClient client, WeatherProperties props, Clock clock) {
		this.client = client;
		this.props = props;
		this.clock = clock;
	}

	/**
	 * @throws WeatherUnavailableException if Open-Meteo cannot be reached and nothing is cached
	 */
	public synchronized WeatherSnapshot current() {
		WeatherSnapshot snapshot = cached;
		if (snapshot != null && snapshot.fetchedAt().plus(props.cacheTtl()).isAfter(clock.instant())) {
			return snapshot;
		}
		try {
			snapshot = client.fetch();
			cached = snapshot;
			log.debug("Weather refreshed: rain12h={}% temp={}°C", snapshot.maxRainProbability12h(),
					snapshot.current().temperature());
			return snapshot;
		}
		catch (RuntimeException ex) {
			log.warn("Open-Meteo unavailable: {}", ex.getMessage());
			if (cached != null) {
				return cached; // stale but better than nothing
			}
			throw new WeatherUnavailableException("El servicio de clima no está disponible", ex);
		}
	}

}
