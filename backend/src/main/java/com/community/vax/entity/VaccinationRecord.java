package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 接种记录：到诊核验 → 接种 → 留观 的全过程结果 */
@Entity
@Table(name = "vaccination_record")
public class VaccinationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "appointment_id", unique = true)
    @JsonIgnoreProperties({"child", "plan"})
    private Appointment appointment;

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

    @Column(nullable = false, length = 64)
    private String batchNo;

    private LocalDate vaccineExpiryDate;

    @Column(nullable = false)
    private LocalDate vaccinationDate;

    private Long nurseUserId;
    @Column(length = 32)
    private String nurseName;

    private Long doctorUserId;
    @Column(length = 32)
    private String doctorName;

    // ---- 到诊核验（护士） ----
    @Column(nullable = false)
    private Boolean identityVerified = false;
    @Column(nullable = false)
    private Boolean batchVerified = false;
    @Column(nullable = false)
    private Boolean consentSigned = false;
    @Column(nullable = false)
    private Boolean recentFeverChecked = false;
    /** 近期是否发热 */
    @Column(nullable = false)
    private Boolean recentFever = false;
    @Column(nullable = false)
    private Boolean contraindicationChecked = false;

    @Column(length = 255)
    private String verifyNote;

    // ---- 接种 ----
    @Column(length = 32)
    private String injectionSite;

    /** 医生复核意见（禁忌/健康状态存疑时） */
    @Column(length = 500)
    private String doctorReviewNote;

    // ---- 留观 ----
    /** 要求留观分钟数 */
    private Integer observationMinutes = 30;
    private LocalDateTime observationStartTime;
    private LocalDateTime observationEndTime;

    /** 留观现场反应，如 无异常 / 局部红肿 / 皮疹 */
    @Column(length = 255)
    private String onSiteReaction = "无异常";

    @Column(nullable = false)
    private Boolean guardianConfirmed = false;

    /** COMPLETED 完成留观 / ABNORMAL 现场异常转随访 / WITHHELD 暂缓未接种 / OBSERVING 留观中 */
    @Column(nullable = false, length = 16)
    private String status = "COMPLETED";

    /** 换苗说明：同疫苗组替代产品时记录，如“IPV 缺货，使用同组 bOPV 替代” */
    @Column(length = 255)
    private String substitutionNote;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment a) { this.appointment = a; }
    public Child getChild() { return child; }
    public void setChild(Child child) { this.child = child; }
    public String getVaccineCode() { return vaccineCode; }
    public void setVaccineCode(String vaccineCode) { this.vaccineCode = vaccineCode; }
    public String getVaccineName() { return vaccineName; }
    public void setVaccineName(String vaccineName) { this.vaccineName = vaccineName; }
    public Integer getDoseNo() { return doseNo; }
    public void setDoseNo(Integer doseNo) { this.doseNo = doseNo; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public LocalDate getVaccineExpiryDate() { return vaccineExpiryDate; }
    public void setVaccineExpiryDate(LocalDate d) { this.vaccineExpiryDate = d; }
    public LocalDate getVaccinationDate() { return vaccinationDate; }
    public void setVaccinationDate(LocalDate d) { this.vaccinationDate = d; }
    public Long getNurseUserId() { return nurseUserId; }
    public void setNurseUserId(Long v) { this.nurseUserId = v; }
    public String getNurseName() { return nurseName; }
    public void setNurseName(String v) { this.nurseName = v; }
    public Long getDoctorUserId() { return doctorUserId; }
    public void setDoctorUserId(Long v) { this.doctorUserId = v; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String v) { this.doctorName = v; }
    public Boolean getIdentityVerified() { return identityVerified; }
    public void setIdentityVerified(Boolean v) { this.identityVerified = v; }
    public Boolean getBatchVerified() { return batchVerified; }
    public void setBatchVerified(Boolean v) { this.batchVerified = v; }
    public Boolean getConsentSigned() { return consentSigned; }
    public void setConsentSigned(Boolean v) { this.consentSigned = v; }
    public Boolean getRecentFeverChecked() { return recentFeverChecked; }
    public void setRecentFeverChecked(Boolean v) { this.recentFeverChecked = v; }
    public Boolean getRecentFever() { return recentFever; }
    public void setRecentFever(Boolean v) { this.recentFever = v; }
    public Boolean getContraindicationChecked() { return contraindicationChecked; }
    public void setContraindicationChecked(Boolean v) { this.contraindicationChecked = v; }
    public String getVerifyNote() { return verifyNote; }
    public void setVerifyNote(String v) { this.verifyNote = v; }
    public String getInjectionSite() { return injectionSite; }
    public void setInjectionSite(String v) { this.injectionSite = v; }
    public String getDoctorReviewNote() { return doctorReviewNote; }
    public void setDoctorReviewNote(String v) { this.doctorReviewNote = v; }
    public Integer getObservationMinutes() { return observationMinutes; }
    public void setObservationMinutes(Integer v) { this.observationMinutes = v; }
    public LocalDateTime getObservationStartTime() { return observationStartTime; }
    public void setObservationStartTime(LocalDateTime v) { this.observationStartTime = v; }
    public LocalDateTime getObservationEndTime() { return observationEndTime; }
    public void setObservationEndTime(LocalDateTime v) { this.observationEndTime = v; }
    public String getOnSiteReaction() { return onSiteReaction; }
    public void setOnSiteReaction(String v) { this.onSiteReaction = v; }
    public Boolean getGuardianConfirmed() { return guardianConfirmed; }
    public void setGuardianConfirmed(Boolean v) { this.guardianConfirmed = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getSubstitutionNote() { return substitutionNote; }
    public void setSubstitutionNote(String v) { this.substitutionNote = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
