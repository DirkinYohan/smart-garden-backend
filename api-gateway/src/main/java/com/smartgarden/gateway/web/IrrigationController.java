package com.smartgarden.gateway.web;

import com.smartgarden.contracts.common.v1.PumpAction;
import com.smartgarden.gateway.dto.ConfigDto;
import com.smartgarden.gateway.dto.CropProfileDto;
import com.smartgarden.gateway.dto.CropProfileRequest;
import com.smartgarden.gateway.dto.PumpResponse;
import com.smartgarden.gateway.dto.PumpStartRequest;
import com.smartgarden.gateway.dto.UpdateConfigRequest;
import com.smartgarden.gateway.grpc.GardenGrpcClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Riego")
public class IrrigationController {

	private final GardenGrpcClient grpc;

	public IrrigationController(GardenGrpcClient grpc) {
		this.grpc = grpc;
	}

	@GetMapping("/config")
	@Operation(summary = "Configuración actual de riego")
	public ConfigDto config() {
		return grpc.config();
	}

	@PutMapping("/config")
	@Operation(summary = "Actualiza modo, cultivo, umbrales, rainPause o aiEnabled (solo los campos enviados)")
	public ConfigDto updateConfig(@Valid @RequestBody UpdateConfigRequest request) {
		return grpc.updateConfig(request);
	}

	@GetMapping("/crops")
	@Operation(summary = "Perfiles de cultivo")
	public List<CropProfileDto> crops() {
		return grpc.crops();
	}

	@PostMapping("/crops")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Crea un perfil de cultivo")
	public CropProfileDto createCrop(@Valid @RequestBody CropProfileRequest request) {
		return grpc.upsertCrop(0, request);
	}

	@PutMapping("/crops/{id}")
	@Operation(summary = "Edita un perfil de cultivo")
	public CropProfileDto updateCrop(@PathVariable @Positive(message = "id debe ser positivo") long id,
			@Valid @RequestBody CropProfileRequest request) {
		return grpc.upsertCrop(id, request);
	}

	@PostMapping("/pump/start")
	@Operation(summary = "Enciende la bomba (solo en modo MANUAL, máximo 120 s)")
	public PumpResponse startPump(@Valid @RequestBody PumpStartRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
		return grpc.pump(PumpAction.PUMP_ACTION_ON, request.durationSec(), idempotencyKey);
	}

	@PostMapping("/pump/stop")
	@Operation(summary = "Apaga la bomba (solo en modo MANUAL)")
	public PumpResponse stopPump(
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
		return grpc.pump(PumpAction.PUMP_ACTION_OFF, 0, idempotencyKey);
	}

}
