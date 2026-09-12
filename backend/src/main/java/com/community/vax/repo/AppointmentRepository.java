package com.community.vax.repo;

import com.community.vax.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByChildIdOrderByAppointmentDateDesc(Long childId);
    List<Appointment> findByAppointmentDateAndStatus(LocalDate date, String status);
    List<Appointment> findByAppointmentDateBetweenAndStatusIn(LocalDate from, LocalDate to, List<String> statuses);
    List<Appointment> findByStatusIn(List<String> statuses);
    List<Appointment> findByPlanIdAndStatusIn(Long planId, List<String> statuses);
}
