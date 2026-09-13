package com.community.vax.service;

import com.community.vax.entity.*;
import com.community.vax.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 接种/补种计划生成核心。
 * 依据：出生日期、免疫程序模板（建议月龄/最早月龄/年龄上限/最短间隔）、
 * 既往接种（本地 + 迁入已核验，同疫苗组序贯）、待核验/模糊迁入记录（防重复接种）、
 * 禁忌症、过敏史、疫苗库存（含同组换苗）。
 */
@Service
public class PlanService {

    public static final List<String> BOOKABLE = List.of("DUE", "OVERDUE");

    private final VaccinationPlanRepository planRepo;
    private final ScheduleTemplateRepository templateRepo;
    private final VaccineRepository vaccineRepo;
    private final PriorVaccinationRepository priorRepo;
    private final ContraindicationRepository contraRepo;
    private final AllergyRepository allergyRepo;
    private final ChildRepository childRepo;
    private final StockService stockService;
    private final NotificationService notifier;

    public PlanService(VaccinationPlanRepository planRepo, ScheduleTemplateRepository templateRepo,
                       VaccineRepository vaccineRepo, PriorVaccinationRepository priorRepo,
                       ContraindicationRepository contraRepo, AllergyRepository allergyRepo,
                       ChildRepository childRepo, StockService stockService,
                       NotificationService notifier) {
        this.planRepo = planRepo;
        this.templateRepo = templateRepo;
        this.vaccineRepo = vaccineRepo;
        this.priorRepo = priorRepo;
        this.contraRepo = contraRepo;
        this.allergyRepo = allergyRepo;
        this.childRepo = childRepo;
        this.stockService = stockService;
        this.notifier = notifier;
    }

    /** 程序行：模板 + 所属疫苗组 + 组内序号(0基) + 对应的已完成既往接种 */
    public record Line(ScheduleTemplate template, String group, int indexInGroup, PriorVaccination completion) {}

    /** 计算某儿童当前全部程序行（含已完成与待种），全局按建议月龄排序 */
    @Transactional(readOnly = true)
    public List<Line> computeLines(Child child) {
        Map<String, Vaccine> vMap = new HashMap<>();
        vaccineRepo.findAll().forEach(v -> vMap.put(v.getCode(), v));

        // 同疫苗组的程序模板按建议月龄/剂序排队（支持 IPV→bOPV 这类序贯程序）
        Map<String, List<ScheduleTemplate>> groupTemplates = new TreeMap<>();
        for (ScheduleTemplate t : templateRepo.findAll()) {
            groupTemplates.computeIfAbsent(t.getVaccine().getVaccineGroup(), k -> new ArrayList<>()).add(t);
        }
        groupTemplates.values().forEach(list -> list.sort(
                Comparator.comparingInt(ScheduleTemplate::getRecommendedAgeMonths)
                        .thenComparingInt(ScheduleTemplate::getDoseNo)
                        .thenComparing(t -> t.getVaccine().getCode())));

        // 有效既往接种（本地或迁入已核验），按疫苗组归集、按日期排序
        Map<String, List<PriorVaccination>> groupPriors = new HashMap<>();
        priorRepo.findByChildIdOrderByVaccinationDateAsc(child.getId()).stream()
                .filter(p -> "LOCAL".equals(p.getSource()) || "CONFIRMED".equals(p.getVerifyStatus()))
                .filter(p -> p.getVaccineCode() != null && vMap.containsKey(p.getVaccineCode()))
                .sorted(Comparator.comparing(PriorVaccination::getVaccinationDate)
                        .thenComparing(PriorVaccination::getDoseNo))
                .forEach(p -> groupPriors
                        .computeIfAbsent(vMap.get(p.getVaccineCode()).getVaccineGroup(), k -> new ArrayList<>())
                        .add(p));

        List<Line> lines = new ArrayList<>();
        for (Map.Entry<String, List<ScheduleTemplate>> e : groupTemplates.entrySet()) {
            List<PriorVaccination> done = groupPriors.getOrDefault(e.getKey(), List.of());
            List<ScheduleTemplate> ts = e.getValue();
            for (int i = 0; i < ts.size(); i++) {
                lines.add(new Line(ts.get(i), e.getKey(), i, i < done.size() ? done.get(i) : null));
            }
        }
        lines.sort(Comparator.comparingInt(l -> l.template().getRecommendedAgeMonths()));
        return lines;
    }

