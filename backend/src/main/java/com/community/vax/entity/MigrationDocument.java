package com.community.vax.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 家长上传的外地预防接种本（照片/扫描件），识别出的记录挂到 prior_vaccination */
@Entity
@Table(name = "migration_document")
public class MigrationDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long childId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 64)
    private String contentType;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] data;

    /** UPLOADED 已上传 / PARSED 已识别 / COMMITTED 已提交核验 */
    @Column(nullable = false, length = 16)
    private String status = "UPLOADED";

    @Column(length = 2000)
    private String ocrText;

    private Long uploadedByUserId;

    @Column(length = 32)
    private String uploadedByName;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public Long getUploadedByUserId() { return uploadedByUserId; }
    public void setUploadedByUserId(Long v) { this.uploadedByUserId = v; }
    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String v) { this.uploadedByName = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
