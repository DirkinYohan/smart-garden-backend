package com.smartgarden.irrigation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IrrigationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(IrrigationServiceApplication.class, args);
	}

}
