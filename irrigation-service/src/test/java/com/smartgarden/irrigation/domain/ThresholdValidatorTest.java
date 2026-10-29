package com.smartgarden.irrigation.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThresholdValidatorTest {

	@ParameterizedTest
	@CsvSource({ "0,10", "30,70", "40,50", "90,100", "0,100" })
	void acceptsValidThresholds(int min, int max) {
		assertThatCode(() -> ThresholdValidator.validate(min, max)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@CsvSource({ "-1,50", "30,101", "70,30", "50,50", "40,49", "95,100", "101,120" })
	void rejectsInvalidThresholds(int min, int max) {
		assertThatThrownBy(() -> ThresholdValidator.validate(min, max)).isInstanceOf(IllegalArgumentException.class);
	}

}
