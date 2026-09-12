package com.community.vax.repo;

import com.community.vax.entity.Contraindication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContraindicationRepository extends JpaRepository<Contraindication, Long> {
    List<Contraindication> findByChildIdAndActiveTrue(Long childId);
}
