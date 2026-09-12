package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "child")
public class Child {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    /** M 男 / F 女 */
    @Column(nullable = false, length = 2)
    private String gender;

    @Column(nullable = false)
    private LocalDate birthDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "guardian_id")
    @JsonIgnoreProperties({"userId"})
    private ParentProfile guardian;

    @Column(length = 32)
    private String idCardNo;

    /** 迁入本社区日期（本地出生儿童可为空） */
    private LocalDate moveInDate;

    @Column(length = 500)
    private String migrationNote;

    /** 近期健康状态描述，如“2 天前发热 38.5℃，已退热” */
    @Column(length = 500)
    private String healthStatus;

    private LocalDateTime healthUpdatedAt;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public ParentProfile getGuardian() { return guardian; }
    public void setGuardian(ParentProfile guardian) { this.guardian = guardian; }
    public String getIdCardNo() { return idCardNo; }
    public void setIdCardNo(String idCardNo) { this.idCardNo = idCardNo; }
    public LocalDate getMoveInDate() { return moveInDate; }
    public void setMoveInDate(LocalDate moveInDate) { this.moveInDate = moveInDate; }
    public String getMigrationNote() { return migrationNote; }
    public void setMigrationNote(String migrationNote) { this.migrationNote = migrationNote; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public LocalDateTime getHealthUpdatedAt() { return healthUpdatedAt; }
    public void setHealthUpdatedAt(LocalDateTime healthUpdatedAt) { this.healthUpdatedAt = healthUpdatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
