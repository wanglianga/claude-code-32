package com.community.vax.service;

import com.community.vax.common.BizException;
import com.community.vax.entity.*;
import com.community.vax.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 接种现场服务：护士五项核验（身份/批号/知情同意/近期发热/禁忌症）→
 * 医生复核（必要时）→ 接种扣库存 → 留观计时、现场反应与家长确认 → 回写计划/既往史。
 */
@Service
public class VaccinationService {

    private final VaccinationRecordRepository recordRepo;
    private final AppointmentRepository appointmentRepo;
    private final VaccineBatchRepository batchRepo;
    private final PriorVaccinationRepository priorRepo;
    private final VaccinationPlanRepository planRepo;
    private final StockService stockService;
    private final PlanService planService;
    private final AefiService aefiService;
    private final ConsultationService consultationService;
    private final AppointmentService appointmentService;
    private final NotificationService notifier;

    public VaccinationService(VaccinationRecordRepository recordRepo, AppointmentRepository appointmentRepo,
                              VaccineBatchRepository batchRepo, PriorVaccinationRepository priorRepo,
                              VaccinationPlanRepository planRepo, StockService stockService,
                              PlanService planService, AefiService aefiService,
                              ConsultationService consultationService, AppointmentService appointmentService,
                              NotificationService notifier) {
        this.recordRepo = recordRepo;
        this.appointmentRepo = appointmentRepo;
        this.batchRepo = batchRepo;
        this.priorRepo = priorRepo;
        this.planRepo = planRepo;
        this.stockService = stockService;
        this.planService = planService;
        this.aefiService = aefiService;
        this.consultationService = consultationService;
        this.appointmentService = appointmentService;
        this.notifier = notifier;
    }

    /** 核验入参 */
    public record VerifyRequest(
            Boolean identityVerified,
            Boolean batchVerified,
            Boolean consentSigned,
            Boolean recentFeverChecked,
            Boolean recentFever,
            Boolean contraindicationChecked,
            String actualBatchNo,
            String verifyNote,
            boolean withhold,
            String withholdReason) {}

    /** 护士到诊核验 + 接种（核验通过即完成接种并扣减库存，进入留观） */
    @Transactional
    public VaccinationRecord verifyAndVaccinate(Long appointmentId, VerifyRequest req, SysUser nurse) {
        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new BizException("预约不存在"));
        if (!"CHECKED_IN".equals(appt.getStatus()) && !"BOOKED".equals(appt.getStatus())) {
            throw new BizException("当前预约状态不可核验（需先到诊签到）");
        }

        Child child = appt.getChild();

        // 暂缓不接种：记录原因，释放预约并推送医生
        if (req.withhold()) {
            VaccinationRecord rec = new VaccinationRecord();
            rec.setAppointment(appt);
            rec.setChild(child);
            rec.setVaccineCode(appt.getVaccineCode());
            rec.setVaccineName(appt.getVaccineName());
            rec.setDoseNo(appt.getDoseNo());
            rec.setBatchNo(req.actualBatchNo() == null ? "-" : req.actualBatchNo());
            rec.setVaccinationDate(LocalDate.now());
            rec.setNurseUserId(nurse.getId());
            rec.setNurseName(nurse.getName());
            rec.setStatus("WITHHELD");
            rec.setVerifyNote(req.withholdReason());
            rec.setObservationMinutes(0);
            rec = recordRepo.save(rec);
            appt.setStatus("CANCELLED_CLINIC");
            appt.setCancelReason("到诊暂缓：" + req.withholdReason());
            appt.setUpdatedAt(LocalDateTime.now());
            appointmentRepo.save(appt);
            appointmentService.releaseSlot(appt.getId());
            notifier.notifyStaff("CONTRA", "URGENT",
                    "到诊暂缓接种：" + child.getName() + " " + appt.getVaccineName(),
                    "护士 " + nurse.getName() + " 核验后暂缓接种，原因：" + req.withholdReason()
                            + "。需预防接种医生跟进复核并重新安排补种。",
                    child, "RECORD", rec.getId());
            planService.regenerate(child.getId());
            return rec;
        }

        requireTrue(req.identityVerified(), "儿童身份未核验通过，不能接种");
        requireTrue(req.batchVerified(), "疫苗批号未核验通过，不能接种");
        requireTrue(req.consentSigned(), "家长未签署知情同意书，不能接种");
        requireTrue(req.recentFeverChecked(), "未完成近期发热筛查");
        requireTrue(req.contraindicationChecked(), "未完成禁忌症筛查");
        if (Boolean.TRUE.equals(req.recentFever())) {
            throw new BizException("儿童近期发热未排除，应暂缓接种并请医生评估");
        }

