package com.smartgarden.common.thresholds;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CropThresholdClampTest {

	@Test
	void keepsMinimumGap() {
		int[] r = CropThresholdClamp.clamp(50, 40, 30, 70);

		assertThat(r[0]).isLessThan(r[1]);
		assertThat(r[1] - r[0]).isGreaterThanOrEqualTo(CropThresholdClamp.MIN_GAP);
	}

	@Test
	void staysWithinTenAndNinety() {
		assertThat(CropThresholdClamp.clamp(-5, 120, 0, 0)).containsExactly(10, 90);
	}

	@Test
	void respectsTheProfileMargin() {
		int[] r = CropThresholdClamp.clamp(10, 20, 60, 80);

		assertThat(r[0]).isGreaterThanOrEqualTo(50);
		assertThat(r[1]).isLessThanOrEqualTo(90);
	}

	@Test
	void theCropProfileChangesTheResult() {
		int[] tomate = CropThresholdClamp.clamp(70, 72, 60, 80);
		int[] calabaza = CropThresholdClamp.clamp(70, 72, 30, 40);

		assertThat(tomate).isNotEqualTo(calabaza);
		assertThat(tomate[0]).isGreaterThanOrEqualTo(50);
		assertThat(calabaza[1]).isLessThanOrEqualTo(50);
	}

}
