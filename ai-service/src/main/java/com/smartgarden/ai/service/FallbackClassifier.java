package com.smartgarden.ai.service;

import com.smartgarden.ai.domain.FallbackReason;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public final class FallbackClassifier {

	private static final Pattern SECRETS = Pattern.compile("AIza[0-9A-Za-z_\\-]{20,}|([?&]key=)[^&\\s\"]+");

	private static final int MAX_DETAIL = 300;

	private FallbackClassifier() {
	}

	public static FallbackReason classify(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof GeminiNotConfiguredException) {
				return FallbackReason.NO_API_KEY;
			}
			if (t instanceof TimeoutException || t instanceof SocketTimeoutException) {
				return FallbackReason.TIMEOUT;
			}
			if (t instanceof InvalidRecommendationException) {
				return FallbackReason.INVALID_RESPONSE;
			}
		}
		String text = allMessages(error).toLowerCase(Locale.ROOT);
		if (text.contains("429") || text.contains("resource_exhausted") || text.contains("quota")) {
			return FallbackReason.QUOTA_EXCEEDED;
		}
		if (text.contains("api key not valid") || text.contains("api_key_invalid") || text.contains("permission_denied")
				|| text.contains(" 401") || text.contains(" 403") || text.contains("unauthenticated")) {
			return FallbackReason.AUTH_ERROR;
		}
		if (text.contains(" 404") || text.contains("not_found") || text.contains("no longer available")
				|| text.contains("is not found")) {
			return FallbackReason.MODEL_NOT_FOUND;
		}
		if (text.contains(" 503") || text.contains("unavailable") || text.contains("high demand")
				|| text.contains("overloaded")) {
			return FallbackReason.UNAVAILABLE;
		}
		if (text.contains("timed out") || text.contains("timeout") || text.contains("deadline")) {
			return FallbackReason.TIMEOUT;
		}
		if (text.contains("parse") || text.contains("json") || text.contains("deserializ")
				|| text.contains("unrecognized")) {
			return FallbackReason.INVALID_RESPONSE;
		}
		return FallbackReason.ERROR;
	}

	public static String detail(Throwable error) {
		String text = SECRETS.matcher(allMessages(error)).replaceAll("$1***").replaceAll("\\s+", " ").trim();
		return text.length() <= MAX_DETAIL ? text : text.substring(0, MAX_DETAIL) + "…";
	}

	private static String allMessages(Throwable error) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
			String msg = t.getMessage();
			if (msg != null && !msg.isBlank() && sb.indexOf(msg) < 0) {
				if (!sb.isEmpty()) {
					sb.append(" | ");
				}
				sb.append(msg);
			}
			else if (msg == null && sb.isEmpty()) {
				sb.append(t.getClass().getSimpleName());
			}
		}
		return sb.toString();
	}

}
