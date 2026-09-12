package com.community.vax.service;

import com.community.vax.common.BizException;
import com.community.vax.entity.*;
import com.community.vax.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 预约服务：可约时段由门诊容量、医生排班、疫苗批号/冷链/库存、儿童健康状态共同决定。
 */
@Service
public class AppointmentService {

    public static final List<String> ACTIVE = List.of("BOOKED", "CHECKED_IN", "VACCINATED");
    private static final List<String> HEALTH_BLOCK_KEYWORDS = List.of("发热", "发烧", "腹泻", "呕吐");

    private final AppointmentRepository appointmentRepo;
    private final ClinicCapacityRepository capacityRepo;
    private final DoctorScheduleRepository doctorScheduleRepo;
    private final VaccinationPlanRepository planRepo;
    private final ChildRepository childRepo;
    private final VaccineRepository vaccineRepo;
    private final StockService stockService;
    private final NotificationService notifier;

    public AppointmentService(AppointmentRepository appointmentRepo, ClinicCapacityRepository capacityRepo,
                              DoctorScheduleRepository doctorScheduleRepo, VaccinationPlanRepository planRepo,
                              ChildRepository childRepo, VaccineRepository vaccineRepo,
                              StockService stockService, NotificationService notifier) {
        this.appointmentRepo = appointmentRepo;
        this.capacityRepo = capacityRepo;
        this.doctorScheduleRepo = doctorScheduleRepo;
        this.planRepo = planRepo;
        this.childRepo = childRepo;
        this.vaccineRepo = vaccineRepo;
        this.stockService = stockService;
        this.notifier = notifier;
    }

    /** 可约时段视图行 */
    public record SlotView(LocalDate date, String timeSlot, int remaining, int capacity,
                           String doctorName, String batchNo, String vaccineCode, String vaccineName,
                           LocalDate batchExpiry, String coldChainStatus, boolean substituted,
                           boolean bookable, List<String> reasons) {}

    /** 查询某计划行未来 days 天的可约时段（含不可约原因，前端可解释） */
    @Transactional(readOnly = true)
    public List<SlotView> availableSlots(Long planId, String preferredVaccineCode, int days) {
        VaccinationPlan plan = planRepo.findById(planId).orElseThrow(() -> new BizException("计划不存在"));
        Child child = plan.getChild();
        LocalDate today = LocalDate.now();

        // 实际想约的疫苗：本苗或同组替代苗
        String vaccineCode = preferredVaccineCode == null || preferredVaccineCode.isBlank()
                ? plan.getVaccineCode() : preferredVaccineCode;
        Vaccine target = vaccineRepo.findByCode(vaccineCode).orElseThrow(() -> new BizException("疫苗不存在"));
        Vaccine planned = vaccineRepo.findByCode(plan.getVaccineCode()).orElseThrow();
        boolean substituted = !vaccineCode.equals(plan.getVaccineCode());
        if (substituted && !target.getVaccineGroup().equals(planned.getVaccineGroup())) {
            throw new BizException("替代疫苗必须与计划疫苗属于同一疫苗组");
        }
        List<VaccineBatch> batches = stockService.availableBatches(vaccineCode);

        List<SlotView> views = new ArrayList<>();
        List<ClinicCapacity> caps = capacityRepo
                .findByClinicDateBetweenOrderByClinicDateAscTimeSlotAsc(today, today.plusDays(days));
        for (ClinicCapacity cap : caps) {
            if (!cap.getOpen()) continue;
            List<String> reasons = new ArrayList<>();
            List<DoctorSchedule> docs = doctorScheduleRepo
                    .findByWorkDateAndTimeSlotAndOnDutyTrue(cap.getClinicDate(), cap.getTimeSlot());

            if (cap.getRemaining() <= 0) reasons.add("门诊容量已满");
            if (docs.isEmpty()) reasons.add("该时段无医生排班");
            if (batches.isEmpty()) {
                reasons.add(substituted ? "替代苗无可用批号" : "该疫苗无可用批号（缺货/过期/冷链中断）");
            }
            if (today.isBefore(plan.getEarliestDate())) {
                reasons.add("最短间隔/起始月龄未满，最早 " + plan.getEarliestDate());
            }
            if (cap.getClinicDate().isAfter(plan.getAgeLimitDate())) {
                reasons.add("超过补种年龄上限 " + plan.getAgeLimitDate());
            }
            String healthBlock = healthBlockReason(child);
            if (healthBlock != null) reasons.add(healthBlock);
            if (PlanService.BOOKABLE.contains(plan.getStatus()) == false) {
                reasons.add("WAIT_VERIFY".equals(plan.getStatus())
                        ? "迁入接种记录待人工核验，核验前不可预约，避免重复接种"
                        : "计划当前状态为 " + plan.getStatus() + "，不可直接预约");
            }

            VaccineBatch b = batches.isEmpty() ? null : batches.get(0);
            views.add(new SlotView(
                    cap.getClinicDate(), cap.getTimeSlot(), cap.getRemaining(), cap.getMaxCapacity(),
                    docs.isEmpty() ? null : docs.get(0).getDoctorName(),
                    b == null ? null : b.getBatchNo(), vaccineCode, target.getName(),
                    b == null ? null : b.getExpiryDate(),
                    b == null ? null : b.getColdChainStatus(),
                    substituted, reasons.isEmpty(), reasons));
        }
        return views;
    }

