package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.Confidence;
import com.smartgarden.ai.domain.DryingRate;
import com.smartgarden.ai.domain.Sample;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Soil drying rate (moisture points per hour) by linear regression over the valid readings of the last 12 hours.
 * <p>
 * Readings taken while the pump is on, and during the 15 minutes after it stops, are excluded. The remaining
 * readings form "dry-down segments" (separated by irrigations), so a single straight line across them would be
 * distorted by the jumps. The slope is therefore a pooled within-segment least-squares estimate:
 *
 * <pre>
 *   slope = Σ_seg Σ_i (t_i − t̄_seg)(m_i − m̄_seg) / Σ_seg Σ_i (t_i − t̄_seg)²      (t in hours)
 * </pre>
 *
 * with R² computed with the same segment means. Confidence: HIGH with ≥ 20 readings spanning ≥ 6 h and R² ≥ 0.6;
 * MEDIUM with ≥ 8 readings spanning ≥ 3 h and R² ≥ 0.3; otherwise LOW. With fewer than 4 usable readings or less
 * than 1 h of data the rate is unknown. The result is clamped to [−10, +5] points/h.
 */
public final class DryingRateEstimator {

	static final Duration WINDOW = Duration.ofHours(12);

	static final Duration AFTER_PUMP_EXCLUSION = Duration.ofMinutes(15);

	private DryingRateEstimator() {
	}

	public static DryingRate estimate(List<Sample> samples, Instant now) {
		Instant since = now.minus(WINDOW);
		List<List<Sample>> segments = new ArrayList<>();
		List<Sample> current = new ArrayList<>();
		Instant excludedUntil = Instant.MIN;
		for (Sample s : samples) {
			if (s.time().isBefore(since)) {
				continue;
			}
			if (s.pumpOn()) {
				excludedUntil = s.time().plus(AFTER_PUMP_EXCLUSION);
				if (!current.isEmpty()) {
					segments.add(current);
					current = new ArrayList<>();
				}
				continue;
			}
			if (s.time().isBefore(excludedUntil)) {
				continue;
			}
			current.add(s);
		}
		if (!current.isEmpty()) {
			segments.add(current);
		}

		int n = 0;
		double sxy = 0;
		double sxx = 0;
		double ssTot = 0;
		Instant first = null;
		Instant last = null;
		List<double[]> means = new ArrayList<>();
		for (List<Sample> seg : segments) {
			if (seg.size() < 2) {
				continue;
			}
			double tMean = seg.stream().mapToDouble(s -> hours(s.time(), now)).average().orElse(0);
			double mMean = seg.stream().mapToDouble(Sample::moisture).average().orElse(0);
			means.add(new double[] { tMean, mMean });
			for (Sample s : seg) {
				double dt = hours(s.time(), now) - tMean;
				double dm = s.moisture() - mMean;
				sxy += dt * dm;
				sxx += dt * dt;
				ssTot += dm * dm;
				n++;
			}
			first = first == null || seg.getFirst().time().isBefore(first) ? seg.getFirst().time() : first;
			last = last == null || seg.getLast().time().isAfter(last) ? seg.getLast().time() : last;
		}
		if (n < 4 || sxx == 0 || first == null) {
			return new DryingRate(null, Confidence.LOW, n);
		}
		double spanHours = Duration.between(first, last).toMinutes() / 60.0;
		if (spanHours < 1) {
			return new DryingRate(null, Confidence.LOW, n);
		}
		double slope = sxy / sxx;
		double r2 = ssTot == 0 ? 1 : Math.min(1, (slope * slope * sxx) / ssTot);
		Confidence confidence;
		if (n >= 20 && spanHours >= 6 && r2 >= 0.6) {
			confidence = Confidence.HIGH;
		}
		else if (n >= 8 && spanHours >= 3 && r2 >= 0.3) {
			confidence = Confidence.MEDIUM;
		}
		else {
			confidence = Confidence.LOW;
		}
		double rate = Math.round(Math.clamp(slope, -10, 5) * 100.0) / 100.0;
		return new DryingRate(rate, confidence, n);
	}

	private static double hours(Instant t, Instant reference) {
		return Duration.between(reference, t).toSeconds() / 3600.0;
	}

}
