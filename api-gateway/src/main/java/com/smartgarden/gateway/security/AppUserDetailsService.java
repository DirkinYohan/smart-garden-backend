package com.smartgarden.gateway.security;

import com.smartgarden.gateway.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository users;

	public AppUserDetailsService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		return users.findByUsername(username)
			.map(u -> User.withUsername(u.getUsername())
				.password(u.getPasswordHash())
				.roles(u.getRole())
				.disabled(!u.isEnabled())
				.build())
			.orElseThrow(() -> new UsernameNotFoundException(username));
	}

}
