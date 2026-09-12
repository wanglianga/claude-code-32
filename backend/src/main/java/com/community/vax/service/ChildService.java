package com.community.vax.service;

import com.community.vax.common.BizException;
import com.community.vax.common.Role;
import com.community.vax.entity.*;
import com.community.vax.repo.*;
import com.community.vax.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChildService {

    private final ChildRepository childRepo;
    private final ParentProfileRepository parentRepo;
    private final SysUserRepository userRepo;
    private final AllergyRepository allergyRepo;
    private final ContraindicationRepository contraRepo;
    private final PriorVaccinationRepository priorRepo;
    private final VaccinationRecordRepository recordRepo;
    private final VaccinationPlanRepository planRepo;
    private final AefiCaseRepository aefiRepo;
    private final ConsultationRepository consultRepo;
    private final VaccineRepository vaccineRepo;
    private final PlanService planService;
    private final NotificationService notifier;

    public ChildService(ChildRepository childRepo, ParentProfileRepository parentRepo,
                        SysUserRepository userRepo, AllergyRepository allergyRepo,
                        ContraindicationRepository contraRepo, PriorVaccinationRepository priorRepo,
                        VaccinationRecordRepository recordRepo, VaccinationPlanRepository planRepo,
                        AefiCaseRepository aefiRepo, ConsultationRepository consultRepo,
                        VaccineRepository vaccineRepo,
                        PlanService planService, NotificationService notifier) {
        this.childRepo = childRepo;
        this.parentRepo = parentRepo;
        this.userRepo = userRepo;
        this.allergyRepo = allergyRepo;
        this.contraRepo = contraRepo;
        this.priorRepo = priorRepo;
        this.recordRepo = recordRepo;
        this.planRepo = planRepo;
        this.aefiRepo = aefiRepo;
        this.consultRepo = consultRepo;
        this.vaccineRepo = vaccineRepo;
        this.planService = planService;
        this.notifier = notifier;
    }

    @Transactional
    public Child create(Child child, Long guardianUserId) {
        ParentProfile guardian = parentRepo.findById(
                        userRepo.findById(guardianUserId).orElseThrow().getPersonId())
                .orElseThrow(() -> new BizException("家长档案不存在"));
        child.setGuardian(guardian);
        child.setCreatedAt(LocalDateTime.now());
        child = childRepo.save(child);
        planService.regenerate(child.getId());
        return child;
    }

    @Transactional(readOnly = true)
    public List<Child> listFor(SysUser user) {
        if (user.getRole() == Role.PARENT) {
            ParentProfile p = parentRepo.findById(user.getPersonId()).orElse(null);
            return p == null ? List.of() : childRepo.findByGuardianIdOrderByBirthDateDesc(p.getId());
        }
        return childRepo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Child getFor(SysUser user, Long id) {
        Child child = childRepo.findById(id).orElseThrow(() -> new BizException("儿童不存在"));
        if (user.getRole() == Role.PARENT
                && (child.getGuardian() == null
                || !child.getGuardian().getUserId().equals(user.getId()))) {
            throw new BizException("无权查看该儿童档案", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return child;
    }

    @Transactional
    public Child updateHealth(Long id, String healthStatus, SysUser user) {
        Child child = getFor(user, id);
        child.setHealthStatus(healthStatus);
        child.setHealthUpdatedAt(LocalDateTime.now());
        child = childRepo.save(child);
        // 急性症状申报推送护士/医生，到诊核验与预约判断都会读取
        if (healthStatus != null && healthStatus.matches(".*(发热|发烧|皮疹|呕吐|腹泻).*")) {
            notifier.notifyStaff("INFO", "WARN",
                    "儿童健康状态申报：" + child.getName(),
                    "家长更新近期健康状态：" + healthStatus + "。预约与接种前请复核。",
                    child, "CHILD", child.getId());
        }
        return child;
    }

    @Transactional
    public Allergy addAllergy(Long childId, Allergy allergy, SysUser user) {
        allergy.setChild(getFor(user, childId));
        allergy = allergyRepo.save(allergy);
        // 新增过敏史后，既有医生复核结论失效，需要重新复核
        planRepo.findByChildIdOrderByDueDateAsc(childId)
                .forEach(p -> { p.setDoctorApproved(false); planRepo.save(p); });
        planService.regenerate(childId);
        return allergy;
    }

    @Transactional
    public Contraindication addContra(Long childId, Contraindication c, SysUser user) {
        c.setChild(getFor(user, childId));
        if (c.getRecordedDate() == null) c.setRecordedDate(LocalDate.now());
        c = contraRepo.save(c);
        final String vacCode = c.getVaccineCode();
        planRepo.findByChildIdOrderByDueDateAsc(childId).stream()
                .filter(p -> "ALL".equals(vacCode) || p.getVaccineCode().equals(vacCode))
                .forEach(p -> { p.setDoctorApproved(false); planRepo.save(p); });
        planService.regenerate(childId);
        notifier.notifyStaff("CONTRA", "WARN",
                "新增禁忌症：" + c.getChild().getName(),
                "禁忌类型：" + c.getContraType() + "；适用疫苗：" + c.getVaccineCode()
                        + "；说明：" + nullToDash(c.getDescription()), c.getChild(), "CHILD", childId);
        return c;
    }

    public record PriorRequest(String vaccineCode, Integer doseNo, LocalDate vaccinationDate,
                               String batchNo, String clinicName, String note) {}

    @Transactional
    public PriorVaccination addPrior(Long childId, PriorRequest req, SysUser user) {
        Child child = getFor(user, childId);
        PriorVaccination p = new PriorVaccination();
        p.setChild(child);
        p.setVaccineCode(req.vaccineCode());
        p.setVaccineName(vaccineRepo.findByCode(req.vaccineCode())
                .map(Vaccine::getName).orElse(req.vaccineCode()));
        p.setDoseNo(req.doseNo());
        p.setVaccinationDate(req.vaccinationDate());
        p.setBatchNo(req.batchNo());
        p.setClinicName(req.clinicName());
        p.setNote(req.note());
        p.setSource("MIGRATED");
        p.setVerified(false);
        p = priorRepo.save(p);
        notifier.notifyStaff("MIGRATION", "WARN",
                "迁入接种记录待核验：" + child.getName(),
                "家长登记 " + req.vaccineCode() + " 第" + req.doseNo() + "剂（" + req.vaccinationDate()
                        + "，原接种单位：" + nullToDash(req.clinicName()) + "），请核验接种证后确认。",
                child, "PRIOR", p.getId());
        planService.regenerate(childId);
        return p;
    }

    /** 护士/医生核验迁入记录材料 */
    @Transactional
    public PriorVaccination verifyPrior(Long priorId, boolean verified) {
        PriorVaccination p = priorRepo.findById(priorId).orElseThrow(() -> new BizException("记录不存在"));
        p.setVerified(verified);
        priorRepo.save(p);
        planService.regenerate(p.getChild().getId());
        return p;
    }

    @Transactional
    public List<VaccinationPlan> replan(Long childId, SysUser user) {
        getFor(user, childId);
        return planService.regenerate(childId);
    }

    /**
     * 儿童维度健康档案：基础信息 + 过敏/禁忌 + 时间线
     * （计划、接种、异常反应、咨询、迁入记录统一按时间排列，可解释补种延后/换苗/复核原因）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> healthRecord(SysUser user, Long childId) {
        Child child = getFor(user, childId);
        List<Map<String, Object>> timeline = new ArrayList<>();

        for (VaccinationPlan p : planRepo.findByChildIdOrderByDueDateAsc(childId)) {
            timeline.add(Map.of(
                    "date", String.valueOf(p.getDueDate()),
                    "type", "PLAN", "typeName", "接种计划",
                    "title", p.getVaccineName() + " 第" + p.getDoseNo() + "剂",
                    "status", p.getStatus(),
                    "detail", nullToDash(p.getRemark())));
        }
        for (PriorVaccination p : priorRepo.findByChildIdOrderByVaccinationDateAsc(childId)) {
            timeline.add(Map.of(
                    "date", String.valueOf(p.getVaccinationDate()),
                    "type", "PRIOR", "typeName", "MIGRATED".equals(p.getSource()) ? "迁入接种" : "既往接种",
                    "title", p.getVaccineName() + " 第" + p.getDoseNo() + "剂",
                    "status", Boolean.TRUE.equals(p.getVerified()) ? "VERIFIED" : "UNVERIFIED",
                    "detail", "批号 " + nullToDash(p.getBatchNo()) + "；" + nullToDash(p.getClinicName())
                            + ("MIGRATED".equals(p.getSource())
                                ? (Boolean.TRUE.equals(p.getVerified()) ? "（材料已核验）" : "（材料待核验）")
                                : "（本门诊接种）")));
        }
        for (VaccinationRecord r : recordRepo.findByChildIdOrderByVaccinationDateDesc(childId)) {
            timeline.add(Map.of(
                    "date", String.valueOf(r.getVaccinationDate()),
                    "type", "RECORD", "typeName", "接种记录",
                    "title", r.getVaccineName() + " 第" + r.getDoseNo() + "剂",
                    "status", r.getStatus(),
                    "detail", "批号 " + r.getBatchNo()
                            + ("WITHHELD".equals(r.getStatus()) ? "；暂缓原因：" + nullToDash(r.getVerifyNote())
                            : "；现场反应：" + nullToDash(r.getOnSiteReaction())
                              + (r.getSubstitutionNote() == null ? "" : "；" + r.getSubstitutionNote())
                              + (r.getDoctorReviewNote() == null ? "" : "；医生复核：" + r.getDoctorReviewNote()))));
        }
        for (AefiCase a : aefiRepo.findByChildIdOrderByCreatedAtDesc(childId)) {
            timeline.add(Map.of(
                    "date", String.valueOf(a.getVaccinationDate()),
                    "type", "AEFI", "typeName", "异常反应",
                    "title", a.getVaccineName() + "（批号 " + a.getBatchNo() + "）",
                    "status", a.getStatus(),
                    "detail", "症状：" + a.getSymptoms()
                            + "；最终判断：" + (a.getFinalConclusion() == null ? "待判断" : a.getFinalConclusion())));
        }
        for (Consultation c : consultRepo.findByChildIdOrderByCreatedAtDesc(childId)) {
            timeline.add(Map.of(
                    "date", String.valueOf(c.getCreatedAt().toLocalDate()),
                    "type", "CONSULT", "typeName", "家长咨询",
                    "title", c.getTopic(),
                    "status", c.getStatus(),
                    "detail", c.getQuestion()
                            + (c.getReply() == null ? "" : "；回复：" + c.getReply())
                            + (Boolean.TRUE.equals(c.getPreVaccineAlert()) ? "（已列入下次接种前提醒）" : "")));
        }

        timeline.sort((a, b) -> String.valueOf(b.get("date")).compareTo(String.valueOf(a.get("date"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("child", child);
        result.put("allergies", allergyRepo.findByChildId(childId));
        result.put("contraindications", contraRepo.findByChildIdAndActiveTrue(childId));
        result.put("priors", priorRepo.findByChildIdOrderByVaccinationDateAsc(childId));
        result.put("timeline", timeline);
        return result;
    }

    private String nullToDash(String s) { return s == null || s.isBlank() ? "-" : s; }
}
