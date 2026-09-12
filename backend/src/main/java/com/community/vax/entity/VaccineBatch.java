package com.community.vax.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 疫苗批号库存（含冷链状态） */
@Entity
@Table(name = "vaccine_batch", uniqueConstraints = @UniqueConstraint(columnNames = "batchNo"))
public class VaccineBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String batchNo;

    @Column(nullable = false, length = 32)
    private String vaccineCode;

    @Column(nullable = false, length = 64)
    private String vaccineName;

    @Column(nullable = false)
    private Integer quantity = 0;

    /** 安全库存阈值，低于即预警 */
    @Column(nullable = false)
    private Integer safetyStock = 10;

    @Column(nullable = false)
    private LocalDate expiryDate;

    private LocalDate arrivalDate;

    /** NORMAL 正常 / BROKEN 冷链中断 / DECOMMISSIONED 报废停用 */
    @Column(nullable = false, length = 16)
    private String coldChainStatus = "NORMAL";

    private LocalDateTime coldChainCheckedAt = LocalDateTime.now();

    @Column(length = 255)
    private String coldChainNote;

    private LocalDateTime createdAt = LocalDateTime.now();

    /** 综合可发放：在效期内、冷链正常、库存>0 */
    @Transient
    public boolean isAvailable() {
        return quantity > 0
                && !expiryDate.isBefore(LocalDate.now())
                && "NORMAL".equals(coldChainStatus);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getVaccineCode() { return vaccineCode; }
    public void setVaccineCode(String vaccineCode) { this.vaccineCode = vaccineCode; }
    public String getVaccineName() { return vaccineName; }
    public void setVaccineName(String vaccineName) { this.vaccineName = vaccineName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getSafetyStock() { return safetyStock; }
    public void setSafetyStock(Integer safetyStock) { this.safetyStock = safetyStock; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public LocalDate getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(LocalDate arrivalDate) { this.arrivalDate = arrivalDate; }
    public String getColdChainStatus() { return coldChainStatus; }
    public void setColdChainStatus(String coldChainStatus) { this.coldChainStatus = coldChainStatus; }
    public LocalDateTime getColdChainCheckedAt() { return coldChainCheckedAt; }
    public void setColdChainCheckedAt(LocalDateTime coldChainCheckedAt) { this.coldChainCheckedAt = coldChainCheckedAt; }
    public String getColdChainNote() { return coldChainNote; }
    public void setColdChainNote(String coldChainNote) { this.coldChainNote = coldChainNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
