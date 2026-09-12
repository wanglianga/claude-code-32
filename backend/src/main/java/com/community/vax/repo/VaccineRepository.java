package com.community.vax.repo;

import com.community.vax.entity.Vaccine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VaccineRepository extends JpaRepository<Vaccine, Long> {
    Optional<Vaccine> findByCode(String code);
    List<Vaccine> findByVaccineGroup(String group);
}
