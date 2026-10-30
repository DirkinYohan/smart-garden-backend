package com.smartgarden.gateway.service;

import com.smartgarden.gateway.dto.MeResponse;
import com.smartgarden.gateway.entity.AppUserEntity;
import com.smartgarden.gateway.repository.AppUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserQueryService {

	private final AppUserRepository users;

	public UserQueryService(AppUserRepository users) {
		this.users = users;
	}

	public MeResponse me(String username) {
		AppUserEntity user = require(username);
		return new MeResponse(user.getUsername(), user.getRole(), user.isMustChangePassword(),
				user.getPasswordChangedAt());
	}

	public AppUserEntity require(String username) {
		return users.findByUsername(username)
			.orElseThrow(() -> new BadCredentialsException("La sesión ya no es válida"));
	}

}
