package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 系统为儿童生成的接种/补种计划行 */
@Entity
@Table(name = "vaccination_plan",
        uniqueConstraints = @UniqueConstraint(columnNames = {"child_id", "vaccine_code", "dose_no"}))
public class VaccinationPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "child_id", nullable = false)
    @JsonIgnoreProperties({"guardian"})
    private Child child;

    @Column(nullable = false, length = 32)
    private String vaccineCode;

    @Column(nullable = false, length = 64)
    private String vaccineName;

    @Column(nullable = false)
    private Integer doseNo;

    /** DUE 应种 / DONE 已完成 / OVERDUE 漏种 / WAIT_INTERVAL 间隔未满 / WAIT_STOCK 等待库存 / CONTRA 禁忌暂缓 / EXPIRED 超龄不补种 / REVIEW 医生复核中 */
    @Column(nullable = false, length = 20)
    private String status;

    private LocalDate dueDate;

    /** 该剂最早可接种日期（满足最短间隔/起始月龄） */
    private LocalDate earliestDate;

    /** 年龄上限日期 */
    private LocalDate ageLimitDate;

    /** 已实际接种日期（完成时回填） */
    private LocalDate completedDate;

    /** 实际使用疫苗代码（可能换苗，与计划疫苗不同组同效） */
    @Column(length = 32)
    private String completedVaccineCode;

    @Column(length = 64)
    private String completedVaccineName;

    @Column(length = 64)
    private String completedBatchNo;

    @Column(length = 500)
    private String remark;

    /** 医生是否已复核同意（过敏/禁忌评估），计划重算时保留，不回退为待复核 */
    @Column(nullable = false)
    private Boolean doctorApproved = false;

    @Column(length = 255)
    private String doctorApprovedNote;

    /** 护士/医生接诊可见的剂次调整说明：为何跳过、追加、延后或换苗 */
    @Column(length = 500)
    private String adjustReason;

    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Child getChild() { return child; }
    public void setChild(Child child) { this.child = child; }
    public String getVaccineCode() { return vaccineCode; }
    public void setVaccineCode(String vaccineCode) { this.vaccineCode = vaccineCode; }
    public String getVaccineName() { return vaccineName; }
    public void setVaccineName(String vaccineName) { this.vaccineName = vaccineName; }
    public Integer getDoseNo() { return doseNo; }
    public void setDoseNo(Integer doseNo) { this.doseNo = doseNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getEarliestDate() { return earliestDate; }
    public void setEarliestDate(LocalDate earliestDate) { this.earliestDate = earliestDate; }
    public LocalDate getAgeLimitDate() { return ageLimitDate; }
    public void setAgeLimitDate(LocalDate ageLimitDate) { this.ageLimitDate = ageLimitDate; }
    public LocalDate getCompletedDate() { return completedDate; }
    public void setCompletedDate(LocalDate completedDate) { this.completedDate = completedDate; }
    public String getCompletedVaccineCode() { return completedVaccineCode; }
    public void setCompletedVaccineCode(String c) { this.completedVaccineCode = c; }
    public String getCompletedVaccineName() { return completedVaccineName; }
    public void setCompletedVaccineName(String n) { this.completedVaccineName = n; }
    public String getCompletedBatchNo() { return completedBatchNo; }
    public void setCompletedBatchNo(String b) { this.completedBatchNo = b; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getDoctorApproved() { return doctorApproved; }
    public void setDoctorApproved(Boolean v) { this.doctorApproved = v; }
    public String getDoctorApprovedNote() { return doctorApprovedNote; }
    public void setDoctorApprovedNote(String v) { this.doctorApprovedNote = v; }
    public String getAdjustReason() { return adjustReason; }
    public void setAdjustReason(String adjustReason) { this.adjustReason = adjustReason; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
