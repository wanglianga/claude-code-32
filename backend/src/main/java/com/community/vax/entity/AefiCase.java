package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 疑似预防接种异常反应（AEFI）个案 */
@Entity
@Table(name = "aefi_case")
public class AefiCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "record_id")
    @JsonIgnoreProperties({"child", "appointment"})
    private VaccinationRecord record;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "child_id", nullable = false)
    @JsonIgnoreProperties({"guardian"})
    private Child child;

    @Column(nullable = false, length = 32)
    private String vaccineCode;
    @Column(nullable = false, length = 64)
    private String vaccineName;
    /** 关联接种批号 */
    @Column(nullable = false, length = 64)
    private String batchNo;
    @Column(nullable = false)
    private Integer doseNo;
    @Column(nullable = false)
    private LocalDate vaccinationDate;

    /** 症状，如 发热、皮疹、局部红肿、过敏性皮疹 */
    @Column(nullable = false, length = 500)
    private String symptoms;

    private LocalDate onsetDate;

    @Column(length = 500)
    private String symptomDetail;

    /** 就医记录 */
    @Column(length = 500)
    private String medicalRecord;

    @Column(length = 128)
    private String hospital;

    /** OPEN 待随访 / FOLLOWING 随访中 / REPORTED 已区级上报 / CLOSED 已结案 */
    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    /** 最终判断：一般反应 / 异常反应 / 偶合症 / 心因性反应 / 不能排除 / 无因果关系 */
    @Column(length = 64)
    private String finalConclusion;

    @Column(length = 500)
    private String conclusionNote;

    /** 是否区级上报 */
    @Column(nullable = false)
    private Boolean districtReported = false;
    private LocalDateTime reportedAt;

    /** 报告来源：现场留观 / 家长咨询 / 随访发现 */
    @Column(length = 32)
    private String source = "现场留观";

    private Long createdBy;
    @Column(length = 32)
    private String createdByName;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public VaccinationRecord getRecord() { return record; }
    public void setRecord(VaccinationRecord r) { this.record = r; }
    public Child getChild() { return child; }
    public void setChild(Child child) { this.child = child; }
    public String getVaccineCode() { return vaccineCode; }
    public void setVaccineCode(String v) { this.vaccineCode = v; }
    public String getVaccineName() { return vaccineName; }
    public void setVaccineName(String v) { this.vaccineName = v; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String b) { this.batchNo = b; }
    public Integer getDoseNo() { return doseNo; }
    public void setDoseNo(Integer d) { this.doseNo = d; }
    public LocalDate getVaccinationDate() { return vaccinationDate; }
    public void setVaccinationDate(LocalDate d) { this.vaccinationDate = d; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String s) { this.symptoms = s; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public void setOnsetDate(LocalDate d) { this.onsetDate = d; }
    public String getSymptomDetail() { return symptomDetail; }
    public void setSymptomDetail(String s) { this.symptomDetail = s; }
    public String getMedicalRecord() { return medicalRecord; }
    public void setMedicalRecord(String m) { this.medicalRecord = m; }
    public String getHospital() { return hospital; }
    public void setHospital(String h) { this.hospital = h; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public String getFinalConclusion() { return finalConclusion; }
    public void setFinalConclusion(String c) { this.finalConclusion = c; }
    public String getConclusionNote() { return conclusionNote; }
    public void setConclusionNote(String c) { this.conclusionNote = c; }
    public Boolean getDistrictReported() { return districtReported; }
    public void setDistrictReported(Boolean b) { this.districtReported = b; }
    public LocalDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(LocalDateTime t) { this.reportedAt = t; }
    public String getSource() { return source; }
    public void setSource(String s) { this.source = s; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String v) { this.createdByName = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
