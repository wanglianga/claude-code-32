package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 家长咨询记录；要点会进入该儿童下次接种前提醒 */
@Entity
@Table(name = "consultation")
public class Consultation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "child_id", nullable = false)
    @JsonIgnoreProperties({"guardian"})
    private Child child;

    @Column(nullable = false, length = 64)
    private String topic;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(length = 1000)
    private String reply;

    @Column(length = 32)
    private String repliedByName;

    /** 回复人角色：医生 / 随访人员 / 管理员 */
    @Column(length = 16)
    private String repliedByRole;

    /** 是否需要在下次接种前提醒医护 */
    @Column(nullable = false)
    private Boolean preVaccineAlert = false;

    @Column(length = 500)
    private String alertNote;

    /** OPEN 待回复 / REPLIED 已回复 */
    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime repliedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Child getChild() { return child; }
    public void setChild(Child child) { this.child = child; }
    public String getTopic() { return topic; }
    public void setTopic(String t) { this.topic = t; }
    public String getQuestion() { return question; }
    public void setQuestion(String q) { this.question = q; }
    public String getReply() { return reply; }
    public void setReply(String r) { this.reply = r; }
    public String getRepliedByName() { return repliedByName; }
    public void setRepliedByName(String v) { this.repliedByName = v; }
    public String getRepliedByRole() { return repliedByRole; }
    public void setRepliedByRole(String v) { this.repliedByRole = v; }
    public Boolean getPreVaccineAlert() { return preVaccineAlert; }
    public void setPreVaccineAlert(Boolean v) { this.preVaccineAlert = v; }
    public String getAlertNote() { return alertNote; }
    public void setAlertNote(String v) { this.alertNote = v; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getRepliedAt() { return repliedAt; }
    public void setRepliedAt(LocalDateTime v) { this.repliedAt = v; }
}
