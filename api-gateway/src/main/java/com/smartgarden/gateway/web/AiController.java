package com.smartgarden.gateway.web;

import com.smartgarden.gateway.dto.AnalysisDto;
import com.smartgarden.gateway.dto.StatsDto;
import com.smartgarden.gateway.dto.WeatherDto;
import com.smartgarden.gateway.grpc.GardenGrpcClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "IA y clima")
public class AiController {

	private final GardenGrpcClient grpc;

	public AiController(GardenGrpcClient grpc) {
		this.grpc = grpc;
	}

	@GetMapping("/ai/analysis/latest")
	@Operation(summary = "Último análisis de la IA (404 si aún no hay)")
	public AnalysisDto latest() {
		return grpc.latestAnalysis();
	}

	@GetMapping("/ai/analysis")
	@Operation(summary = "Historial de análisis")
	public List<AnalysisDto> list(@RequestParam(defaultValue = "10") @Min(value = 1,
			message = "limit debe estar entre 1 y 100") @Max(value = 100,
					message = "limit debe estar entre 1 y 100") int limit) {
		return grpc.analyses(limit);
	}

	@PostMapping("/ai/analysis")
	@Operation(summary = "Solicita un análisis inmediato (máximo uno por minuto, 429 si se excede)")
	public AnalysisDto request() {
		return grpc.requestAnalysis();
	}

	@GetMapping("/ai/stats")
	@Operation(summary = "Estadísticas de la IA y humedad diaria de los últimos 7 o 30 días")
	public StatsDto stats(@RequestParam(defaultValue = "7") @Pattern(regexp = "7|30",
			message = "days debe ser 7 o 30") String days) {
		return grpc.stats(Integer.parseInt(days));
	}

	@GetMapping("/weather")
	@Operation(summary = "Clima actual y pronóstico de las próximas 12 h (Open-Meteo)")
	public WeatherDto weather() {
		return grpc.weather();
	}

}
