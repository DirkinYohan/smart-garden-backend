package com.smartgarden.telemetry.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartgarden.telemetry.domain.DeviceMode;
import com.smartgarden.telemetry.domain.TelemetrySnapshot;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class TelemetryPayloadParserTest {

	private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

	private final TelemetryPayloadParser parser = new TelemetryPayloadParser(JsonMapper.builder().build());

	@Test
	void parsesContractPayload() {
		String json = """
				{"deviceId":"esp32-huerta-01","raw":2350,"moisture":45,"pumpOn":false,"sensorOk":true,
				 "mode":"AUTO","minMoisture":30,"maxMoisture":70,"rainPause":false,"uptimeMs":123456}
				""";

		TelemetrySnapshot s = parser.parse(json, NOW);

		assertThat(s.deviceId()).isEqualTo("esp32-huerta-01");
		assertThat(s.raw()).isEqualTo(2350);
		assertThat(s.moisture()).isEqualTo(45);
		assertThat(s.pumpOn()).isFalse();
		assertThat(s.sensorOk()).isTrue();
		assertThat(s.mode()).isEqualTo(DeviceMode.AUTO);
		assertThat(s.minMoisture()).isEqualTo(30);
		assertThat(s.maxMoisture()).isEqualTo(70);
		assertThat(s.rainPause()).isFalse();
		assertThat(s.uptimeMs()).isEqualTo(123456L);
		assertThat(s.receivedAt()).isEqualTo(NOW);
	}

	@Test
	void ignoresUnknownFieldsAndAcceptsLowercaseMode() {
		String json = """
				{"deviceId":"d1","raw":2000,"moisture":10,"pumpOn":true,"mode":"manual",
				 "minMoisture":20,"maxMoisture":60,"firmware":"1.2.3"}
				""";

		TelemetrySnapshot s = parser.parse(json, NOW);

		assertThat(s.mode()).isEqualTo(DeviceMode.MANUAL);
		assertThat(s.sensorOk()).isTrue();
		assertThat(s.rainPause()).isFalse();
		assertThat(s.uptimeMs()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "not json", "[1,2]", "{\"deviceId\":\"d1\"}",
			"{\"deviceId\":\"d1\",\"raw\":1,\"moisture\":150,\"pumpOn\":false,\"mode\":\"AUTO\",\"minMoisture\":30,\"maxMoisture\":70}",
			"{\"deviceId\":\"d1\",\"raw\":1,\"moisture\":50,\"pumpOn\":false,\"mode\":\"TURBO\",\"minMoisture\":30,\"maxMoisture\":70}",
			"{\"deviceId\":\"d1\",\"raw\":1,\"moisture\":50,\"pumpOn\":false,\"mode\":\"AUTO\",\"minMoisture\":80,\"maxMoisture\":70}",
			"{\"deviceId\":\"\",\"raw\":1,\"moisture\":50,\"pumpOn\":false,\"mode\":\"AUTO\",\"minMoisture\":30,\"maxMoisture\":70}",
			"{\"deviceId\":\"d1\",\"raw\":1,\"moisture\":\"abc\",\"pumpOn\":false,\"mode\":\"AUTO\",\"minMoisture\":30,\"maxMoisture\":70}" })
	void rejectsInvalidPayloads(String json) {
		assertThatThrownBy(() -> parser.parse(json, NOW)).isInstanceOf(InvalidPayloadException.class);
	}

	@Test
	void readingStuckAtAnAdcRailIsMarkedInvalid() {
		// Real ESP32 payload with the sensor disconnected: raw 4095 → moisture 0, but sensorOk true
		String disconnected = """
				{"deviceId":"esp32-huerta-01","raw":4095,"moisture":0,"pumpOn":false,"sensorOk":true,
				 "mode":"MANUAL","minMoisture":45,"maxMoisture":75,"rainPause":true,"uptimeMs":208179}
				""";
		String shorted = disconnected.replace("\"raw\":4095", "\"raw\":0").replace("\"moisture\":0", "\"moisture\":100");
		String normal = disconnected.replace("\"raw\":4095", "\"raw\":2479").replace("\"moisture\":0", "\"moisture\":62");

		assertThat(parser.parse(disconnected, NOW).sensorOk()).isFalse();
		assertThat(parser.parse(shorted, NOW).sensorOk()).isFalse();
		assertThat(parser.parse(normal, NOW).sensorOk()).isTrue();
		// near the rail (seen in production: raw 4089 reported as 0 %)
		assertThat(parser.parse(disconnected.replace("\"raw\":4095", "\"raw\":4089"), NOW).sensorOk()).isFalse();
		// dry soil is still a valid reading
		assertThat(parser.parse(normal.replace("\"raw\":2479", "\"raw\":3450"), NOW).sensorOk()).isTrue();
	}

	@Test
	void parsesStatusWithOrWithoutQuotes() {
		assertThat(TelemetryPayloadParser.parseStatus("\"online\"")).isEqualTo("online");
		assertThat(TelemetryPayloadParser.parseStatus(" offline ")).isEqualTo("offline");
		assertThatThrownBy(() -> TelemetryPayloadParser.parseStatus("rebooting"))
			.isInstanceOf(InvalidPayloadException.class);
	}

}
