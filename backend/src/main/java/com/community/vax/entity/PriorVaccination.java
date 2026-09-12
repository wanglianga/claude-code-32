package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;

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

    @Column(nullable = false, length = 32)
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

    /** 迁入记录是否材料齐全（接种证/信息系统可查） */
    @Column(nullable = false)
    private Boolean verified = false;

    @Column(length = 255)
    private String note;

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
    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
