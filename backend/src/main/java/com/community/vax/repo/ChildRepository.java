package com.community.vax.repo;

import com.community.vax.entity.Child;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChildRepository extends JpaRepository<Child, Long> {
    List<Child> findByGuardianIdOrderByBirthDateDesc(Long guardianId);
    List<Child> findAllByOrderByCreatedAtDesc();
}
