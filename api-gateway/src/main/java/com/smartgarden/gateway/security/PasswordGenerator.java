package com.smartgarden.gateway.security;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PasswordGenerator {

	public static final int LENGTH = 16;

	private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";

	private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";

	private static final String DIGITS = "23456789";

	private static final String SYMBOLS = "!@#$%&*?-_";

	private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		List<Character> chars = new ArrayList<>(LENGTH);
		chars.add(pick(UPPER));
		chars.add(pick(LOWER));
		chars.add(pick(DIGITS));
		chars.add(pick(SYMBOLS));
		while (chars.size() < LENGTH) {
			chars.add(pick(ALL));
		}
		Collections.shuffle(chars, random);
		StringBuilder sb = new StringBuilder(LENGTH);
		chars.forEach(sb::append);
		return sb.toString();
	}

	private char pick(String alphabet) {
		return alphabet.charAt(random.nextInt(alphabet.length()));
	}

}
