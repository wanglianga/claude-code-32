package com.community.vax.repo;

import com.community.vax.entity.FollowUp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {
    List<FollowUp> findByAefiIdOrderByFollowDateAsc(Long aefiId);
    List<FollowUp> findByNextFollowDateLessThanEqualAndAefiStatusNot(LocalDate date, String status);
}
