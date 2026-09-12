package com.community.vax.repo;

import com.community.vax.entity.AefiCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AefiCaseRepository extends JpaRepository<AefiCase, Long> {
    List<AefiCase> findByChildIdOrderByCreatedAtDesc(Long childId);
    List<AefiCase> findByBatchNo(String batchNo);
    List<AefiCase> findByVaccineCode(String vaccineCode);
    List<AefiCase> findByStatusNotOrderByCreatedAtDesc(String status);
}
