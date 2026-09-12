package com.community.vax.repo;

import com.community.vax.entity.ScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, Long> {
    List<ScheduleTemplate> findByVaccineIdOrderByDoseNo(Long vaccineId);
    List<ScheduleTemplate> findAll();
}
