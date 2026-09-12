package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import com.community.vax.common.Role;
import com.community.vax.entity.VaccinationPlan;
import com.community.vax.repo.VaccinationPlanRepository;
import com.community.vax.security.Roles;
import com.community.vax.service.ChildService;
import com.community.vax.service.PlanService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;
    private final VaccinationPlanRepository planRepo;
    private final ChildService childService;
    private final WebSupport web;

    public PlanController(PlanService planService, VaccinationPlanRepository planRepo,
                          ChildService childService, WebSupport web) {
        this.planService = planService;
        this.planRepo = planRepo;
        this.childService = childService;
        this.web = web;
    }

    @GetMapping("/child/{childId}")
    public ApiResult<List<VaccinationPlan>> byChild(@PathVariable Long childId) {
        childService.getFor(web.currentUser(), childId);
        return ApiResult.ok(planService.listForChild(childId));
    }

    @PostMapping("/child/{childId}/regenerate")
    public ApiResult<List<VaccinationPlan>> regenerate(@PathVariable Long childId) {
        childService.getFor(web.currentUser(), childId);
        return ApiResult.ok(planService.regenerate(childId));
    }

    /** 医生复核：过敏/禁忌状态的计划行经评估后开放预约 */
    @PostMapping("/{id}/approve")
    @Roles({Role.DOCTOR, Role.ADMIN})
    public ApiResult<Void> approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        planService.doctorApprove(id, body.get("note"), web.currentUser());
        return ApiResult.ok();
    }
}
