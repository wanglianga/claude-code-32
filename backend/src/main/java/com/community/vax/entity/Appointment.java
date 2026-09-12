package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointment")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "child_id", nullable = false)
    @JsonIgnoreProperties({"guardian"})
    private Child child;

    /** 预约的计划行 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id")
    @JsonIgnoreProperties({"child"})
    private VaccinationPlan plan;

    @Column(nullable = false, length = 32)
    private String vaccineCode;

    @Column(nullable = false, length = 64)
    private String vaccineName;

    @Column(nullable = false)
    private Integer doseNo;

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false, length = 16)
    private String timeSlot;

    private Long capacityId;

    private Long doctorUserId;

    @Column(length = 32)
    private String doctorName;

    /** 预留批号（可为空，到诊时由护士确认实际批号） */
    @Column(length = 64)
    private String reservedBatchNo;

    /** BOOKED 已预约 / CANCELLED 家长取消 / CANCELLED_CLINIC 门诊取消 / CHECKED_IN 已到诊 / VACCINATED 已接种 / NO_SHOW 爽约 */
    @Column(nullable = false, length = 20)
    private String status = "BOOKED";

    @Column(length = 255)
    private String cancelReason;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Child getChild() { return child; }
    public void setChild(Child child) { this.child = child; }
    public VaccinationPlan getPlan() { return plan; }
    public void setPlan(VaccinationPlan plan) { this.plan = plan; }
    public String getVaccineCode() { return vaccineCode; }
    public void setVaccineCode(String vaccineCode) { this.vaccineCode = vaccineCode; }
    public String getVaccineName() { return vaccineName; }
    public void setVaccineName(String vaccineName) { this.vaccineName = vaccineName; }
    public Integer getDoseNo() { return doseNo; }
    public void setDoseNo(Integer doseNo) { this.doseNo = doseNo; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate d) { this.appointmentDate = d; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public Long getCapacityId() { return capacityId; }
    public void setCapacityId(Long capacityId) { this.capacityId = capacityId; }
    public Long getDoctorUserId() { return doctorUserId; }
    public void setDoctorUserId(Long doctorUserId) { this.doctorUserId = doctorUserId; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getReservedBatchNo() { return reservedBatchNo; }
    public void setReservedBatchNo(String b) { this.reservedBatchNo = b; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
