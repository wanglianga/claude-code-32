package com.community.vax.service;

import com.community.vax.common.BizException;
import com.community.vax.entity.*;
import com.community.vax.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 疑似预防接种异常反应（AEFI）：关联接种批号、症状、就医记录与最终判断，
 * 支持门诊咨询答复、区级上报，以及按儿童/疫苗/批号追踪。
 */
@Service
public class AefiService {

    private final AefiCaseRepository aefiRepo;
    private final VaccinationRecordRepository recordRepo;
    private final VaccineRepository vaccineRepo;
    private final NotificationService notifier;

    public AefiService(AefiCaseRepository aefiRepo, VaccinationRecordRepository recordRepo,
                       VaccineRepository vaccineRepo, NotificationService notifier) {
        this.aefiRepo = aefiRepo;
        this.recordRepo = recordRepo;
        this.vaccineRepo = vaccineRepo;
        this.notifier = notifier;
    }

    /** 现场留观异常 → 自动建个案 */
    @Transactional
    public AefiCase createFromRecord(VaccinationRecord rec, String symptoms, String source, SysUser operator) {
        AefiCase c = new AefiCase();
        c.setRecord(rec);
        c.setChild(rec.getChild());
        c.setVaccineCode(rec.getVaccineCode());
        c.setVaccineName(rec.getVaccineName());
        c.setBatchNo(rec.getBatchNo());
        c.setDoseNo(rec.getDoseNo());
        c.setVaccinationDate(rec.getVaccinationDate());
        c.setSymptoms(symptoms);
        c.setOnsetDate(LocalDate.now());
        c.setStatus("OPEN");
        c.setSource(source);
        if (operator != null) {
            c.setCreatedBy(operator.getId());
            c.setCreatedByName(operator.getName());
        }
        c = aefiRepo.save(c);

        // 接种后发热/皮疹：状态推给预防接种医生、护士和随访人员
        notifier.notifyStaff("AEFI", "URGENT",
                "疑似异常反应：" + rec.getChild().getName() + " " + rec.getVaccineName(),
                "症状：" + symptoms + "；疫苗批号 " + rec.getBatchNo() + "；接种日期 "
                        + rec.getVaccinationDate() + "。请随访人员尽快联系家长并安排随访。",
                rec.getChild(), "AEFI", c.getId());
        return c;
    }

    public record AefiCreateRequest(Long childId, String vaccineCode, String batchNo, Integer doseNo,
                                    LocalDate vaccinationDate, String symptoms, LocalDate onsetDate,
                                    String symptomDetail, String medicalRecord, String hospital,
                                    String source) {}

    /** 家长咨询/随访发现的异常反应手工报卡 */
    @Transactional
    public AefiCase createManual(AefiCreateRequest req, SysUser operator) {
        Child child = new Child();
        child.setId(req.childId());

        // 尽量关联本门诊接种记录（按儿童+批号）
        VaccinationRecord matched = recordRepo.findByBatchNo(req.batchNo()).stream()
                .filter(r -> r.getChild().getId().equals(req.childId()))
                .findFirst().orElse(null);

        AefiCase c = new AefiCase();
        c.setChild(child);
        c.setVaccineCode(req.vaccineCode());
        c.setVaccineName(vaccineRepo.findByCode(req.vaccineCode()).map(Vaccine::getName)
                .orElse(req.vaccineCode()));
        c.setBatchNo(req.batchNo());
        c.setDoseNo(req.doseNo());
        c.setVaccinationDate(req.vaccinationDate() == null ? LocalDate.now() : req.vaccinationDate());
        c.setSymptoms(req.symptoms());
        c.setOnsetDate(req.onsetDate() == null ? LocalDate.now() : req.onsetDate());
        c.setSymptomDetail(req.symptomDetail());
        c.setMedicalRecord(req.medicalRecord());
        c.setHospital(req.hospital());
        c.setSource(req.source() == null ? "家长咨询" : req.source());
        c.setStatus("OPEN");
        c.setCreatedBy(operator.getId());
        c.setCreatedByName(operator.getName());
        if (matched != null) {
            c.setRecord(matched);
            c.setVaccineCode(matched.getVaccineCode());
            c.setVaccineName(matched.getVaccineName());
            c.setDoseNo(matched.getDoseNo());
            c.setVaccinationDate(matched.getVaccinationDate());
        }
        c = aefiRepo.save(c);
        final Long childId = req.childId();
        final AefiCase saved = c;
        notifier.notifyStaff("AEFI", "URGENT",
                "家长报告疑似异常反应",
                "儿童 ID " + childId + "，疫苗 " + saved.getVaccineName() + "，批号 " + req.batchNo()
                        + "，症状：" + req.symptoms() + "。请尽快核实并随访。",
                null, "AEFI", saved.getId());
        return saved;
    }

    /** 登记就医记录 */
    @Transactional
    public AefiCase updateMedical(Long id, String hospital, String medicalRecord) {
        AefiCase c = aefiRepo.findById(id).orElseThrow(() -> new BizException("个案不存在"));
        c.setHospital(hospital);
        c.setMedicalRecord(medicalRecord);
        c.setUpdatedAt(LocalDateTime.now());
        return aefiRepo.save(c);
    }

    /** 最终判断（一般反应/异常反应/偶合症…），可同时结案 */
    @Transactional
    public AefiCase conclude(Long id, String conclusion, String note, boolean closeCase) {
        AefiCase c = aefiRepo.findById(id).orElseThrow(() -> new BizException("个案不存在"));
        c.setFinalConclusion(conclusion);
        c.setConclusionNote(note);
        if (closeCase) c.setStatus("CLOSED");
        c.setUpdatedAt(LocalDateTime.now());
        return aefiRepo.save(c);
    }

    /** 区级上报 */
    @Transactional
    public AefiCase reportDistrict(Long id) {
        AefiCase c = aefiRepo.findById(id).orElseThrow(() -> new BizException("个案不存在"));
        if (c.getDistrictReported()) throw new BizException("该个案已上报区级");
        c.setDistrictReported(true);
        c.setStatus("REPORTED");
        c.setReportedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        aefiRepo.save(c);
        notifier.notifyRole("ADMIN", "INFO", "INFO",
                "AEFI 已区级上报：" + c.getChild().getName(),
                c.getVaccineName() + "（批号 " + c.getBatchNo() + "）个案已生成区级上报材料，最终判断："
                        + (c.getFinalConclusion() == null ? "待定" : c.getFinalConclusion()) + "。",
                c.getChild(), "AEFI", c.getId());
        return c;
    }

    @Transactional(readOnly = true)
    public List<AefiCase> search(Long childId, String vaccineCode, String batchNo, boolean openOnly) {
        List<AefiCase> all = openOnly
                ? aefiRepo.findByStatusNotOrderByCreatedAtDesc("CLOSED")
                : aefiRepo.findAll().stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())).toList();
        return all.stream()
                .filter(c -> childId == null || c.getChild().getId().equals(childId))
                .filter(c -> vaccineCode == null || vaccineCode.isBlank()
                        || vaccineCode.equals(c.getVaccineCode()))
                .filter(c -> batchNo == null || batchNo.isBlank() || batchNo.equals(c.getBatchNo()))
                .toList();
    }
}
