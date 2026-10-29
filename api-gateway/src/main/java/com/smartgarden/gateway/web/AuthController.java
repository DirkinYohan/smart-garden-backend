package com.smartgarden.gateway.web;

import com.smartgarden.gateway.dto.ChangePasswordRequest;
import com.smartgarden.gateway.dto.LoginRequest;
import com.smartgarden.gateway.dto.LoginResponse;
import com.smartgarden.gateway.dto.MeResponse;
import com.smartgarden.gateway.dto.TokenResponse;
import com.smartgarden.gateway.security.JwtService.IssuedToken;
import com.smartgarden.gateway.security.LoginService;
import com.smartgarden.gateway.security.PasswordService;
import com.smartgarden.gateway.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private final LoginService loginService;

	private final PasswordService passwordService;

	private final UserQueryService userQueries;

	public AuthController(LoginService loginService, PasswordService passwordService, UserQueryService userQueries) {
		this.loginService = loginService;
		this.passwordService = passwordService;
		this.userQueries = userQueries;
	}

	@PostMapping("/login")
	@SecurityRequirements
	@Operation(summary = "Inicia sesión y devuelve un JWT (423 si la cuenta está bloqueada)")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		LoginService.LoginResult result = loginService.login(request.username(), request.password());
		log.info("User '{}' logged in (mustChangePassword={})", result.username(), result.mustChangePassword());
		return new LoginResponse(result.token().token(), result.token().expiresAt(), result.mustChangePassword(),
				result.username());
	}

	@GetMapping("/me")
	@Operation(summary = "Usuario autenticado y si debe cambiar la contraseña")
	public MeResponse me(Principal principal) {
		return userQueries.me(principal.getName());
	}

	@PutMapping("/password")
	@Operation(summary = "Cambia la contraseña y devuelve un token nuevo (cierra las demás sesiones)")
	public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, Principal principal) {
		IssuedToken token = passwordService.changePassword(principal.getName(), request.currentPassword(),
				request.newPassword());
		return new TokenResponse(token.token(), token.expiresAt());
	}

}
