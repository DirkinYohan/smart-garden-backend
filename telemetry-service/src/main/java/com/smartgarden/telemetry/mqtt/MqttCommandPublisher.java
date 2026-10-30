package com.smartgarden.telemetry.mqtt;

import com.smartgarden.telemetry.config.MqttProperties;
import com.smartgarden.telemetry.mqtt.DeviceCommandFactory.DeviceCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

@Component
public class MqttCommandPublisher {

	private static final Logger log = LoggerFactory.getLogger(MqttCommandPublisher.class);

	private final MqttPahoMessageHandler outbound;

	private final MqttProperties props;

	public MqttCommandPublisher(MqttPahoMessageHandler outbound, MqttProperties props) {
		this.outbound = outbound;
		this.props = props;
	}

	public String publish(DeviceCommand command) {
		outbound.handleMessage(MessageBuilder.withPayload(command.payload())
			.setHeader(MqttHeaders.TOPIC, props.commandsTopic())
			.setHeader(MqttHeaders.RETAINED, command.retained())
			.setHeader(MqttHeaders.QOS, props.qos())
			.build());
		log.info("Published command to {} (retained={}): {}", props.commandsTopic(), command.retained(),
				command.payload());
		return props.commandsTopic();
	}

}
