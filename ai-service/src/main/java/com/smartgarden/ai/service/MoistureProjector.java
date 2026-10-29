package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.Confidence;
import com.smartgarden.ai.domain.DryingRate;
import com.smartgarden.ai.domain.Projection;
import com.smartgarden.ai.domain.ProjectionPoint;
import com.smartgarden.ai.domain.WeatherSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hourly soil-moisture projection for the next 24 hours.
 *
 * <pre>
 *   m(0)   = current valid moisture
 *   m(k)   = clamp( m(k−1) + r·Δt(k) + rain(k), 0, 100 )          k = 1..24, points at the next full hours
 *   r      = drying rate (points/h, usually negative); 0 if unknown
 *   rain(k)= min(2.5 · P(k), 5)   if probability(k) ≥ 70 % and P(k) > 0 mm   (P = forecast precipitation of that hour)
 *            the accumulated rain gain over the 24 h is capped at 15 points
 * </pre>
 *
 * The forecast covers 12 h; beyond it no rain is assumed. The estimated next irrigation is the moment the projection
 * crosses the minimum threshold (linear interpolation between points); "now" if the soil is already below it; null if
 * it does not happen within 24 h. Confidence is the drying-rate confidence (LOW when the rate is unknown).
 */
public final class MoistureProjector {

	static final int HOURS = 24;

	static final double RAIN_PROBABILITY = 70;

	static final double RAIN_GAIN_PER_MM = 2.5;

	static final double MAX_RAIN_GAIN_PER_HOUR = 5;

	static final double MAX_RAIN_GAIN_TOTAL = 15;

	private MoistureProjector() {
	}

	/**
	 * @param currentMoisture valid current moisture, or null (then no projection is possible)
	 * @param weather forecast, or null
	 */
	public static Projection project(Integer currentMoisture, DryingRate rate, WeatherSnapshot weather,
			int minThreshold, Instant now) {
		if (currentMoisture == null) {
			return new Projection(List.of(), rate.ratePerHour(), null, Confidence.LOW);
		}
		double r = rate.ratePerHour() == null ? 0 : rate.ratePerHour();
		Map<Instant, WeatherSnapshot.Hourly> forecastByHour = new HashMap<>();
		if (weather != null) {
			weather.hourly().forEach(h -> forecastByHour.put(h.time().truncatedTo(ChronoUnit.HOURS), h));
		}

		List<ProjectionPoint> points = new ArrayList<>(HOURS);
		Instant estimated = currentMoisture < minThreshold ? now : null;
		Instant previousTime = now;
		double previous = currentMoisture;
		double rainTotal = 0;
		Instant hour = now.truncatedTo(ChronoUnit.HOURS);
		for (int k = 1; k <= HOURS; k++) {
			Instant time = hour.plus(Duration.ofHours(k));
			double dtHours = Duration.between(previousTime, time).toSeconds() / 3600.0;
			double gain = 0;
			// rain falling during [time − 1 h, time)
			WeatherSnapshot.Hourly h = forecastByHour.get(time.minus(Duration.ofHours(1)));
			if (h != null && h.precipitationProbability() >= RAIN_PROBABILITY && h.precipitation() > 0
					&& rainTotal < MAX_RAIN_GAIN_TOTAL) {
				gain = Math.min(Math.min(RAIN_GAIN_PER_MM * h.precipitation(), MAX_RAIN_GAIN_PER_HOUR),
						MAX_RAIN_GAIN_TOTAL - rainTotal);
				rainTotal += gain;
			}
			double value = Math.clamp(previous + r * dtHours + gain, 0, 100);
			if (estimated == null && value < minThreshold && previous >= minThreshold) {
				double fraction = (previous - minThreshold) / (previous - value);
				estimated = previousTime.plusSeconds(Math.round(Duration.between(previousTime, time).toSeconds() * fraction));
			}
			points.add(new ProjectionPoint(time, Math.round(value * 10.0) / 10.0));
			previous = value;
			previousTime = time;
		}
		Confidence confidence = rate.ratePerHour() == null ? Confidence.LOW : rate.confidence();
		return new Projection(List.copyOf(points), rate.ratePerHour(), estimated, confidence);
	}

	/** Hours from now until the estimated next irrigation (0.1 h precision), or null. */
	public static Double hoursUntil(Projection projection, Instant now) {
		Instant at = projection.estimatedNextIrrigationAt();
		if (at == null) {
			return null;
		}
		return Math.max(0, Math.round(Duration.between(now, at).toMinutes() / 6.0) / 10.0);
	}

}
