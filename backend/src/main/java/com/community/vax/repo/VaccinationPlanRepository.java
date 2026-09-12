package com.community.vax.repo;

import com.community.vax.entity.VaccinationPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VaccinationPlanRepository extends JpaRepository<VaccinationPlan, Long> {
    List<VaccinationPlan> findByChildIdOrderByDueDateAsc(Long childId);
    Optional<VaccinationPlan> findByChildIdAndVaccineCodeAndDoseNo(Long childId, String code, Integer doseNo);
    List<VaccinationPlan> findByStatus(String status);
    List<VaccinationPlan> findByChildIdAndStatus(Long childId, String status);
}
