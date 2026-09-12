package com.community.vax.repo;

import com.community.vax.entity.VaccineBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VaccineBatchRepository extends JpaRepository<VaccineBatch, Long> {
    List<VaccineBatch> findByVaccineCodeOrderByExpiryDateAsc(String vaccineCode);
    Optional<VaccineBatch> findByBatchNo(String batchNo);
    List<VaccineBatch> findAllByOrderByArrivalDateDesc();
}
