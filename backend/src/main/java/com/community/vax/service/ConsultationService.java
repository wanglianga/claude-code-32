package com.community.vax.service;

import com.community.vax.common.BizException;
import com.community.vax.entity.Child;
import com.community.vax.entity.Consultation;
import com.community.vax.entity.SysUser;
import com.community.vax.repo.ChildRepository;
import com.community.vax.repo.ConsultationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 家长咨询：回复 + 标记“下次接种前提醒”，供护士到诊核验时带出 */
@Service
public class ConsultationService {

    private final ConsultationRepository consultationRepo;
    private final ChildRepository childRepo;
    private final NotificationService notifier;

    public ConsultationService(ConsultationRepository consultationRepo, ChildRepository childRepo,
                               NotificationService notifier) {
        this.consultationRepo = consultationRepo;
        this.childRepo = childRepo;
        this.notifier = notifier;
    }

    public record AskRequest(Long childId, String topic, String question) {}

    @Transactional
    public Consultation ask(AskRequest req, SysUser parent) {
        Child child = childRepo.findById(req.childId()).orElseThrow(() -> new BizException("儿童不存在"));
        if (child.getGuardian() == null || !child.getGuardian().getUserId().equals(parent.getId())) {
            throw new BizException("只能为自己的子女发起咨询", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        Consultation c = new Consultation();
        c.setChild(child);
        c.setTopic(req.topic());
        c.setQuestion(req.question());
        c.setStatus("OPEN");
        c = consultationRepo.save(c);

        notifier.notifyRole("DOCTOR", "CONSULT", "INFO",
                "家长咨询待回复：" + child.getName(),
                "主题：" + req.topic() + "；问题：" + req.question(), child, "CONSULTATION", c.getId());
        notifier.notifyRole("FOLLOWUP", "CONSULT", "INFO",
                "家长咨询待回复：" + child.getName(),
                "主题：" + req.topic() + "；问题：" + req.question(), child, "CONSULTATION", c.getId());
        return c;
    }

    public record ReplyRequest(String reply, boolean preVaccineAlert, String alertNote) {}

    @Transactional
    public Consultation reply(Long id, ReplyRequest req, SysUser staff) {
        Consultation c = consultationRepo.findById(id).orElseThrow(() -> new BizException("咨询不存在"));
        c.setReply(req.reply());
        c.setRepliedByName(staff.getName());
        c.setRepliedByRole(staff.getRole().name());
        c.setRepliedAt(LocalDateTime.now());
        c.setStatus("REPLIED");
        c.setPreVaccineAlert(req.preVaccineAlert());
        c.setAlertNote(req.preVaccineAlert()
                ? (req.alertNote() == null || req.alertNote().isBlank() ? req.reply() : req.alertNote())
                : null);
        consultationRepo.save(c);

        Long uid = c.getChild().getGuardian() == null ? null : c.getChild().getGuardian().getUserId();
        if (uid != null) {
            notifier.notifyUser(uid, "CONSULT", "INFO",
                    "您的咨询已回复：" + c.getTopic(),
                    c.getChild().getName() + " - " + c.getTopic() + "：" + req.reply()
                            + (req.preVaccineAlert() ? "（已登记为下次接种前提醒，到诊时医护会主动核对）" : ""),
                    c.getChild(), "CONSULTATION", c.getId());
        }
        if (req.preVaccineAlert()) {
            notifier.notifyRole("NURSE", "PREVAX", "WARN",
                    "接种前提醒：" + c.getChild().getName() + " " + c.getTopic(),
                    "家长咨询中标记的接种前注意事项：" + c.getAlertNote()
                            + "。下次到诊核验时请主动与家长确认。", c.getChild(), "CONSULTATION", c.getId());
        }
        return c;
    }

    @Transactional(readOnly = true)
    public List<Consultation> listForChild(Long childId) {
        return consultationRepo.findByChildIdOrderByCreatedAtDesc(childId);
    }

    @Transactional(readOnly = true)
    public List<Consultation> listOpen() {
        return consultationRepo.findByStatusOrderByCreatedAtDesc("OPEN");
    }

    /** 该儿童下次接种前需提醒医护的咨询要点 */
    @Transactional(readOnly = true)
    public List<Consultation> pendingAlertsForChild(Long childId) {
        return consultationRepo.findByChildIdAndPreVaccineAlertTrue(childId);
    }
}
