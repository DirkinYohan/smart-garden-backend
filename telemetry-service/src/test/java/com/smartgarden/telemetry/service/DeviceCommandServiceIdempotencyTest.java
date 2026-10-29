package com.smartgarden.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartgarden.contracts.common.v1.PumpAction;
import com.smartgarden.telemetry.mqtt.DeviceCommandFactory;
import com.smartgarden.telemetry.mqtt.MqttCommandPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeviceCommandServiceIdempotencyTest {

	private MqttCommandPublisher publisher;

	private DeviceCommandService service;

	@BeforeEach
	void setUp() {
		publisher = mock(MqttCommandPublisher.class);
		when(publisher.publish(any())).thenReturn("smartgarden/esp32/commands");
		service = new DeviceCommandService(
				new DeviceCommandFactory(tools.jackson.databind.json.JsonMapper.builder().build()), publisher);
	}

	@Test
	void sameRequestIdPublishesOnce() {
		service.sendPump(PumpAction.PUMP_ACTION_ON, 30, "abc-123");
		service.sendPump(PumpAction.PUMP_ACTION_ON, 30, "abc-123");

		verify(publisher, times(1)).publish(any());
	}

	@Test
	void differentRequestIdsPublishBoth() {
		service.sendPump(PumpAction.PUMP_ACTION_ON, 30, "uno");
		service.sendPump(PumpAction.PUMP_ACTION_ON, 30, "dos");

		verify(publisher, times(2)).publish(any());
	}

	@Test
	void withoutRequestIdAlwaysPublishes() {
		service.sendPump(PumpAction.PUMP_ACTION_ON, 30);
		service.sendPump(PumpAction.PUMP_ACTION_ON, 30);

		verify(publisher, times(2)).publish(any());
	}

	@Test
	void duplicateReturnsTheAlreadySentCommand() {
		var first = service.sendPump(PumpAction.PUMP_ACTION_ON, 30, "dup");
		var second = service.sendPump(PumpAction.PUMP_ACTION_ON, 30, "dup");

		assertThat(second).isEqualTo(first);
	}

}
