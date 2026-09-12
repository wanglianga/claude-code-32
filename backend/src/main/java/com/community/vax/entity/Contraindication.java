package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "contraindication")
public class Contraindication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "child_id", nullable = false)
    @JsonIgnoreProperties({"guardian"})
    private Child child;

    /** 禁忌类型，如 免疫缺陷、严重过敏史、急性发热、妊娠期（示例） */
    @Column(nullable = false, length = 64)
    private String contraType;

    @Column(length = 500)
    private String description;

    /** 适用疫苗代码，ALL 表示所有疫苗 */
    @Column(nullable = false, length = 32)
    private String vaccineCode = "ALL";

    @Column(nullable = false)
    private Boolean active = true;

    private LocalDate recordedDate = LocalDate.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Child getChild() { return child; }
    public void setChild(Child child) { this.child = child; }
    public String getContraType() { return contraType; }
    public void setContraType(String contraType) { this.contraType = contraType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVaccineCode() { return vaccineCode; }
    public void setVaccineCode(String vaccineCode) { this.vaccineCode = vaccineCode; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDate getRecordedDate() { return recordedDate; }
    public void setRecordedDate(LocalDate recordedDate) { this.recordedDate = recordedDate; }
}
