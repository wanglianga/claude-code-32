package com.community.vax.repo;

import com.community.vax.entity.MigrationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MigrationDocumentRepository extends JpaRepository<MigrationDocument, Long> {
    List<MigrationDocument> findByChildIdOrderByCreatedAtDesc(Long childId);
}
