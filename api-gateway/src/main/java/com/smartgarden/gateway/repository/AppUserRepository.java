package com.smartgarden.gateway.repository;

import com.smartgarden.gateway.entity.AppUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

	Optional<AppUserEntity> findByUsername(String username);

	boolean existsByUsername(String username);

}
