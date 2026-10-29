package com.smartgarden.gateway.dto;

import java.util.List;

public record HistoryResponse(String range, int intervalMinutes, List<HistoryPointDto> points) {
}
