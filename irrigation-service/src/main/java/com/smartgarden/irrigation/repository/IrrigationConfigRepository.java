package com.smartgarden.irrigation.repository;

import com.smartgarden.irrigation.entity.IrrigationConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IrrigationConfigRepository extends JpaRepository<IrrigationConfigEntity, Long> {

}
