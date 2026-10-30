package com.smartgarden.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartgarden.telemetry.domain.DeviceMode;
import com.smartgarden.telemetry.domain.TelemetrySnapshot;
import com.smartgarden.telemetry.entity.IrrigationEventEntity;
import com.smartgarden.telemetry.repository.IrrigationEventRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IrrigationEventServiceTest {

	private static final Instant T0 = Instant.parse("2026-10-21T12:00:00Z");

	@Mock
	private IrrigationEventRepository repository;

	private IrrigationEventService service;

	@BeforeEach
	void setUp() {
		service = new IrrigationEventService(repository);
	}

	@Test
	void opensAndClosesAnEventFromPumpTransitions() {
		when(repository.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(anyString()))
			.thenReturn(Optional.empty());
		when(repository.save(any(IrrigationEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		assertThat(service.onReading(reading(40, false, 0))).isEqualTo(PumpTransition.NONE);
		assertThat(service.onReading(reading(28, true, 5))).isEqualTo(PumpTransition.STARTED);

		IrrigationEventEntity open = IrrigationEventEntity.open("esp32", T0.plusSeconds(5), 28, DeviceMode.AUTO);
		when(repository.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc("esp32"))
			.thenReturn(Optional.of(open));

		assertThat(service.onReading(reading(50, true, 10))).isEqualTo(PumpTransition.NONE);
		assertThat(service.onReading(reading(71, false, 65))).isEqualTo(PumpTransition.STOPPED);

		assertThat(open.isInProgress()).isFalse();
		assertThat(open.getDurationSec()).isEqualTo(60);
		assertThat(open.getStartMoisture()).isEqualTo(28);
		assertThat(open.getEndMoisture()).isEqualTo(71);
		verify(repository).save(any(IrrigationEventEntity.class));
	}

	@Test
	void openEventInDatabaseMeansPumpWasOnAfterRestart() {
		IrrigationEventEntity open = IrrigationEventEntity.open("esp32", T0, 25, DeviceMode.AUTO);
		when(repository.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc("esp32"))
			.thenReturn(Optional.of(open));

		assertThat(service.onReading(reading(30, true, 30))).isEqualTo(PumpTransition.NONE);
		verify(repository, never()).save(any());

		assertThat(service.onReading(reading(70, false, 90))).isEqualTo(PumpTransition.STOPPED);
		assertThat(open.getDurationSec()).isEqualTo(90);
	}

	private static TelemetrySnapshot reading(int moisture, boolean pumpOn, int secondsAfterT0) {
		return new TelemetrySnapshot("esp32", 2000, moisture, pumpOn, true, DeviceMode.AUTO, 30, 70, false, 1000,
				T0.plusSeconds(secondsAfterT0));
	}

}
