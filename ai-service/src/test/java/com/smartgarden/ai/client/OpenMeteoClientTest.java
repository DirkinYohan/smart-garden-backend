package com.smartgarden.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgarden.ai.client.OpenMeteoClient.CurrentBlock;
import com.smartgarden.ai.client.OpenMeteoClient.HourlyBlock;
import com.smartgarden.ai.client.OpenMeteoClient.OpenMeteoResponse;
import com.smartgarden.ai.domain.WeatherSnapshot;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenMeteoClientTest {

	@Test
	void computesMaxRainProbabilityAndTotalPrecipitation() {
		OpenMeteoResponse response = new OpenMeteoResponse(4.71, -74.07,
				new CurrentBlock(1_792_612_800L, 16.7, 68.0, 0.0, 3),
				new HourlyBlock(List.of(1_792_612_800L, 1_792_616_400L, 1_792_620_000L), List.of(16.7, 16.3, 15.7),
						List.of(68.0, 70.0, 75.0), Arrays.asList(20.0, null, 85.0), List.of(0.0, 0.4, 1.25)));

		WeatherSnapshot w = OpenMeteoClient.toSnapshot(response, Instant.EPOCH);

		assertThat(w.maxRainProbability12h()).isEqualTo(85.0);
		assertThat(w.totalPrecipitation12h()).isEqualTo(1.7);
		assertThat(w.hourly()).hasSize(3);
		assertThat(w.hourly().get(1).precipitationProbability()).isZero();
		assertThat(w.current().temperature()).isEqualTo(16.7);
		assertThat(w.current().time()).isEqualTo(Instant.ofEpochSecond(1_792_612_800L));
	}

}
