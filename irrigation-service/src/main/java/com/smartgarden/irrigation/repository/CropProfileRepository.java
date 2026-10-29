package com.smartgarden.irrigation.repository;

import com.smartgarden.irrigation.entity.CropProfileEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropProfileRepository extends JpaRepository<CropProfileEntity, Long> {

	List<CropProfileEntity> findAllByOrderByNameAsc();

	Optional<CropProfileEntity> findByNameIgnoreCase(String name);

}
