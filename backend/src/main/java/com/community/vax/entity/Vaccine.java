package com.community.vax.entity;

import jakarta.persistence.*;

/** 疫苗目录（免疫规划疫苗 + 非免疫规划疫苗） */
@Entity
@Table(name = "vaccine", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class Vaccine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 疫苗代码，如 HEPB、BCG、OPV */
    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    /** 同品种可替代疫苗的分组代码，如 IPV/OPV 同属脊灰组；换苗判断使用 */
    @Column(nullable = false, length = 32)
    private String vaccineGroup;

    /** 免疫规划 / 非免疫规划 */
    @Column(nullable = false, length = 16)
    private String category = "免疫规划";

    /** 总剂次数 */
    @Column(nullable = false)
    private Integer totalDoses = 1;

    @Column(length = 255)
    private String description;

    /** 疫苗含有的常见致敏成分关键字（逗号分隔），如“酵母,鸡蛋”，用于过敏史匹配 */
    @Column(length = 128)
    private String components;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVaccineGroup() { return vaccineGroup; }
    public void setVaccineGroup(String vaccineGroup) { this.vaccineGroup = vaccineGroup; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getTotalDoses() { return totalDoses; }
    public void setTotalDoses(Integer totalDoses) { this.totalDoses = totalDoses; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getComponents() { return components; }
    public void setComponents(String components) { this.components = components; }
}
