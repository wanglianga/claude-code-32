package com.community.vax.service;

import com.community.vax.entity.Child;
import com.community.vax.entity.VaccinationPlan;
import com.community.vax.repo.ChildRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 补种提醒的批量重算：
 * 结合最短间隔、年龄上限与库存到货——库存到货后，原 WAIT_STOCK 行会自动转为可约，
 * 漏种/到期行会重新推送提醒。
 */
@Service
public class ReminderService {

    private final ChildRepository childRepo;
    private final PlanService planService;

    public ReminderService(ChildRepository childRepo, PlanService planService) {
        this.childRepo = childRepo;
        this.planService = planService;
    }

    @Transactional
    public int refreshAllPlansAndRemind() {
        int n = 0;
        for (Child child : childRepo.findAll()) {
            List<VaccinationPlan> plans = planService.regenerate(child.getId());
            if (!plans.isEmpty()) n++;
        }
        return n;
    }
}
