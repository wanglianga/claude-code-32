package com.community.vax.service;

import com.community.vax.common.BizException;
import com.community.vax.entity.AefiCase;
import com.community.vax.entity.FollowUp;
import com.community.vax.entity.SysUser;
import com.community.vax.repo.AefiCaseRepository;
import com.community.vax.repo.FollowUpRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** AEFI 随访：随访记录、症状转归、下次随访提醒、结案联动 */
@Service
public class FollowUpService {

    private final FollowUpRepository followRepo;
    private final AefiCaseRepository aefiRepo;
    private final NotificationService notifier;

    public FollowUpService(FollowUpRepository followRepo, AefiCaseRepository aefiRepo,
                           NotificationService notifier) {
        this.followRepo = followRepo;
        this.aefiRepo = aefiRepo;
        this.notifier = notifier;
    }

    public record FollowRequest(Long aefiId, LocalDate followDate, String method, Double temperature,
                                String symptomsStatus, String advice, String outcome,
                                LocalDate nextFollowDate) {}

    @Transactional
    public FollowUp create(FollowRequest req, SysUser operator) {
        AefiCase aefi = aefiRepo.findById(req.aefiId()).orElseThrow(() -> new BizException("AEFI 个案不存在"));
        FollowUp f = new FollowUp();
        f.setAefi(aefi);
        f.setFollowDate(req.followDate() == null ? LocalDate.now() : req.followDate());
        f.setMethod(req.method() == null ? "PHONE" : req.method());
        f.setTemperature(req.temperature());
        f.setSymptomsStatus(req.symptomsStatus());
        f.setAdvice(req.advice());
        f.setOutcome(req.outcome() == null ? "ONGOING" : req.outcome());
        f.setNextFollowDate(req.nextFollowDate());
        f.setFollowUserId(operator.getId());
        f.setFollowUserName(operator.getName());
        f = followRepo.save(f);

        if ("OPEN".equals(aefi.getStatus())) {
            aefi.setStatus("FOLLOWING");
            aefiRepo.save(aefi);
        }
        // 好转且不再安排随访 → 提示医生可做最终判断；仍有下次随访 → 到期前提醒
        if ("RESOLVED".equals(f.getOutcome()) && f.getNextFollowDate() == null) {
            notifier.notifyRole("DOCTOR", "FOLLOWUP", "INFO",
                    "AEFI 症状已好转，待最终判断：" + aefi.getChild().getName(),
                    aefi.getVaccineName() + "（批号 " + aefi.getBatchNo() + "）最近一次随访转归为“已好转”，"
                            + "请预防接种医生完成最终判断并决定是否结案/上报。",
                    aefi.getChild(), "AEFI", aefi.getId());
            // 同步告知家长
            if (aefi.getChild().getGuardian() != null && aefi.getChild().getGuardian().getUserId() != null) {
                notifier.notifyUser(aefi.getChild().getGuardian().getUserId(), "INFO", "INFO",
                        "随访结果反馈", "孩子 " + aefi.getChild().getName() + " 接种后不适症状随访已好转，"
                                + "门诊将完成最终判断，如有反复请及时联系。", aefi.getChild(), "AEFI", aefi.getId());
            }
        }
        return f;
    }

    @Transactional(readOnly = true)
    public List<FollowUp> listByAefi(Long aefiId) {
        return followRepo.findByAefiIdOrderByFollowDateAsc(aefiId);
    }

    /** 定时：下次随访到期，提醒随访人员 */
    @Transactional
    public int remindDueFollowUps() {
        List<FollowUp> due = followRepo
                .findByNextFollowDateLessThanEqualAndAefiStatusNot(LocalDate.now(), "CLOSED");
        int n = 0;
        for (FollowUp f : due) {
            AefiCase aefi = f.getAefi();
            notifier.notifyRole("FOLLOWUP", "FOLLOWUP", "WARN",
                    "随访到期：" + aefi.getChild().getName() + " " + aefi.getVaccineName(),
                    "AEFI 个案计划于 " + f.getNextFollowDate() + " 前再次随访（批号 " + aefi.getBatchNo()
                            + "，当前症状：" + nullToDash(f.getSymptomsStatus()) + "），请按时联系家长。",
                    aefi.getChild(), "AEFI", aefi.getId());
            n++;
        }
        return n;
    }

    private String nullToDash(String s) { return s == null || s.isBlank() ? "-" : s; }
}
