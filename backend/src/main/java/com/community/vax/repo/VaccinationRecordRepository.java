package com.community.vax.repo;

import com.community.vax.entity.VaccinationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VaccinationRecordRepository extends JpaRepository<VaccinationRecord, Long> {
    List<VaccinationRecord> findByChildIdOrderByVaccinationDateDesc(Long childId);
    List<VaccinationRecord> findByBatchNo(String batchNo);
    Optional<VaccinationRecord> findByAppointmentId(Long appointmentId);
}
