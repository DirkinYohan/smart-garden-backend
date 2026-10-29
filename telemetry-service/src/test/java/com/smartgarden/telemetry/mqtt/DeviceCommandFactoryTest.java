package com.smartgarden.telemetry.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartgarden.contracts.common.v1.PumpAction;
import com.smartgarden.telemetry.domain.DeviceMode;
import com.smartgarden.telemetry.mqtt.DeviceCommandFactory.DeviceCommand;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeviceCommandFactoryTest {

	private final DeviceCommandFactory factory = new DeviceCommandFactory(JsonMapper.builder().build());

	@Test
	void configIsRetainedAndMatchesContract() {
		DeviceCommand cmd = factory.config(DeviceMode.AI, 35, 65, true);

		assertThat(cmd.retained()).isTrue();
		assertThat(cmd.payload())
			.isEqualTo("{\"type\":\"CONFIG\",\"mode\":\"AI\",\"minMoisture\":35,\"maxMoisture\":65,\"rainPause\":true}");
	}

	@Test
	void pumpCommandsAreNotRetained() {
		DeviceCommand on = factory.pump(PumpAction.PUMP_ACTION_ON, 30);
		DeviceCommand off = factory.pump(PumpAction.PUMP_ACTION_OFF, 0);

		assertThat(on.retained()).isFalse();
		assertThat(on.payload()).isEqualTo("{\"type\":\"PUMP\",\"action\":\"ON\",\"durationSec\":30}");
		assertThat(off.payload()).isEqualTo("{\"type\":\"PUMP\",\"action\":\"OFF\"}");
	}

	@Test
	void rejectsInvalidCommands() {
		assertThatThrownBy(() -> factory.pump(PumpAction.PUMP_ACTION_ON, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> factory.pump(PumpAction.PUMP_ACTION_ON, 121))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> factory.pump(PumpAction.PUMP_ACTION_UNSPECIFIED, 10))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> factory.config(DeviceMode.AUTO, 70, 30, false))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
