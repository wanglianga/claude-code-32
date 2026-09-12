package com.community.vax.repo;

import com.community.vax.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    List<DoctorSchedule> findByWorkDateAndTimeSlotAndOnDutyTrue(LocalDate date, String slot);
    List<DoctorSchedule> findByWorkDateBetweenOrderByWorkDateAsc(LocalDate from, LocalDate to);
}
