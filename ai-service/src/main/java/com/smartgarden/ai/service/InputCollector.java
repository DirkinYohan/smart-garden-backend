package com.smartgarden.ai.service;

import com.smartgarden.ai.client.GardenDataClient;
import com.smartgarden.ai.domain.AnalysisInputData;
import com.smartgarden.ai.domain.Sample;
import com.smartgarden.ai.domain.WeatherSnapshot;
import com.smartgarden.contracts.irrigation.v1.IrrigationConfig;
import com.smartgarden.contracts.telemetry.v1.CurrentStatus;
import com.smartgarden.contracts.telemetry.v1.GetHistoryResponse;
import com.smartgarden.contracts.telemetry.v1.HistoryPoint;
import com.smartgarden.contracts.telemetry.v1.IrrigationEvent;
import com.smartgarden.contracts.telemetry.v1.TelemetryReading;
import io.grpc.StatusRuntimeException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gathers the analysis input from telemetry, irrigation and weather. Each source is optional: a failing source is
 * logged and the analysis continues with what is available.
 * <p>
 * The current moisture always comes from {@code TelemetryService.GetCurrentStatus}, and only from a VALID reading
 * (sensorOk and a raw value not stuck at an ADC rail) received in the last 5 minutes. Otherwise the analysis says
 * there is no reading instead of using a stale or invalid value (the "0 %" bug).
 */
@Component
public class InputCollector {

	private static final Logger log = LoggerFactory.getLogger(InputCollector.class);

	public static final Duration MAX_READING_AGE = Duration.ofMinutes(5);

	/** Same rails as telemetry-service (12-bit ADC): defense in depth for readings stored before the fix. */
	static final int ADC_RAIL_LOW = 10;

	static final int ADC_RAIL_HIGH = 4000;

	private final GardenDataClient garden;

	private final WeatherService weather;

	private final Clock clock;

	public InputCollector(GardenDataClient garden, WeatherService weather, Clock clock) {
		this.garden = garden;
		this.weather = weather;
		this.clock = clock;
	}

	public AnalysisContext collect() {
		CurrentStatus status = safely("telemetry status", garden::currentStatus);
		GetHistoryResponse history = safely("telemetry history", garden::history24h);
		List<Sample> samples = orEmpty(safely("telemetry samples", () -> garden.recentSamples(12)));
		List<IrrigationEvent> events = orEmpty(safely("irrigation events", () -> garden.irrigationEvents(50)));
		IrrigationConfig config = safely("irrigation config", garden::irrigationConfig);
		WeatherSnapshot forecast = safely("weather", weather::current);
		Instant now = clock.instant();
		return new AnalysisContext(build(status, history, config, forecast, events, now), forecast, samples, now);
	}

	static AnalysisInputData build(CurrentStatus status, GetHistoryResponse history, IrrigationConfig config,
			WeatherSnapshot forecast, List<IrrigationEvent> events, Instant now) {
		TelemetryReading latest = status != null && status.getHasReading() ? status.getReading() : null;
		TelemetryReading valid = null;
		if (status != null && status.hasLastValidReading() && isValid(status.getLastValidReading())) {
			valid = status.getLastValidReading();
		}
		else if (latest != null && isValid(latest)) {
			valid = latest;
		}
		Double validAge = valid == null ? null : ageMinutes(valid, now);
		boolean hasReading = validAge != null && validAge <= MAX_READING_AGE.toMinutes();
		boolean sensorFault = !hasReading && latest != null && !isValid(latest)
				&& ageMinutes(latest, now) <= MAX_READING_AGE.toMinutes();

		long samples = 0;
		double weightedSum = 0;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		if (history != null) {
			for (HistoryPoint p : history.getPointsList()) {
				if (p.getSamples() <= 0) {
					continue;
				}
				samples += p.getSamples();
				weightedSum += p.getAvgMoisture() * p.getSamples();
				min = Math.min(min, p.getMinMoisture());
				max = Math.max(max, p.getMaxMoisture());
			}
		}

		Instant dayAgo = now.minus(Duration.ofHours(24));
		int irrigations = 0;
		long pumpSeconds = 0;
		for (IrrigationEvent e : events) {
			Instant started = instant(e.getStartedAt());
			if (started.isBefore(dayAgo)) {
				continue;
			}
			irrigations++;
			Instant ended = e.hasEndedAt() ? instant(e.getEndedAt()) : now;
			pumpSeconds += Math.max(0, Duration.between(started, ended).toSeconds());
		}

		boolean configAvailable = config != null;
		return new AnalysisInputData(configAvailable && config.hasCrop() ? config.getCrop().getName() : "Genérico",
				configAvailable ? config.getMode().name().replace("IRRIGATION_MODE_", "") : "DESCONOCIDO",
				configAvailable && config.getAiEnabled(), configAvailable,
				configAvailable ? config.getMinMoisture() : 0, configAvailable ? config.getMaxMoisture() : 0,
				configAvailable && config.hasCrop() ? config.getCrop().getMinMoisture() : 0,
				configAvailable && config.hasCrop() ? config.getCrop().getMaxMoisture() : 0, hasReading,
				status != null && status.getDeviceOnline(), hasReading ? valid.getMoisture() : 0,
				hasReading && valid.getPumpOn(), samples > 0 ? Math.round(weightedSum / samples * 10.0) / 10.0 : 0,
				samples > 0 ? min : 0, samples > 0 ? max : 0, (int) samples, forecast != null,
				forecast != null ? forecast.maxRainProbability12h() : 0,
				forecast != null ? forecast.totalPrecipitation12h() : 0,
				forecast != null ? forecast.current().temperature() : 0,
				forecast != null ? forecast.current().relativeHumidity() : 0, sensorFault,
				validAge == null ? null : Math.round(validAge * 10.0) / 10.0, null, irrigations,
				Math.round(pumpSeconds / 60.0 * 10.0) / 10.0, configAvailable && config.getRainPause(),
				configAvailable && config.hasCrop() ? config.getCrop().getId() : 0L);
	}

	static boolean isValid(TelemetryReading r) {
		return r.getSensorOk() && r.getRaw() > ADC_RAIL_LOW && r.getRaw() < ADC_RAIL_HIGH;
	}

	private static double ageMinutes(TelemetryReading r, Instant now) {
		return Math.max(0, Duration.between(instant(r.getReceivedAt()), now).toSeconds() / 60.0);
	}

	private static Instant instant(com.google.protobuf.Timestamp ts) {
		return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
	}

	private static <T> List<T> orEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	private static <T> T safely(String source, Supplier<T> call) {
		try {
			return call.get();
		}
		catch (StatusRuntimeException ex) {
			log.warn("Analysis input '{}' unavailable: {}", source, ex.getStatus().getCode());
		}
		catch (RuntimeException ex) {
			log.warn("Analysis input '{}' unavailable: {}", source, ex.getMessage());
		}
		return null;
	}

	/** Input + raw data needed for the deterministic computations (projection, drying rate). */
	public record AnalysisContext(AnalysisInputData input, WeatherSnapshot weather, List<Sample> samples,
			Instant now) {
	}

}
