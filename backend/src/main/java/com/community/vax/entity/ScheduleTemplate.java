package com.community.vax.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;

/** 免疫程序模板：某疫苗第几剂的建议月龄、最早月龄、年龄上限、与上剂最短间隔（天） */
@Entity
@Table(name = "schedule_template")
public class ScheduleTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vaccine_id", nullable = false)
    @JsonIgnoreProperties({"description"})
    private Vaccine vaccine;

    @Column(nullable = false)
    private Integer doseNo;

    /** 建议接种月龄 */
    @Column(nullable = false)
    private Integer recommendedAgeMonths;

    /** 最早可接种月龄（考虑最短间隔与起始月龄） */
    @Column(nullable = false)
    private Integer minAgeMonths = 0;

    /** 年龄上限月龄，超过不再补种 */
    @Column(nullable = false)
    private Integer maxAgeMonths;

    /** 与同疫苗上一剂的最短间隔（天），首剂为 0 */
    @Column(nullable = false)
    private Integer minIntervalDays = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Vaccine getVaccine() { return vaccine; }
    public void setVaccine(Vaccine vaccine) { this.vaccine = vaccine; }
    public Integer getDoseNo() { return doseNo; }
    public void setDoseNo(Integer doseNo) { this.doseNo = doseNo; }
    public Integer getRecommendedAgeMonths() { return recommendedAgeMonths; }
    public void setRecommendedAgeMonths(Integer recommendedAgeMonths) { this.recommendedAgeMonths = recommendedAgeMonths; }
    public Integer getMinAgeMonths() { return minAgeMonths; }
    public void setMinAgeMonths(Integer minAgeMonths) { this.minAgeMonths = minAgeMonths; }
    public Integer getMaxAgeMonths() { return maxAgeMonths; }
    public void setMaxAgeMonths(Integer maxAgeMonths) { this.maxAgeMonths = maxAgeMonths; }
    public Integer getMinIntervalDays() { return minIntervalDays; }
    public void setMinIntervalDays(Integer minIntervalDays) { this.minIntervalDays = minIntervalDays; }
}
