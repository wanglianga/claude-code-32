package com.community.vax.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日定时：爽约置漏种、随访到期提醒、
 * 库存到货后自动重算等待库存儿童的计划（由 PlanService 重新评估状态并开放预约）。
 */
@Component
public class DailyScheduler {

    private final AppointmentService appointmentService;
    private final FollowUpService followUpService;
    private final ReminderService reminderService;

    public DailyScheduler(AppointmentService appointmentService, FollowUpService followUpService,
                          ReminderService reminderService) {
        this.appointmentService = appointmentService;
        this.followUpService = followUpService;
        this.reminderService = reminderService;
    }

    /** 每天 07:10 执行 */
    @Scheduled(cron = "0 10 7 * * ?")
    public void daily() {
        appointmentService.markNoShows();
        followUpService.remindDueFollowUps();
        reminderService.refreshAllPlansAndRemind();
    }
}