    /** 健康状态是否阻断预约（3 天内申报过发热等急性症状） */
    private String healthBlockReason(Child child) {
        if (child.getHealthStatus() == null || child.getHealthStatus().isBlank()) return null;
        if (child.getHealthUpdatedAt() != null
                && child.getHealthUpdatedAt().toLocalDate().isBefore(LocalDate.now().minusDays(3))) {
            return null;
        }
        for (String kw : HEALTH_BLOCK_KEYWORDS) {
            if (child.getHealthStatus().contains(kw)) {
                return "儿童近期健康申报异常（" + child.getHealthStatus() + "），需医生评估后再预约";
            }
        }
        return null;
    }

    public record BookRequest(Long planId, LocalDate date, String timeSlot, String vaccineCode) {}

    @Transactional
    public Appointment book(BookRequest req, SysUser parent) {
        VaccinationPlan plan = planRepo.findById(req.planId())
                .orElseThrow(() -> new BizException("计划不存在"));
        Child child = plan.getChild();
        if (child.getGuardian() == null || !child.getGuardian().getUserId().equals(parent.getId())) {
            throw new BizException("只能为自己的子女预约", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (!PlanService.BOOKABLE.contains(plan.getStatus())) {
            throw new BizException("该剂次当前状态（" + plan.getStatus() + "）不可预约：" + plan.getRemark());
        }
        if (LocalDate.now().isBefore(plan.getEarliestDate())) {
            throw new BizException("最短间隔/起始月龄未满，最早可接种日期 " + plan.getEarliestDate());
        }
        if (req.date().isAfter(plan.getAgeLimitDate())) {
            throw new BizException("预约日期超过补种年龄上限 " + plan.getAgeLimitDate());
        }
        String healthBlock = healthBlockReason(child);
        if (healthBlock != null) throw new BizException(healthBlock);

        // 同一计划行只允许一个有效预约
        List<Appointment> dup = appointmentRepo.findByPlanIdAndStatusIn(plan.getId(),
                List.of("BOOKED", "CHECKED_IN"));
        if (!dup.isEmpty()) throw new BizException("该剂次已存在未完成的预约，请先取消或改约");

        String vaccineCode = (req.vaccineCode() == null || req.vaccineCode().isBlank())
                ? plan.getVaccineCode() : req.vaccineCode();
        Vaccine target = vaccineRepo.findByCode(vaccineCode).orElseThrow(() -> new BizException("疫苗不存在"));
        Vaccine planned = vaccineRepo.findByCode(plan.getVaccineCode()).orElseThrow();
        boolean substituted = !vaccineCode.equals(plan.getVaccineCode());
        if (substituted && !target.getVaccineGroup().equals(planned.getVaccineGroup())) {
            throw new BizException("替代疫苗与计划疫苗不属于同一疫苗组，不能换苗");
        }
        List<VaccineBatch> batches = stockService.availableBatches(vaccineCode);
        if (batches.isEmpty()) {
            throw new BizException(substituted ? "所选替代苗无可用批号" : "该疫苗当前无可用批号（缺货/过期/冷链中断），可关注到货提醒或选择同组替代苗");
        }

        ClinicCapacity cap = capacityRepo.findByClinicDateAndTimeSlot(req.date(), req.timeSlot())
                .orElseThrow(() -> new BizException("该时段不存在"));
        if (!cap.getOpen() || cap.getRemaining() <= 0) throw new BizException("该时段容量已满");
        List<DoctorSchedule> docs = doctorScheduleRepo
                .findByWorkDateAndTimeSlotAndOnDutyTrue(req.date(), req.timeSlot());
        if (docs.isEmpty()) throw new BizException("该时段无医生排班");

        cap.setBookedCount(cap.getBookedCount() + 1); // @Version 防并发超约
        capacityRepo.save(cap);

        Appointment appt = new Appointment();
        appt.setChild(child);
        appt.setPlan(plan);
        appt.setVaccineCode(vaccineCode);
        appt.setVaccineName(target.getName());
        appt.setDoseNo(plan.getDoseNo());
        appt.setAppointmentDate(req.date());
        appt.setTimeSlot(req.timeSlot());
        appt.setCapacityId(cap.getId());
        appt.setDoctorUserId(docs.get(0).getDoctorUserId());
        appt.setDoctorName(docs.get(0).getDoctorName());
        appt.setReservedBatchNo(batches.get(0).getBatchNo());
        appt.setStatus("BOOKED");
        appt.setUpdatedAt(LocalDateTime.now());
        appt = appointmentRepo.save(appt);

        String subNote = substituted ? "（本苗缺货，同组换苗为 " + target.getName() + "）" : "";
        notifier.notifyRole("NURSE", "INFO", "INFO",
                "新预约：" + child.getName() + " " + appt.getVaccineName() + " 第" + plan.getDoseNo() + "剂",
                req.date() + " " + req.timeSlot() + "，预留批号 " + batches.get(0).getBatchNo() + subNote,
                child, "APPOINTMENT", appt.getId());
        return appt;
    }

    @Transactional
    public Appointment cancel(Long appointmentId, String reason, SysUser operator, boolean byClinic) {
        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new BizException("预约不存在"));
        if (!"BOOKED".equals(appt.getStatus()) && !"CHECKED_IN".equals(appt.getStatus())) {
            throw new BizException("仅已预约/已到诊状态可以取消");
        }
        if (!byClinic) {
            Long uid = appt.getChild().getGuardian() == null ? null : appt.getChild().getGuardian().getUserId();
            if (uid == null || !uid.equals(operator.getId())) {
                throw new BizException("只能取消自己的预约", org.springframework.http.HttpStatus.FORBIDDEN);
            }
        }
        appt.setStatus(byClinic ? "CANCELLED_CLINIC" : "CANCELLED");
        appt.setCancelReason(reason);
        appt.setUpdatedAt(LocalDateTime.now());
        releaseCapacity(appt);
        appointmentRepo.save(appt);

        if (byClinic) {
            Long uid = appt.getChild().getGuardian() == null ? null : appt.getChild().getGuardian().getUserId();
            if (uid != null) {
                notifier.notifyUser(uid, "CANCEL", "WARN",
                        "门诊取消预约通知", "您为 " + appt.getChild().getName() + " 预约的 "
                                + appt.getAppointmentDate() + " " + appt.getTimeSlot() + " "
                                + appt.getVaccineName() + " 已被门诊取消：" + reason + "，请重新预约。",
                        appt.getChild(), "APPOINTMENT", appt.getId());
            }
        } else {
            // 需求：家长临时取消要推给医生、护士、随访人员
            notifier.notifyStaff("CANCEL", "INFO",
                    "家长临时取消：" + appt.getChild().getName(),
                    appt.getChild().getName() + " 的 " + appt.getVaccineName() + " 第" + appt.getDoseNo()
                            + "剂预约（" + appt.getAppointmentDate() + " " + appt.getTimeSlot()
                            + "）被家长临时取消，原因：" + nullToDash(reason)
                            + "。请关注该剂次重新预约与补种提醒。",
                    appt.getChild(), "APPOINTMENT", appt.getId());
        }
        return appt;
    }

    /** 到诊签到（护士） */
    @Transactional
    public Appointment checkIn(Long appointmentId, SysUser nurse) {
        Appointment appt = requireEntity(appointmentId);
        if (!"BOOKED".equals(appt.getStatus())) throw new BizException("仅已预约状态可以签到到诊");
        appt.setStatus("CHECKED_IN");
        appt.setUpdatedAt(LocalDateTime.now());
        return appointmentRepo.save(appt);
    }

    @Transactional
    public Appointment requireEntity(Long id) {
        return appointmentRepo.findById(id).orElseThrow(() -> new BizException("预约不存在"));
    }

    @Transactional
    public void releaseSlot(Long appointmentId) {
        appointmentRepo.findById(appointmentId).ifPresent(this::releaseCapacity);
    }

    private void releaseCapacity(Appointment appt) {
        if (appt.getCapacityId() == null) return;
        capacityRepo.findById(appt.getCapacityId()).ifPresent(c -> {
            c.setBookedCount(Math.max(0, c.getBookedCount() - 1));
            capacityRepo.save(c);
        });
    }

    /** 过夜任务：把过去日期仍 BOOKED 的预约标记爽约，相关剂次回到漏种 */
    @Transactional
    public int markNoShows() {
        LocalDate today = LocalDate.now();
        int n = 0;
        for (Appointment a : appointmentRepo.findByStatusIn(List.of("BOOKED"))) {
            if (a.getAppointmentDate().isBefore(today)) {
                a.setStatus("NO_SHOW");
                a.setCancelReason("未到诊，系统按爽约处理");
                a.setUpdatedAt(LocalDateTime.now());
                releaseCapacity(a);
                appointmentRepo.save(a);
                notifier.notifyStaff("OVERDUE", "WARN",
                        "爽约漏种：" + a.getChild().getName(),
                        a.getChild().getName() + " 预约的 " + a.getVaccineName() + " 第" + a.getDoseNo()
                                + "剂（" + a.getAppointmentDate() + "）未到诊，剂次重新进入漏种补种。",
                        a.getChild(), "APPOINTMENT", a.getId());
                Long uid = a.getChild().getGuardian() == null ? null : a.getChild().getGuardian().getUserId();
                if (uid != null) {
                    notifier.notifyUser(uid, "OVERDUE", "WARN", "爽约提醒",
                            a.getChild().getName() + " 的 " + a.getVaccineName() + " 预约未到诊，请重新预约补种。",
                            a.getChild(), "APPOINTMENT", a.getId());
                }
                n++;
            }
        }
        return n;
    }

    private String nullToDash(String s) { return s == null || s.isBlank() ? "未填写" : s; }
}
