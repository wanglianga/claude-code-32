package com.community.vax.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/** 医生排班 */
@Entity
@Table(name = "doctor_schedule")
public class DoctorSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long doctorUserId;

    @Column(nullable = false, length = 32)
    private String doctorName;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false, length = 16)
    private String timeSlot;

    @Column(nullable = false)
    private Boolean onDuty = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDoctorUserId() { return doctorUserId; }
    public void setDoctorUserId(Long doctorUserId) { this.doctorUserId = doctorUserId; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public Boolean getOnDuty() { return onDuty; }
    public void setOnDuty(Boolean onDuty) { this.onDuty = onDuty; }
}
