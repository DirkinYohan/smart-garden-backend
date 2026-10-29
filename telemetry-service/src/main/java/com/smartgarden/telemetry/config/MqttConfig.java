package com.smartgarden.telemetry.config;

import com.smartgarden.telemetry.mqtt.MqttInboundHandler;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * Spring Integration MQTT (Eclipse Paho v3): one inbound adapter for telemetry + status and one outbound handler for
 * commands. Paho reconnects automatically if the broker goes away.
 */
@Configuration
public class MqttConfig {

	private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

	@Bean
	MqttPahoClientFactory mqttClientFactory(MqttProperties props) {
		MqttConnectOptions options = new MqttConnectOptions();
		options.setServerURIs(new String[] { props.serverUri() });
		options.setAutomaticReconnect(true);
		options.setCleanSession(true);
		options.setConnectionTimeout(10);
		options.setKeepAliveInterval(30);
		if (props.hasCredentials()) {
			options.setUserName(props.username());
			options.setPassword(props.password() == null ? new char[0] : props.password().toCharArray());
		}
		DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
		factory.setConnectionOptions(options);
		log.info("MQTT broker: {}", props.serverUri());
		return factory;
	}

	@Bean
	MessageChannel mqttInboundChannel() {
		return new DirectChannel();
	}

	@Bean
	MessageProducer mqttInboundAdapter(MqttPahoClientFactory factory, MqttProperties props,
			MessageChannel mqttInboundChannel) {
		MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(props.clientId() + "-sub",
				factory, props.telemetryTopic(), props.statusTopic());
		adapter.setQos(props.qos(), props.qos());
		adapter.setConverter(new DefaultPahoMessageConverter());
		adapter.setCompletionTimeout(5000);
		adapter.setOutputChannel(mqttInboundChannel);
		return adapter;
	}

	@Bean
	@ServiceActivator(inputChannel = "mqttInboundChannel")
	MessageHandler mqttInboundMessageHandler(MqttInboundHandler handler) {
		return handler::handle;
	}

	@Bean
	MqttPahoMessageHandler mqttOutboundHandler(MqttPahoClientFactory factory, MqttProperties props) {
		MqttPahoMessageHandler handler = new MqttPahoMessageHandler(props.clientId() + "-pub", factory);
		handler.setAsync(false);
		handler.setDefaultTopic(props.commandsTopic());
		handler.setDefaultQos(props.qos());
		handler.setCompletionTimeout(5000);
		return handler;
	}

}
