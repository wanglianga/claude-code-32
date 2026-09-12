package com.community.vax.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/** 门诊日容量（按接种日） */
@Entity
@Table(name = "clinic_capacity")
public class ClinicCapacity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate clinicDate;

    /** 时段，如 08:30-09:00 */
    @Column(nullable = false, length = 16)
    private String timeSlot;

    @Column(nullable = false)
    private Integer maxCapacity;

    /** 已预约数（预约/取消时维护） */
    @Column(nullable = false)
    private Integer bookedCount = 0;

    @Column(nullable = false)
    private Boolean open = true;

    @Version
    private Long version;

    @Transient
    public int getRemaining() { return Math.max(0, maxCapacity - bookedCount); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getClinicDate() { return clinicDate; }
    public void setClinicDate(LocalDate clinicDate) { this.clinicDate = clinicDate; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }
    public Integer getBookedCount() { return bookedCount; }
    public void setBookedCount(Integer bookedCount) { this.bookedCount = bookedCount; }
    public Boolean getOpen() { return open; }
    public void setOpen(Boolean open) { this.open = open; }
}