        String batchNo = req.actualBatchNo() == null || req.actualBatchNo().isBlank()
                ? appt.getReservedBatchNo() : req.actualBatchNo();
        VaccineBatch batch = batchRepo.findByBatchNo(batchNo)
                .orElseThrow(() -> new BizException("实际批号 " + batchNo + " 不存在"));
        if (!batch.getVaccineCode().equals(appt.getVaccineCode())) {
            throw new BizException("实际批号所属疫苗与预约疫苗不一致，请核对或走同组换苗流程");
        }
        if (!batch.isAvailable()) {
            throw new BizException("批号 " + batchNo + " 不可发放："
                    + (batch.getColdChainStatus() != null && !"NORMAL".equals(batch.getColdChainStatus())
                        ? "冷链状态 " + batch.getColdChainStatus()
                        : "库存为 0 或已过期"));
        }

        VaccinationRecord rec = recordRepo.findByAppointmentId(appt.getId())
                .orElseGet(VaccinationRecord::new);
        rec.setAppointment(appt);
        rec.setChild(child);
        rec.setVaccineCode(appt.getVaccineCode());
        rec.setVaccineName(appt.getVaccineName());
        rec.setDoseNo(appt.getDoseNo());
        rec.setBatchNo(batchNo);
        rec.setVaccineExpiryDate(batch.getExpiryDate());
        rec.setVaccinationDate(LocalDate.now());
        rec.setNurseUserId(nurse.getId());
        rec.setNurseName(nurse.getName());
        rec.setDoctorUserId(appt.getDoctorUserId());
        rec.setDoctorName(appt.getDoctorName());
        rec.setIdentityVerified(true);
        rec.setBatchVerified(true);
        rec.setConsentSigned(true);
        rec.setRecentFeverChecked(true);
        rec.setRecentFever(false);
        rec.setContraindicationChecked(true);
        rec.setVerifyNote(req.verifyNote());
        rec.setInjectionSite("左上臂外侧");
        rec.setObservationMinutes(30);
        rec.setObservationStartTime(LocalDateTime.now());
        rec.setOnSiteReaction("留观中");
        rec.setGuardianConfirmed(false);
        rec.setStatus("OBSERVING");
        if (appt.getPlan() != null && !appt.getPlan().getVaccineCode().equals(appt.getVaccineCode())) {
            rec.setSubstitutionNote("同组换苗：计划 " + appt.getPlan().getVaccineName()
                    + " 缺货，实际接种 " + appt.getVaccineName());
        }
        rec = recordRepo.save(rec);

        // 扣库存（近效期批号），并检查安全库存
        stockService.consumeBatch(batchNo);
        if (batch.getQuantity() <= batch.getSafetyStock()) {
            notifier.notifyRole("ADMIN", "STOCK", "WARN",
                    "库存预警：" + batch.getVaccineName() + " 批号 " + batchNo,
                    "接种扣减后剩余 " + batch.getQuantity() + " 支，已低于安全库存 "
                            + batch.getSafetyStock() + "，请及时补货。", child, "BATCH", batch.getId());
        }

