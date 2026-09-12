package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** AEFI 随访记录 */
@Entity
@Table(name = "follow_up")
public class FollowUp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "aefi_id", nullable = false)
    @JsonIgnoreProperties({"child", "record"})
    private AefiCase aefi;

    @Column(nullable = false)
    private LocalDate followDate;

    /** PHONE 电话随访 / ONSITE 门诊随访 / HOME 入户随访 */
    @Column(nullable = false, length = 16)
    private String method = "PHONE";

    /** 当前体温，有发热时填写 */
    private Double temperature;

    @Column(length = 500)
    private String symptomsStatus;

    @Column(length = 500)
    private String advice;

    /** RESOLVED 已好转 / ONGOING 持续 / WORSENED 加重要就医 / HOSPITALIZED 已住院 */
    @Column(nullable = false, length = 16)
    private String outcome = "ONGOING";

    private Long followUserId;
    @Column(length = 32)
    private String followUserName;

    /** 下次随访日期，系统据此生成提醒 */
    private LocalDate nextFollowDate;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AefiCase getAefi() { return aefi; }
    public void setAefi(AefiCase aefi) { this.aefi = aefi; }
    public LocalDate getFollowDate() { return followDate; }
    public void setFollowDate(LocalDate d) { this.followDate = d; }
    public String getMethod() { return method; }
    public void setMethod(String m) { this.method = m; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double t) { this.temperature = t; }
    public String getSymptomsStatus() { return symptomsStatus; }
    public void setSymptomsStatus(String s) { this.symptomsStatus = s; }
    public String getAdvice() { return advice; }
    public void setAdvice(String a) { this.advice = a; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String o) { this.outcome = o; }
    public Long getFollowUserId() { return followUserId; }
    public void setFollowUserId(Long v) { this.followUserId = v; }
    public String getFollowUserName() { return followUserName; }
    public void setFollowUserName(String v) { this.followUserName = v; }
    public LocalDate getNextFollowDate() { return nextFollowDate; }
    public void setNextFollowDate(LocalDate d) { this.nextFollowDate = d; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
