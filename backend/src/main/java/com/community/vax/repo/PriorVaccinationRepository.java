package com.community.vax.repo;

import com.community.vax.entity.PriorVaccination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriorVaccinationRepository extends JpaRepository<PriorVaccination, Long> {
    List<PriorVaccination> findByChildIdOrderByVaccinationDateAsc(Long childId);
    List<PriorVaccination> findByVerifyStatus(String verifyStatus);
    List<PriorVaccination> findByChildIdAndVerifyStatusInOrderByVaccinationDateAsc(Long childId, List<String> statuses);
    long countByVerifyStatus(String verifyStatus);
}
