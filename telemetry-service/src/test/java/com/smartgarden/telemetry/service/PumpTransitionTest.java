package com.smartgarden.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PumpTransitionTest {

	@Test
	void falseToTrueStartsAnEvent() {
		assertThat(PumpTransition.detect(false, true)).isEqualTo(PumpTransition.STARTED);
	}

	@Test
	void trueToFalseStopsAnEvent() {
		assertThat(PumpTransition.detect(true, false)).isEqualTo(PumpTransition.STOPPED);
	}

	@Test
	void unchangedStateIsNoTransition() {
		assertThat(PumpTransition.detect(true, true)).isEqualTo(PumpTransition.NONE);
		assertThat(PumpTransition.detect(false, false)).isEqualTo(PumpTransition.NONE);
	}

	@Test
	void unknownPreviousStateOnlyStartsWhenPumpIsOn() {
		assertThat(PumpTransition.detect(null, true)).isEqualTo(PumpTransition.STARTED);
		assertThat(PumpTransition.detect(null, false)).isEqualTo(PumpTransition.NONE);
	}

}
