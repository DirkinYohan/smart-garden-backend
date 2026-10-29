package com.smartgarden.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartgarden.ai.config.WeatherProperties;
import com.smartgarden.ai.domain.WeatherSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Open-Meteo forecast API (free, no API key). Requests the current conditions and the next 12 hours.
 * @throws org.springframework.web.client.RestClientException if the API is unreachable
 */
@Component
public class OpenMeteoClient {

	static final int FORECAST_HOURS = 12;

	private final RestClient restClient;

	private final WeatherProperties props;

	private final Clock clock;

	public OpenMeteoClient(RestClient openMeteoRestClient, WeatherProperties props, Clock clock) {
		this.restClient = openMeteoRestClient;
		this.props = props;
		this.clock = clock;
	}

	public WeatherSnapshot fetch() {
		OpenMeteoResponse response = restClient.get()
			.uri(uri -> uri.path("/v1/forecast")
				.queryParam("latitude", props.latitude())
				.queryParam("longitude", props.longitude())
				.queryParam("current", "temperature_2m,relative_humidity_2m,precipitation,weather_code")
				.queryParam("hourly", "temperature_2m,relative_humidity_2m,precipitation_probability,precipitation")
				.queryParam("forecast_hours", FORECAST_HOURS)
				.queryParam("timeformat", "unixtime")
				.queryParam("timezone", props.timezone())
				.build())
			.retrieve()
			.body(OpenMeteoResponse.class);
		if (response == null || response.current() == null || response.hourly() == null) {
			throw new IllegalStateException("Empty Open-Meteo response");
		}
		return toSnapshot(response, clock.instant());
	}

	static WeatherSnapshot toSnapshot(OpenMeteoResponse r, Instant fetchedAt) {
		CurrentBlock c = r.current();
		WeatherSnapshot.Current current = new WeatherSnapshot.Current(Instant.ofEpochSecond(c.time()),
				orZero(c.temperature()), orZero(c.relativeHumidity()), orZero(c.precipitation()),
				c.weatherCode() == null ? 0 : c.weatherCode());

		HourlyBlock h = r.hourly();
		List<WeatherSnapshot.Hourly> hourly = new ArrayList<>();
		int n = h.time() == null ? 0 : Math.min(h.time().size(), FORECAST_HOURS);
		double maxRain = 0;
		double totalPrecipitation = 0;
		for (int i = 0; i < n; i++) {
			double probability = at(h.precipitationProbability(), i);
			double precipitation = at(h.precipitation(), i);
			maxRain = Math.max(maxRain, probability);
			totalPrecipitation += precipitation;
			hourly.add(new WeatherSnapshot.Hourly(Instant.ofEpochSecond(h.time().get(i)), at(h.temperature(), i),
					at(h.relativeHumidity(), i), probability, precipitation));
		}
		return new WeatherSnapshot(fetchedAt, r.latitude(), r.longitude(), current, List.copyOf(hourly), maxRain,
				Math.round(totalPrecipitation * 10.0) / 10.0);
	}

	private static double at(List<Double> values, int i) {
		if (values == null || i >= values.size() || values.get(i) == null) {
			return 0;
		}
		return values.get(i);
	}

	private static double orZero(Double value) {
		return value == null ? 0 : value;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OpenMeteoResponse(double latitude, double longitude, CurrentBlock current, HourlyBlock hourly) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record CurrentBlock(long time, @JsonProperty("temperature_2m") Double temperature,
			@JsonProperty("relative_humidity_2m") Double relativeHumidity, Double precipitation,
			@JsonProperty("weather_code") Integer weatherCode) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record HourlyBlock(List<Long> time, @JsonProperty("temperature_2m") List<Double> temperature,
			@JsonProperty("relative_humidity_2m") List<Double> relativeHumidity,
			@JsonProperty("precipitation_probability") List<Double> precipitationProbability,
			List<Double> precipitation) {
	}

}
