package com.smartgarden.gateway.dto;

import jakarta.validation.constraints.NotBlank;

/** PUT /auth/password. La política completa se valida en el servidor. */
public record ChangePasswordRequest(@NotBlank(message = "La contraseña actual es obligatoria") String currentPassword,
		@NotBlank(message = "La contraseña nueva es obligatoria") String newPassword) {
}
