package com.community.vax.repo;

import com.community.vax.entity.ClinicCapacity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClinicCapacityRepository extends JpaRepository<ClinicCapacity, Long> {
    List<ClinicCapacity> findByClinicDateBetweenOrderByClinicDateAscTimeSlotAsc(LocalDate from, LocalDate to);
    Optional<ClinicCapacity> findByClinicDateAndTimeSlot(LocalDate date, String slot);
}
