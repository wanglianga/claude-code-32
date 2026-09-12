package com.community.vax.repo;

import com.community.vax.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {
    List<Consultation> findByChildIdOrderByCreatedAtDesc(Long childId);
    List<Consultation> findByStatusOrderByCreatedAtDesc(String status);
    List<Consultation> findByChildIdAndPreVaccineAlertTrue(Long childId);
}
