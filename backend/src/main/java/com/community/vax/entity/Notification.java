package com.community.vax.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 平台状态推送/提醒。
 * 类型涵盖：漏种、迁入记录不全、库存不足、家长取消、接种后发热皮疹、
 * 补种提醒（间隔/年龄/到货）、随访到期、咨询待回复、接种前咨询要点。
 */
@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PARENT / DOCTOR / NURSE / FOLLOWUP / ADMIN */
    @Column(nullable = false, length = 16)
    private String targetRole;

    /** 具体接收人用户 ID；为空表示该角色全员 */
    private Long targetUserId;

    /** OVERDUE 漏种 / MIGRATION 迁入记录不全 / STOCK 库存不足 / CANCEL 家长取消
     *  / AEFI 接种后异常 / REMIND 补种提醒 / FOLLOWUP 随访到期 / CONSULT 咨询待回复
     *  / PREVAX 接种前提醒 / INFO 一般通知 */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    private Long childId;
    @Column(length = 32)
    private String childName;

    private Long refId;
    @Column(length = 32)
    private String refType;

    /** INFO / WARN / URGENT */
    @Column(nullable = false, length = 8)
    private String level = "INFO";

    @Column(nullable = false)
    private Boolean isRead = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String t) { this.targetRole = t; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long v) { this.targetUserId = v; }
    public String getType() { return type; }
    public void setType(String t) { this.type = t; }
    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }
    public String getContent() { return content; }
    public void setContent(String c) { this.content = c; }
    public Long getChildId() { return childId; }
    public void setChildId(Long v) { this.childId = v; }
    public String getChildName() { return childName; }
    public void setChildName(String v) { this.childName = v; }
    public Long getRefId() { return refId; }
    public void setRefId(Long v) { this.refId = v; }
    public String getRefType() { return refType; }
    public void setRefType(String v) { this.refType = v; }
    public String getLevel() { return level; }
    public void setLevel(String v) { this.level = v; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean v) { this.isRead = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
