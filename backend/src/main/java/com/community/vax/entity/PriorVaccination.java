package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 既往接种记录（含外地/迁入前接种，来源 LOCAL 本地 / MIGRATED 迁入登记） */
@Entity
@Table(name = "prior_vaccination")
public class PriorVaccination {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "child_id", nullable = false)
    @JsonIgnoreProperties({"guardian"})
    private Child child;

    /** 疫苗代码；OCR 无法识别时允许为空，由人工核验纠正 */
    @Column(length = 32)
    private String vaccineCode;

    @Column(nullable = false, length = 64)
    private String vaccineName;

    @Column(nullable = false)
    private Integer doseNo;

    @Column(nullable = false)
    private LocalDate vaccinationDate;

    @Column(length = 64)
    private String batchNo;

    @Column(length = 128)
    private String clinicName;

    /** LOCAL 本门诊 / MIGRATED 迁入记录 */
    @Column(nullable = false, length = 16)
    private String source = "MIGRATED";

    /**
     * 迁入核验状态：
     * UNVERIFIED 待核验 / CONFIRMED 核验通过 / AMBIGUOUS 模糊待人工 / REJECTED 不予采信
     */
    @Column(nullable = false, length = 16)
    private String verifyStatus = "UNVERIFIED";

    /** OCR/登记置信度 0~1，低于 0.7 自动进入模糊队列 */
    private Double confidence;

    /** 来源接种本 */
    private Long migrationDocId;

    @Column(length = 255)
    private String reviewNote;

    @Column(length = 32)
    private String reviewedByName;

    private LocalDateTime reviewedAt;

    @Column(length = 255)
    private String note;

    /** 兼容旧字段：核验通过 = CONFIRMED */
    public Boolean getVerified() {
        return "CONFIRMED".equals(verifyStatus);
    }

    public void setVerified(Boolean v) {
        this.verifyStatus = Boolean.TRUE.equals(v) ? "CONFIRMED" : "UNVERIFIED";
    }

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
    public LocalDate getVaccinationDate() { return vaccinationDate; }
    public void setVaccinationDate(LocalDate vaccinationDate) { this.vaccinationDate = vaccinationDate; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getClinicName() { return clinicName; }
    public void setClinicName(String clinicName) { this.clinicName = clinicName; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Long getMigrationDocId() { return migrationDocId; }
    public void setMigrationDocId(Long migrationDocId) { this.migrationDocId = migrationDocId; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String v) { this.reviewedByName = v; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime v) { this.reviewedAt = v; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