    /** 重新生成并持久化某儿童计划，同时产生漏种/库存/禁忌/复核等状态推送 */
    @Transactional
    public List<VaccinationPlan> regenerate(Long childId) {
        Child child = childRepo.findById(childId)
                .orElseThrow(() -> new com.community.vax.common.BizException("儿童不存在"));
        LocalDate today = LocalDate.now();
        List<Line> lines = computeLines(child);

        // 每组内已确认接种的日期（有序），用于最短间隔
        Map<String, List<LocalDate>> groupDoneDates = new HashMap<>();
        // 每组已确认剂次数、待核验（待核验/模糊）记录
        Map<String, Integer> groupDoneCount = new HashMap<>();
        Map<String, List<PriorVaccination>> groupPending = new HashMap<>();
        Map<String, Vaccine> vMap = new HashMap<>();
        vaccineRepo.findAll().forEach(v -> vMap.put(v.getCode(), v));
        List<PriorVaccination> allPriors = priorRepo.findByChildIdOrderByVaccinationDateAsc(childId);
        // 每组模板剂次数、已确认剂次数（用于程序外追加剂次统计）
        Map<String, Integer> groupTemplateCount = new HashMap<>();
        Map<String, Integer> groupConfirmedCount = new HashMap<>();
        for (Line l : lines) {
            groupTemplateCount.merge(l.group(), 1, Integer::sum);
            if (l.completion() != null) {
                groupDoneDates.computeIfAbsent(l.group(), k -> new ArrayList<>())
                        .add(l.completion().getVaccinationDate());
                groupDoneCount.merge(l.group(), 1, Integer::sum);
            }
        }
        groupDoneDates.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        for (PriorVaccination p : allPriors) {
            boolean confirmed = "LOCAL".equals(p.getSource()) || "CONFIRMED".equals(p.getVerifyStatus());
            if (p.getVaccineCode() != null && vMap.containsKey(p.getVaccineCode())) {
                String g = vMap.get(p.getVaccineCode()).getVaccineGroup();
                if (confirmed) {
                    groupConfirmedCount.merge(g, 1, Integer::sum);
                } else if (List.of("UNVERIFIED", "AMBIGUOUS").contains(p.getVerifyStatus())) {
                    groupPending.computeIfAbsent(g, k -> new ArrayList<>()).add(p);
                }
            }
        }
        // 程序外追加剂次（外地多打/自费苗）
        Map<String, Integer> groupExtra = new HashMap<>();
        groupConfirmedCount.forEach((g, n) -> {
            int extra = n - groupTemplateCount.getOrDefault(g, 0);
            if (extra > 0) groupExtra.put(g, extra);
        });
        // 被驳回的迁入记录（按组），其原因要随补种剂次展示给护士
        Map<String, List<PriorVaccination>> groupRejected = new HashMap<>();
        for (PriorVaccination p : allPriors) {
            if ("REJECTED".equals(p.getVerifyStatus())
                    && p.getVaccineCode() != null && vMap.containsKey(p.getVaccineCode())) {
                groupRejected.computeIfAbsent(vMap.get(p.getVaccineCode()).getVaccineGroup(),
                        k -> new ArrayList<>()).add(p);
            }
        }

        List<Contraindication> contras = contraRepo.findByChildIdAndActiveTrue(childId);
        List<Allergy> allergies = allergyRepo.findByChildId(childId);
        boolean migrationIncomplete = priorRepo.findByChildIdOrderByVaccinationDateAsc(childId).stream()
                .anyMatch(p -> List.of("UNVERIFIED", "AMBIGUOUS").contains(p.getVerifyStatus()));

        Map<String, VaccinationPlan> existing = new HashMap<>();
        for (VaccinationPlan p : planRepo.findByChildIdOrderByDueDateAsc(childId)) {
            existing.put(p.getVaccineCode() + "#" + p.getDoseNo(), p);
        }

        List<VaccinationPlan> result = new ArrayList<>();
        for (Line line : lines) {
            ScheduleTemplate t = line.template();
            Vaccine v = t.getVaccine();
            String key = v.getCode() + "#" + t.getDoseNo();
            VaccinationPlan plan = existing.getOrDefault(key, new VaccinationPlan());
            plan.setChild(child);
            plan.setVaccineCode(v.getCode());
            plan.setVaccineName(v.getName());
            plan.setDoseNo(t.getDoseNo());

            LocalDate due = child.getBirthDate().plusMonths(t.getRecommendedAgeMonths());
            LocalDate earliest = child.getBirthDate().plusMonths(t.getMinAgeMonths());
            LocalDate ageLimit = child.getBirthDate().plusMonths(t.getMaxAgeMonths());

            // 与本组上一剂的最短间隔
            if (line.indexInGroup() > 0 && t.getMinIntervalDays() != null && t.getMinIntervalDays() > 0) {
                List<LocalDate> doneDates = groupDoneDates.getOrDefault(line.group(), List.of());
                if (line.indexInGroup() <= doneDates.size()) {
                    LocalDate last = doneDates.get(line.indexInGroup() - 1);
                    LocalDate intervalOk = last.plusDays(t.getMinIntervalDays());
                    if (intervalOk.isAfter(earliest)) earliest = intervalOk;
                }
            }

            plan.setDueDate(due);
            plan.setEarliestDate(earliest);
            plan.setAgeLimitDate(ageLimit);

            List<String> remarks = new ArrayList<>();
            List<String> adjust = new ArrayList<>();
            String status;

            // 同疫苗组只要存在待核验/模糊迁入记录，未完成剂次一律挂起：
            // 序贯关系未核实前不能安全判定可否接种，防止重复接种或越剂漏种
            int doneN = groupDoneCount.getOrDefault(line.group(), 0);
            int pendingN = groupPending.getOrDefault(line.group(), List.of()).size();
            boolean possiblyCoveredByPending = line.completion() == null && pendingN > 0
                    && line.indexInGroup() >= doneN;

            if (line.completion() != null) {
                PriorVaccination c = line.completion();
                status = "DONE";
                plan.setCompletedDate(c.getVaccinationDate());
                plan.setCompletedVaccineCode(c.getVaccineCode());
                plan.setCompletedVaccineName(c.getVaccineName());
                plan.setCompletedBatchNo(c.getBatchNo());
                if (!c.getVaccineCode().equals(v.getCode())) {
                    remarks.add("同组替代苗完成：" + c.getVaccineName()
                            + (c.getBatchNo() == null ? "" : "（批号 " + c.getBatchNo() + "）"));
                    adjust.add("本剂跳过：使用同组替代苗 " + c.getVaccineName() + " 完成，等效计入第" + t.getDoseNo() + "剂");
                } else if ("MIGRATED".equals(c.getSource())) {
                    remarks.add("迁入前接种（接种本已核验）");
                    adjust.add("按外地接种本核验通过计入第" + t.getDoseNo() + "剂（" + c.getVaccinationDate()
                            + "，原单位 " + nullToDash(c.getClinicName()) + "，批号 " + nullToDash(c.getBatchNo()) + "），本剂不再重复接种");
                } else {
                    adjust.add("第" + t.getDoseNo() + "剂已于 " + c.getVaccinationDate() + " 在本门诊完成");
                }
            } else if (today.isAfter(ageLimit)) {
                status = "EXPIRED";
                remarks.add("已超过 " + t.getMaxAgeMonths() + " 月龄补种年龄上限，不再安排补种");
                adjust.add("超过补种年龄上限，本剂不再补种");
            } else if (possiblyCoveredByPending) {
                status = "WAIT_VERIFY";
                List<PriorVaccination> pend = groupPending.get(line.group());
                PriorVaccination maybe = pend.get(Math.min(line.indexInGroup() - doneN, pend.size() - 1));
                String why = "存在" + ("AMBIGUOUS".equals(maybe.getVerifyStatus()) ? "模糊" : "待核验")
                        + "的迁入记录可能已覆盖本剂（" + maybe.getVaccineName() + "，登记日期 "
                        + maybe.getVaccinationDate() + "）";
                remarks.add(why + "，人工核验前不可预约，避免重复接种");
                adjust.add(why + "；医生核验通过则跳过本剂，驳回则追加补种");
            } else if (line.indexInGroup() > 0 && doneN < line.indexInGroup()) {
                status = "WAIT_INTERVAL";
                remarks.add("同疫苗组前序剂次尚未完成，不能越剂接种");
                adjust.add("前序剂次未完成，需先补种第" + (doneN + 1) + "剂后再安排本剂");
            } else if (hasContraindication(contras, v.getCode())) {
                status = "CONTRA";
                remarks.add("存在活动期禁忌症，暂缓接种，需医生评估");
                adjust.add("禁忌症暂缓，需医生评估后决定是否追加");
            } else if (!Boolean.TRUE.equals(plan.getDoctorApproved()) && allergyMatches(allergies, v)) {
                status = "REVIEW";
                remarks.add("过敏史与疫苗成分（" + nullToDash(v.getComponents()) + "）相关，需预防接种医生复核");
                adjust.add("过敏史待医生复核，复核通过后追加接种");
            } else if (today.isBefore(earliest)) {
                status = "WAIT_INTERVAL";
                remarks.add("未满足最短间隔/起始月龄，最早 " + earliest + " 可接种");
                adjust.add("按最短间隔顺延，最早 " + earliest + " 开放预约");
            } else {
                boolean selfStock = stockService.inStock(v.getCode());
                List<String> subs = stockService.substitutableVaccineCodes(v.getCode());
                if (!selfStock && subs.isEmpty()) {
                    status = "WAIT_STOCK";
                    remarks.add("本苗及同组替代苗均无可用库存，等待到货后自动开放预约");
                    adjust.add("因缺货延后，到货后自动追加开放预约");
                } else {
                    status = today.isAfter(due) ? "OVERDUE" : "DUE";
                    List<PriorVaccination> rej = groupRejected.getOrDefault(line.group(), List.of());
                    if (!rej.isEmpty()) {
                        PriorVaccination r = rej.get(0);
                        remarks.add("外地接种本相关记录不予采信（" + nullToDash(r.getReviewNote()) + "），需在本门诊补种");
                        adjust.add("原外地记录第" + (r.getDoseNo() == null ? "?(原件:" + nullToDash(r.getRawDoseText()) + ")" : r.getDoseNo())
                                + "剂被驳回（原因：" + nullToDash(r.getReviewNote()) + "），该剂须在本门诊补种");
                    } else if (status.equals("OVERDUE")) {
                        remarks.add("已漏种，应种日期 " + due + "，请尽快补种");
                        // 迁入儿童前序剂次经核验计入时，说明本剂为何追加
                        boolean hasConfirmedMigration = allPriors.stream()
                                .anyMatch(p -> "MIGRATED".equals(p.getSource())
                                        && "CONFIRMED".equals(p.getVerifyStatus()));
                        if (line.indexInGroup() > 0 && hasConfirmedMigration) {
                            adjust.add("外地接种本核验后，按免疫程序追加补种本剂，应种 " + due + "，已满足最短间隔");
                        } else {
                            adjust.add("漏种追加剂次，满足最短间隔与年龄要求");
                        }
                    } else {
                        adjust.add("常规第" + t.getDoseNo() + "剂，到龄可接种");
                    }
                    if (!selfStock) {
                        remarks.add("本苗当前缺货，可使用同组替代苗：" + String.join("/", subs));
                        adjust.add("本苗缺货时可使用同组替代苗：" + String.join("/", subs));
                    }
                }
            }

            // 程序外追加剂次（外地多打/自费苗），在最后一剂的说明里提示护士
            Integer extra = groupExtra.get(line.group());
            if (extra != null && extra > 0
                    && line.indexInGroup() == groupTemplateCount.get(line.group()) - 1
                    && "DONE".equals(status)) {
                remarks.add("另有 " + extra + " 剂程序外接种记录，已归档不重复安排");
                adjust.add("外地记录含 " + extra + " 剂程序外接种，仅归档不重复接种");
            }

            if (migrationIncomplete && !"DONE".equals(status) && !"WAIT_VERIFY".equals(status)) {
                remarks.add("该儿童存在未核验的迁入接种记录，剂次安排以核验结果为准");
            }
            if (Boolean.TRUE.equals(plan.getDoctorApproved()) && !"DONE".equals(status)) {
                remarks.add("医生已复核同意接种"
                        + (plan.getDoctorApprovedNote() == null ? "" : "：" + plan.getDoctorApprovedNote()));
            }

            plan.setStatus(status);
            plan.setRemark(String.join("；", remarks));
            plan.setAdjustReason(String.join("；", adjust.stream().filter(s -> s != null && !s.isBlank()).toList()));
            plan.setUpdatedAt(LocalDateTime.now());
            planRepo.save(plan);
            result.add(plan);

            pushStatusNotifications(child, plan, status);
        }

        if (migrationIncomplete) {
            notifier.notifyStaff("MIGRATION", "WARN",
                    "迁入接种记录待人工核验：" + child.getName(),
                    "儿童 " + child.getName() + " 上传/登记的外地接种记录存在待核验或模糊条目，相关剂次已置为“待核验”，"
                            + "请在人工核验队列核对疫苗名称、剂次、日期、批号与原件，避免重复接种或漏种。",
                    child, "CHILD", child.getId());
        }
        return result;
    }