        appt.setStatus("VACCINATED");
        appt.setUpdatedAt(LocalDateTime.now());
        appointmentRepo.save(appt);
        return rec;
    }

    /** 医生复核（健康状态/禁忌存疑时） */
    @Transactional
    public VaccinationRecord doctorReview(Long recordId, String note, boolean approved, SysUser doctor) {
        VaccinationRecord rec = recordRepo.findById(recordId).orElseThrow(() -> new BizException("记录不存在"));
        rec.setDoctorUserId(doctor.getId());
        rec.setDoctorName(doctor.getName());
        rec.setDoctorReviewNote((approved ? "同意接种；" : "建议暂缓；") + note);
        return recordRepo.save(rec);
    }

    /** 留观完成入参 */
    public record ObserveRequest(String onSiteReaction, boolean guardianConfirmed,
                                 boolean abnormal, String abnormalSymptoms) {}

    /** 结束留观：家长确认；现场异常自动建 AEFI 并推送 */
    @Transactional
    public VaccinationRecord completeObservation(Long recordId, ObserveRequest req, SysUser operator) {
        VaccinationRecord rec = recordRepo.findById(recordId)
                .orElseThrow(() -> new BizException("接种记录不存在"));
        if (!"OBSERVING".equals(rec.getStatus())) {
            throw new BizException("仅留观中的记录可以结束留观");
        }
        requireTrue(req.guardianConfirmed(), "家长未确认，不能结束留观");
        if (req.onSiteReaction() == null || req.onSiteReaction().isBlank()) {
            throw new BizException("请记录现场反应（无异常请填“无异常”）");
        }
        rec.setOnSiteReaction(req.onSiteReaction());
        rec.setGuardianConfirmed(true);
        rec.setObservationEndTime(LocalDateTime.now());
        rec.setStatus(req.abnormal() ? "ABNORMAL" : "COMPLETED");
        rec = recordRepo.save(rec);

        if (req.abnormal()) {
            AefiCase aefi = aefiService.createFromRecord(rec,
                    req.abnormalSymptoms() == null || req.abnormalSymptoms().isBlank()
                            ? req.onSiteReaction() : req.abnormalSymptoms(),
                    "现场留观", operator);
            notifier.notifyStaff("AEFI", "URGENT",
                    "现场异常反应：" + rec.getChild().getName() + " " + rec.getVaccineName(),
                    "留观期间发现异常（" + aefi.getSymptoms() + "），批号 " + rec.getBatchNo()
                            + "，已建立 AEFI 个案，请随访人员立即跟进。",
                    rec.getChild(), "AEFI", aefi.getId());
        } else {
            // 正常完成：写入既往接种、回写计划并重新生成后续剂次
            writePriorAndCompletePlan(rec);
        }
        return rec;
    }

    /** 现场异常经评估排除后，补登记为正常完成 */
    @Transactional
    public VaccinationRecord resolveAbnormal(Long recordId) {
        VaccinationRecord rec = recordRepo.findById(recordId).orElseThrow();
        if (!"ABNORMAL".equals(rec.getStatus())) throw new BizException("仅现场异常状态可转正常完成");
        rec.setStatus("COMPLETED");
        recordRepo.save(rec);
        writePriorAndCompletePlan(rec);
        return rec;
    }

    private void writePriorAndCompletePlan(VaccinationRecord rec) {
        Child child = rec.getChild();
        PriorVaccination prior = new PriorVaccination();
        prior.setChild(child);
        prior.setVaccineCode(rec.getVaccineCode());
        prior.setVaccineName(rec.getVaccineName());
        prior.setDoseNo(rec.getDoseNo());
        prior.setVaccinationDate(rec.getVaccinationDate());
        prior.setBatchNo(rec.getBatchNo());
        prior.setClinicName("本社区卫生服务中心");
        prior.setSource("LOCAL");
        prior.setVerified(true);
        priorRepo.save(prior);

        // 回写对应计划行（允许同组换苗完成）
        VaccinationPlan matched = planRepo.findByChildIdAndVaccineCodeAndDoseNo(
                child.getId(), rec.getVaccineCode(), rec.getDoseNo()).orElse(null);
        if (matched == null && rec.getAppointment() != null && rec.getAppointment().getPlan() != null) {
            matched = rec.getAppointment().getPlan();
        }
        if (matched != null) {
            matched.setStatus("DONE");
            matched.setCompletedDate(rec.getVaccinationDate());
            matched.setCompletedVaccineCode(rec.getVaccineCode());
            matched.setCompletedVaccineName(rec.getVaccineName());
            matched.setCompletedBatchNo(rec.getBatchNo());
            String note = "已于 " + rec.getVaccinationDate() + " 完成接种，批号 " + rec.getBatchNo();
            if (rec.getSubstitutionNote() != null) note += "；" + rec.getSubstitutionNote();
            matched.setRemark(note);
            matched.setUpdatedAt(LocalDateTime.now());
            planRepo.save(matched);
        }

        // 重新生成后续剂次（最短间隔据此滚动）；家长咨询的接种前提醒随核验视图带出
        planService.regenerate(child.getId());

        // 接种前咨询提醒：本次核验时若有未处理的 preVaccineAlert，标记已随接种处理并通知家长已阅
        var alerts = consultationService.pendingAlertsForChild(child.getId());
        if (!alerts.isEmpty()) {
            notifier.notifyUser(child.getGuardian().getUserId(), "INFO", "INFO",
                    "接种前咨询要点已随本次接种告知医护",
                    rec.getChild().getName() + " 本次接种前，门诊已查阅您此前咨询中标记的 "
                            + alerts.size() + " 条注意事项。", child, "RECORD", rec.getId());
        }
    }

    private void requireTrue(Boolean v, String msg) {
        if (!Boolean.TRUE.equals(v)) throw new BizException(msg);
    }
}
