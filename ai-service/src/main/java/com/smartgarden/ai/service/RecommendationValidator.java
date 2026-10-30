package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.GeminiAnswer;
import com.smartgarden.ai.domain.IrrigationRecommendation;
import com.smartgarden.ai.domain.Priority;
import com.smartgarden.ai.domain.RecommendationCategory;
import com.smartgarden.ai.domain.RecommendationDetail;
import com.smartgarden.ai.domain.SuggestedAction;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class RecommendationValidator {

	public static final int MIN_THRESHOLD = 10;

	public static final int MAX_THRESHOLD = 90;

	public static final int MIN_GAP = 10;

	public static final int MAX_RECOMMENDATIONS = 4;

	private static final int MAX_SUMMARY_CHARS = 400;

	private static final int MAX_HEADLINE_CHARS = 80;

	private static final int MAX_TITLE_CHARS = 60;

	private static final int MAX_TIP_CHARS = 220;

	private RecommendationValidator() {
	}

	public static IrrigationRecommendation sanitize(GeminiAnswer a) {
		if (a == null) {
			throw new InvalidRecommendationException("empty response");
		}
		if (a.recommendedMin() == null || a.recommendedMax() == null) {
			throw new InvalidRecommendationException("missing thresholds");
		}
		if (a.riskLevel() == null) {
			throw new InvalidRecommendationException("missing riskLevel");
		}
		if (a.summary() == null || a.summary().isBlank()) {
			throw new InvalidRecommendationException("missing summary");
		}
		if (a.headline() == null || a.headline().isBlank()) {
			throw new InvalidRecommendationException("missing headline");
		}
		List<RecommendationDetail> details = a.recommendationDetails() == null ? List.of()
				: a.recommendationDetails()
					.stream()
					.filter(Objects::nonNull)
					.filter(d -> d.detail() != null && !d.detail().isBlank())
					.map(RecommendationValidator::detail)
					.limit(MAX_RECOMMENDATIONS)
					.toList();
		if (details.isEmpty()) {
			throw new InvalidRecommendationException("no recommendations");
		}
		int[] thresholds = clampThresholds(a.recommendedMin(), a.recommendedMax());
		return new IrrigationRecommendation(thresholds[0], thresholds[1], Boolean.TRUE.equals(a.pauseForRain()), null,
				a.riskLevel(), limitSentences(a.summary().trim()),
				details.stream().map(RecommendationDetail::detail).toList(),
				truncate(a.headline().trim(), MAX_HEADLINE_CHARS), details);
	}

	private static RecommendationDetail detail(GeminiAnswer.Detail d) {
		String text = truncate(d.detail().trim(), MAX_TIP_CHARS);
		String title = d.title() == null || d.title().isBlank() ? firstWords(text) : truncate(d.title().trim(),
				MAX_TITLE_CHARS);
		return new RecommendationDetail(title, text,
				d.category() == null ? RecommendationCategory.IRRIGATION : d.category(),
				d.priority() == null ? Priority.MEDIUM : d.priority(),
				d.action() == null ? null : new SuggestedAction(d.action(), d.action().label()));
	}

	static int[] clampThresholds(int min, int max) {
		int lo = Math.clamp(Math.min(min, max), MIN_THRESHOLD, MAX_THRESHOLD);
		int hi = Math.clamp(Math.max(min, max), MIN_THRESHOLD, MAX_THRESHOLD);
		if (hi - lo < MIN_GAP) {
			hi = lo + MIN_GAP;
			if (hi > MAX_THRESHOLD) {
				hi = MAX_THRESHOLD;
				lo = MAX_THRESHOLD - MIN_GAP;
			}
		}
		return new int[] { lo, hi };
	}

	static String limitSentences(String text) {
		String[] sentences = text.split("(?<=[.!?])\\s+");
		String joined = String.join(" ", Arrays.copyOf(sentences, Math.min(2, sentences.length)));
		return truncate(joined, MAX_SUMMARY_CHARS);
	}

	private static String firstWords(String text) {
		String[] words = text.split("\\s+");
		return truncate(String.join(" ", Arrays.copyOf(words, Math.min(5, words.length))), MAX_TITLE_CHARS);
	}

	private static String truncate(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
	}

}
