package com.community.vax.config;

import com.community.vax.common.Role;
import com.community.vax.entity.*;
import com.community.vax.repo.*;
import com.community.vax.service.PlanService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 演示数据初始化（幂等：已存在 admin 账号则跳过）。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final SysUserRepository userRepo;
    private final ParentProfileRepository parentRepo;
    private final VaccineRepository vaccineRepo;
    private final ScheduleTemplateRepository templateRepo;
    private final VaccineBatchRepository batchRepo;
    private final ClinicCapacityRepository capacityRepo;
    private final DoctorScheduleRepository scheduleRepo;
    private final ChildRepository childRepo;
    private final AllergyRepository allergyRepo;
    private final ContraindicationRepository contraRepo;
    private final PriorVaccinationRepository priorRepo;
    private final VaccinationRecordRepository recordRepo;
    private final AefiCaseRepository aefiRepo;
    private final FollowUpRepository followRepo;
    private final ConsultationRepository consultRepo;
    private final PlanService planService;
    private final PasswordEncoder encoder;

    public DataSeeder(SysUserRepository userRepo, ParentProfileRepository parentRepo,
                      VaccineRepository vaccineRepo, ScheduleTemplateRepository templateRepo,
                      VaccineBatchRepository batchRepo, ClinicCapacityRepository capacityRepo,
                      DoctorScheduleRepository scheduleRepo, ChildRepository childRepo,
                      AllergyRepository allergyRepo, ContraindicationRepository contraRepo,
                      PriorVaccinationRepository priorRepo, VaccinationRecordRepository recordRepo,
                      AefiCaseRepository aefiRepo, FollowUpRepository followRepo,
                      ConsultationRepository consultRepo, PlanService planService,
                      PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.parentRepo = parentRepo;
        this.vaccineRepo = vaccineRepo;
        this.templateRepo = templateRepo;
        this.batchRepo = batchRepo;
        this.capacityRepo = capacityRepo;
        this.scheduleRepo = scheduleRepo;
        this.childRepo = childRepo;
        this.allergyRepo = allergyRepo;
        this.contraRepo = contraRepo;
        this.priorRepo = priorRepo;
        this.recordRepo = recordRepo;
        this.aefiRepo = aefiRepo;
        this.followRepo = followRepo;
        this.consultRepo = consultRepo;
        this.planService = planService;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepo.findByUsername("admin").isPresent()) return;

        // ---------- 账号 ----------
        SysUser admin = user("admin", "admin123", "王管理", Role.ADMIN, null, "门诊管理员");
        SysUser doctor = user("doctor", "doctor123", "李建华", Role.DOCTOR, null, "预防接种医生");
        SysUser nurse = user("nurse", "nurse123", "赵敏", Role.NURSE, null, "接种护士");
        SysUser follow = user("followup", "follow123", "孙悦", Role.FOLLOWUP, null, "异常反应随访员");
        userRepo.saveAll(List.of(admin, doctor, nurse, follow));

        ParentProfile p1 = new ParentProfile();
        p1.setName("周敏"); p1.setPhone("13800000001"); p1.setAddress("阳光社区 3 栋 201");
        p1 = parentRepo.save(p1);
        SysUser parent1 = user("parent1", "parent123", "周敏", Role.PARENT, p1.getId(), "家长");
        userRepo.save(parent1);
        p1.setUserId(parent1.getId());
        parentRepo.save(p1);

        ParentProfile p2 = new ParentProfile();
        p2.setName("吴芳"); p2.setPhone("13800000002"); p2.setAddress("阳光社区 7 栋 502");
        p2 = parentRepo.save(p2);
        SysUser parent2 = user("parent2", "parent123", "吴芳", Role.PARENT, p2.getId(), "家长");
        userRepo.save(parent2);
        p2.setUserId(parent2.getId());
        parentRepo.save(p2);

        // ---------- 疫苗目录与免疫程序 ----------
        // code, name, group, category, totalDoses, components
        Vaccine hepb = vaccine("HEPB", "重组乙型肝炎疫苗(酵母)", "HEPB", "免疫规划", 3, "酵母");
        Vaccine bcg = vaccine("BCG", "皮内注射用卡介苗", "BCG", "免疫规划", 1, null);
        Vaccine ipv = vaccine("IPV", "脊髓灰质炎灭活疫苗", "POLIO", "免疫规划", 1, null);
        Vaccine opv = vaccine("OPV", "口服二价脊灰减毒活疫苗(bOPV)", "POLIO", "免疫规划", 2, null);
        Vaccine dtp = vaccine("DTP", "吸附无细胞百白破联合疫苗", "DTP", "免疫规划", 4, null);
        Vaccine mr = vaccine("MR", "麻疹风疹联合减毒活疫苗", "MEASLES", "免疫规划", 1, "明胶,鸡蛋");
        Vaccine mmr = vaccine("MMR", "麻腮风联合减毒活疫苗", "MEASLES", "免疫规划", 1, "明胶,鸡蛋");
        Vaccine je = vaccine("JE", "乙型脑炎减毒活疫苗", "JE", "免疫规划", 2, null);
        Vaccine var = vaccine("VAR", "水痘减毒活疫苗", "VAR", "非免疫规划", 2, "明胶");
        vaccineRepo.saveAll(List.of(hepb, bcg, ipv, opv, dtp, mr, mmr, je, var));

        // vaccine, doseNo, recM, minM, maxM, intervalDays
        template(hepb, 1, 0, 0, 24, 0);
        template(hepb, 2, 1, 1, 24, 28);
        template(hepb, 3, 6, 6, 24, 60);
        template(bcg, 1, 0, 0, 12, 0);
        template(ipv, 1, 2, 2, 72, 0);
        template(opv, 2, 3, 3, 72, 28);
        template(opv, 3, 4, 4, 72, 28);
        template(dtp, 1, 3, 3, 72, 0);
        template(dtp, 2, 4, 4, 72, 28);
        template(dtp, 3, 5, 5, 72, 60);
        template(dtp, 4, 24, 18, 72, 180);
        template(mr, 1, 8, 8, 144, 0);
        template(mmr, 2, 18, 18, 144, 90);
        template(je, 1, 8, 8, 72, 0);
        template(je, 2, 24, 18, 72, 365);
        template(var, 1, 12, 12, 144, 0);
        template(var, 2, 48, 15, 144, 365);

        // ---------- 批号库存（含缺货/冷链中断/过期/低库存场景） ----------
        LocalDate today = LocalDate.now();
        batch("HEPB-202601", "HEPB", "重组乙型肝炎疫苗(酵母)", 30, 8, today.plusMonths(8), today.minusDays(20), "NORMAL", null);
        batch("HEPB-202408", "HEPB", "重组乙型肝炎疫苗(酵母)", 5, 8, today.minusMonths(2), today.minusMonths(20), "NORMAL", "已过期，仅留存不可发放");
        batch("BCG-202502", "BCG", "皮内注射用卡介苗", 5, 8, today.plusMonths(3), today.minusMonths(60), "NORMAL", null);
        batch("IPV-202509", "IPV", "脊髓灰质炎灭活疫苗", 0, 6, today.plusMonths(5), today.minusMonths(90), "NORMAL", "本批次已用完，IPV 暂时缺货，可用同组 bOPV 替代");
        batch("OPV-202603", "OPV", "口服二价脊灰减毒活疫苗(bOPV)", 20, 8, today.plusMonths(6), today.minusDays(10), "NORMAL", null);
        batch("DTP-202602", "DTP", "吸附无细胞百白破联合疫苗", 25, 8, today.plusMonths(10), today.minusDays(15), "NORMAL", null);
        batch("MR-202601", "MR", "麻疹风疹联合减毒活疫苗", 8, 6, today.plusMonths(5), today.minusDays(30), "NORMAL", null);
        batch("MMR-202509", "MMR", "麻腮风联合减毒活疫苗", 0, 6, today.plusMonths(4), today.minusMonths(12), "NORMAL", "MMR 缺货，同组 MR 有库存可评估替代");
        batch("VAR-202507", "VAR", "水痘减毒活疫苗", 18, 6, today.plusMonths(6), today.minusMonths(3), "BROKEN", "冷链温度记录中断 2 小时，已封存待处理");

        // ---------- 未来 10 天门诊容量与李医生排班 ----------
        String[] slots = {"08:30-09:00", "09:00-09:30", "10:00-10:30", "14:00-14:30"};
        int[] caps = {20, 20, 20, 15};
        for (int d = 0; d < 10; d++) {
            LocalDate date = today.plusDays(d);
            if (date.getDayOfWeek().getValue() == 7) continue; // 周日休诊
            for (int i = 0; i < slots.length; i++) {
                ClinicCapacity c = new ClinicCapacity();
                c.setClinicDate(date); c.setTimeSlot(slots[i]);
                c.setMaxCapacity(caps[i]); c.setBookedCount(0); c.setOpen(true);
                capacityRepo.save(c);
                // 下午最后一个时段不排班，用于演示“无医生排班”不可约
                if (i < 3) {
                    DoctorSchedule ds = new DoctorSchedule();
                    ds.setDoctorUserId(doctor.getId()); ds.setDoctorName(doctor.getName());
                    ds.setWorkDate(date); ds.setTimeSlot(slots[i]); ds.setOnDuty(true);
                    scheduleRepo.save(ds);
                }
            }
        }

        // ---------- 儿童 A：王小明，迁入、漏种多、酵母过敏待复核 ----------
        Child a = new Child();
        a.setName("王小明"); a.setGender("M"); a.setBirthDate(LocalDate.of(2025, 10, 20));
        a.setIdCardNo("儿童证件号 20251020A"); a.setMoveInDate(LocalDate.of(2026, 8, 15));
        a.setMigrationNote("外地迁入，接种证部分记录待核验");
        a.setGuardian(p1);
        a = childRepo.save(a);

        Allergy al = new Allergy();
        al.setChild(a); al.setAllergen("酵母"); al.setReaction("食用含酵母辅食后口周皮疹");
        al.setNote("家长自述，与乙肝疫苗(酵母)成分相关");
        allergyRepo.save(al);

        prior(a, hepb, 1, LocalDate.of(2025, 10, 20), "HEPB-OLD-01", "外地县医院", "MIGRATED", true, "出生接种");
        prior(a, bcg, 1, LocalDate.of(2025, 10, 21), "BCG-OLD-01", "外地县医院", "MIGRATED", false, "接种证信息不全，待核验");
        prior(a, hepb, 2, LocalDate.of(2025, 11, 25), "HEPB-OLD-02", "外地社区卫生服务中心", "MIGRATED", true, null);

        // ---------- 儿童 C：王二宝，小月龄，演示间隔未满 ----------
        Child c = new Child();
        c.setName("王二宝"); c.setGender("F"); c.setBirthDate(LocalDate.of(2026, 8, 1));
        c.setIdCardNo("儿童证件号 20260801C"); c.setGuardian(p1);
        c = childRepo.save(c);

        // ---------- 儿童 B：吴朵朵，本地接种史较完整，有在跟 AEFI + 近期发热 ----------
        Child b = new Child();
        b.setName("吴朵朵"); b.setGender("F"); b.setBirthDate(LocalDate.of(2024, 3, 10));
        b.setIdCardNo("儿童证件号 20240310B"); b.setGuardian(p2);
        b.setHealthStatus("3 天前发热 38.2℃，目前体温正常但家长仍担心");
        b.setHealthUpdatedAt(java.time.LocalDateTime.now().minusDays(1));
        b = childRepo.save(b);

        Contraindication ci = new Contraindication();
        ci.setChild(b); ci.setContraType("活疫苗接种禁忌评估中");
        ci.setDescription("既往高热惊厥史，减毒活疫苗需医生评估");
        ci.setVaccineCode("VAR"); ci.setActive(true);
        contraRepo.save(ci);

        // 本地既往接种（source=LOCAL，直接计入程序）
        prior(b, hepb, 1, LocalDate.of(2024, 3, 10), "HEPB-202301", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, hepb, 2, LocalDate.of(2024, 4, 12), "HEPB-202301", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, hepb, 3, LocalDate.of(2024, 9, 15), "HEPB-202302", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, bcg, 1, LocalDate.of(2024, 3, 11), "BCG-202301", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, ipv, 1, LocalDate.of(2024, 5, 18), "IPV-202305", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, opv, 2, LocalDate.of(2024, 6, 20), "OPV-202306", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, opv, 3, LocalDate.of(2024, 7, 22), "OPV-202306", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, dtp, 1, LocalDate.of(2024, 6, 20), "DTP-202305", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, dtp, 2, LocalDate.of(2024, 7, 22), "DTP-202305", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, dtp, 3, LocalDate.of(2024, 8, 25), "DTP-202305", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, mr, 1, LocalDate.of(2024, 11, 15), "MR-202311", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, mmr, 2, LocalDate.of(2025, 9, 12), "MMR-202509", "本社区卫生服务中心", "LOCAL", true, null);
        prior(b, je, 1, LocalDate.of(2024, 11, 15), "JE-202311", "本社区卫生服务中心", "LOCAL", true, null);

        // 历史 MMR 接种记录 + AEFI 个案（发热皮疹，随访中，下次随访今天到期）
        VaccinationRecord mmrRec = new VaccinationRecord();
        mmrRec.setChild(b);
        mmrRec.setVaccineCode("MMR"); mmrRec.setVaccineName("麻腮风联合减毒活疫苗");
        mmrRec.setDoseNo(2); mmrRec.setBatchNo("MMR-202509");
        mmrRec.setVaccineExpiryDate(today.plusMonths(4));
        mmrRec.setVaccinationDate(LocalDate.of(2025, 9, 12));
        mmrRec.setNurseUserId(nurse.getId()); mmrRec.setNurseName(nurse.getName());
        mmrRec.setIdentityVerified(true); mmrRec.setBatchVerified(true); mmrRec.setConsentSigned(true);
        mmrRec.setRecentFeverChecked(true); mmrRec.setContraindicationChecked(true);
        mmrRec.setObservationMinutes(30);
        mmrRec.setObservationStartTime(java.time.LocalDateTime.of(2025, 9, 12, 9, 30));
        mmrRec.setObservationEndTime(java.time.LocalDateTime.of(2025, 9, 12, 10, 0));
        mmrRec.setOnSiteReaction("无异常"); mmrRec.setGuardianConfirmed(true);
        mmrRec.setStatus("COMPLETED");
        recordRepo.save(mmrRec);

        AefiCase aefi = new AefiCase();
        aefi.setRecord(mmrRec); aefi.setChild(b);
        aefi.setVaccineCode("MMR"); aefi.setVaccineName("麻腮风联合减毒活疫苗");
        aefi.setBatchNo("MMR-202509"); aefi.setDoseNo(2);
        aefi.setVaccinationDate(LocalDate.of(2025, 9, 12));
        aefi.setSymptoms("发热 38.8℃、全身散在皮疹");
        aefi.setOnsetDate(LocalDate.of(2025, 9, 19));
        aefi.setSymptomDetail("接种后第 7 天发热伴皮疹，精神尚可");
        aefi.setStatus("FOLLOWING");
        aefi.setSource("家长咨询");
        aefi.setCreatedBy(doctor.getId()); aefi.setCreatedByName(doctor.getName());
        aefiRepo.save(aefi);

        FollowUp fu = new FollowUp();
        fu.setAefi(aefi); fu.setFollowDate(today.minusDays(3)); fu.setMethod("PHONE");
        fu.setTemperature(37.4); fu.setSymptomsStatus("皮疹消退中，仍有低热");
        fu.setAdvice("多饮水观察，如反复高热及时就诊"); fu.setOutcome("ONGOING");
        fu.setNextFollowDate(today);
        fu.setFollowUserId(follow.getId()); fu.setFollowUserName(follow.getName());
        followRepo.save(fu);

        // ---------- 咨询：一条待回复 + 一条已回复并标记接种前提醒 ----------
        Consultation open = new Consultation();
        open.setChild(a); open.setTopic("接种后手臂红肿");
        open.setQuestion("孩子上次打完针胳膊红肿了两天，下次接种需要注意什么？");
        open.setStatus("OPEN");
        consultRepo.save(open);

        Consultation replied = new Consultation();
        replied.setChild(b); replied.setTopic("高热惊厥史能否打水痘疫苗");
        replied.setQuestion("孩子以前高热惊厥过，听说水痘是活疫苗，能接种吗？");
        replied.setReply("接种前需预防接种医生评估，当天如发热或急性疾病期暂缓，到诊请携带既往就诊记录。");
        replied.setRepliedByName(doctor.getName()); replied.setRepliedByRole("DOCTOR");
        replied.setRepliedAt(java.time.LocalDateTime.now().minusDays(5));
        replied.setStatus("REPLIED");
        replied.setPreVaccineAlert(true);
        replied.setAlertNote("高热惊厥史 + 拟种水痘减毒活疫苗，接种前必须医生评估并核对就诊记录");
        consultRepo.save(replied);

        // ---------- 生成计划（触发漏种/缺货/迁入等状态推送） ----------
        for (Child ch : List.of(a, b, c)) {
            planService.regenerate(ch.getId());
        }
    }

    private SysUser user(String username, String rawPwd, String name, Role role, Long personId, String title) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(encoder.encode(rawPwd));
        u.setName(name);
        u.setRole(role);
        u.setPersonId(personId);
        u.setTitle(title);
        u.setEnabled(true);
        return u;
    }

    private Vaccine vaccine(String code, String name, String group, String category, int total, String components) {
        Vaccine v = new Vaccine();
        v.setCode(code); v.setName(name); v.setVaccineGroup(group);
        v.setCategory(category); v.setTotalDoses(total); v.setComponents(components);
        return v;
    }

    private void template(Vaccine v, int dose, int rec, int min, int max, int intervalDays) {
        ScheduleTemplate t = new ScheduleTemplate();
        t.setVaccine(v); t.setDoseNo(dose);
        t.setRecommendedAgeMonths(rec); t.setMinAgeMonths(min);
        t.setMaxAgeMonths(max); t.setMinIntervalDays(intervalDays);
        templateRepo.save(t);
    }

    private void batch(String no, String code, String name, int qty, int safety,
                       LocalDate expiry, LocalDate arrival, String cold, String note) {
        VaccineBatch b = new VaccineBatch();
        b.setBatchNo(no); b.setVaccineCode(code); b.setVaccineName(name);
        b.setQuantity(qty); b.setSafetyStock(safety);
        b.setExpiryDate(expiry); b.setArrivalDate(arrival);
        b.setColdChainStatus(cold); b.setColdChainNote(note);
        batchRepo.save(b);
    }

    private void prior(Child child, Vaccine v, int dose, LocalDate date, String batch,
                       String clinic, String source, boolean verified, String note) {
        PriorVaccination p = new PriorVaccination();
        p.setChild(child); p.setVaccineCode(v.getCode()); p.setVaccineName(v.getName());
        p.setDoseNo(dose); p.setVaccinationDate(date); p.setBatchNo(batch);
        p.setClinicName(clinic); p.setSource(source); p.setVerified(verified); p.setNote(note);
        priorRepo.save(p);
    }
}
