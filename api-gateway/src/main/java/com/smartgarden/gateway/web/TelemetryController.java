package com.smartgarden.gateway.web;

import com.smartgarden.gateway.dto.HistoryResponse;
import com.smartgarden.gateway.dto.IrrigationEventDto;
import com.smartgarden.gateway.dto.StatusResponse;
import com.smartgarden.gateway.grpc.GardenGrpcClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Telemetría")
public class TelemetryController {

	private final GardenGrpcClient grpc;

	public TelemetryController(GardenGrpcClient grpc) {
		this.grpc = grpc;
	}

	@GetMapping("/status")
	@Operation(summary = "Lectura actual del sensor y estado online/offline del ESP32")
	public StatusResponse status() {
		return grpc.status();
	}

	@GetMapping("/telemetry/history")
	@Operation(summary = "Serie agregada de humedad (24h: 5 min, 7d: 1 h, 30d: 6 h)")
	public HistoryResponse history(@RequestParam(defaultValue = "24h") @Pattern(regexp = "24h|7d|30d",
			message = "range debe ser 24h, 7d o 30d") String range) {
		return grpc.history(range);
	}

	@GetMapping("/irrigation/events")
	@Operation(summary = "Historial de riegos (más recientes primero)")
	public List<IrrigationEventDto> irrigationEvents(@RequestParam(defaultValue = "20") @Min(value = 1,
			message = "limit debe estar entre 1 y 200") @Max(value = 200,
					message = "limit debe estar entre 1 y 200") int limit) {
		return grpc.irrigationEvents(limit);
	}

}