    private String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private boolean hasContraindication(List<Contraindication> list, String vaccineCode) {
        return list.stream().anyMatch(c -> "ALL".equals(c.getVaccineCode()) || c.getVaccineCode().equals(vaccineCode));
    }

    private boolean allergyMatches(List<Allergy> allergies, Vaccine v) {
        if (v.getComponents() == null || v.getComponents().isBlank()) return false;
        for (String comp : v.getComponents().split(",")) {
            String kw = comp.trim();
            if (kw.isEmpty()) continue;
            for (Allergy a : allergies) {
                String text = (a.getAllergen() == null ? "" : a.getAllergen())
                        + " " + (a.getReaction() == null ? "" : a.getReaction())
                        + " " + (a.getNote() == null ? "" : a.getNote());
                if (text.contains(kw)) return true;
            }
        }
        return false;
    }

    private void pushStatusNotifications(Child child, VaccinationPlan plan, String status) {
        String label = plan.getVaccineName() + " 第" + plan.getDoseNo() + "剂";
        Long guardianUserId = child.getGuardian() == null ? null : child.getGuardian().getUserId();

        switch (status) {
            case "WAIT_VERIFY" -> {
                if (guardianUserId != null) {
                    notifier.notifyUser(guardianUserId, "MIGRATION", "INFO",
                            "迁入接种记录核验中：" + child.getName() + " " + label,
                            label + " 因外地接种记录待人工核验暂不可预约，核验通过/补录完成后将自动开放，请留意通知。",
                            child, "PLAN", plan.getId());
                }
                notifier.notifyStaff("MIGRATION", "WARN",
                        "迁入记录待核验：" + child.getName() + " " + label,
                        child.getName() + " " + label + " 存在可能重复的迁入接种记录，请核对接种本原件后确认（跳过）或驳回（追加补种）。",
                        child, "PLAN", plan.getId());
            }
            case "OVERDUE" -> {
                String content = label + " 已漏种（应种 " + plan.getDueDate() + "），满足最短间隔要求，请尽快在线预约补种；补种年龄上限 "
                        + plan.getAgeLimitDate() + "。";
                if (guardianUserId != null) {
                    notifier.notifyUser(guardianUserId, "OVERDUE", "WARN",
                            "漏种提醒：" + child.getName() + " " + label, content, child, "PLAN", plan.getId());
                }
                notifier.notifyStaff("OVERDUE", "WARN",
                        "漏种：" + child.getName() + " " + label, content, child, "PLAN", plan.getId());
            }
            case "DUE" -> {
                if (guardianUserId != null) {
                    notifier.notifyUser(guardianUserId, "REMIND", "INFO",
                            "接种提醒：" + child.getName() + " " + label,
                            label + " 已到建议接种时间（" + plan.getDueDate()
                                    + "），已满足最短间隔与年龄要求，可预约时段。",
                            child, "PLAN", plan.getId());
                }
            }
            case "WAIT_STOCK" -> {
                notifier.notifyStaff("STOCK", "WARN",
                        "库存不足影响接种：" + child.getName() + " " + label,
                        label + " 本苗与同组替代苗均无可用批号，需尽快补货；到货后系统自动开放可约时段。",
                        child, "PLAN", plan.getId());
                notifier.notifyRole("ADMIN", "STOCK", "WARN",
                        "库存不足：" + plan.getVaccineName(),
                        plan.getVaccineName() + " 无可用批号，已有儿童等待接种（如 " + child.getName() + "）。",
                        child, "PLAN", plan.getId());
            }
            case "CONTRA" -> notifier.notifyStaff("CONTRA", "INFO",
                    "禁忌症暂缓：" + child.getName() + " " + label,
                    label + " 因活动期禁忌症暂缓，需预防接种医生评估后安排。", child, "PLAN", plan.getId());
            case "REVIEW" -> notifier.notifyStaff("REVIEW", "URGENT",
                    "过敏史待医生复核：" + child.getName() + " " + label,
                    label + " 涉及儿童过敏史成分，预约/接种前须预防接种医生复核同意。",
                    child, "PLAN", plan.getId());
            default -> { }
        }
    }

    @Transactional(readOnly = true)
    public List<VaccinationPlan> listForChild(Long childId) {
        return planRepo.findByChildIdOrderByDueDateAsc(childId);
    }

    @Transactional
    public void doctorApprove(Long planId, String note, SysUser doctor) {
        VaccinationPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new com.community.vax.common.BizException("计划不存在"));
        if (!"REVIEW".equals(plan.getStatus()) && !"CONTRA".equals(plan.getStatus())) {
            throw new com.community.vax.common.BizException("仅“待医生复核/禁忌暂缓”的计划需要复核");
        }
        LocalDate today = LocalDate.now();
        if (today.isAfter(plan.getAgeLimitDate())) {
            throw new com.community.vax.common.BizException("已超过补种年龄上限");
        }
        plan.setStatus(today.isAfter(plan.getDueDate()) ? "OVERDUE" : "DUE");
        plan.setDoctorApproved(true);
        plan.setDoctorApprovedNote(note);
        plan.setRemark("医生 " + doctor.getName() + " 已复核同意接种"
                + (note == null || note.isBlank() ? "" : "：" + note)
                + "；复核日期 " + today);
        plan.setUpdatedAt(LocalDateTime.now());
        planRepo.save(plan);
    }
}
