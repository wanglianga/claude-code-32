package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import com.community.vax.common.Role;
import com.community.vax.entity.*;
import com.community.vax.repo.*;
import com.community.vax.security.Roles;
import com.community.vax.service.ChildService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/children")
public class ChildController {

    private final ChildService childService;
    private final WebSupport web;
    private final ConsultationRepository consultRepo;

    public ChildController(ChildService childService, WebSupport web, ConsultationRepository consultRepo) {
        this.childService = childService;
        this.web = web;
        this.consultRepo = consultRepo;
    }

    @GetMapping
    public ApiResult<List<Child>> list() {
        return ApiResult.ok(childService.listFor(web.currentUser()));
    }

    @PostMapping
    @Roles({Role.PARENT})
    public ApiResult<Child> create(@RequestBody Child body) {
        SysUser u = web.currentUser();
        Child c = new Child();
        c.setName(body.getName());
        c.setGender(body.getGender());
        c.setBirthDate(body.getBirthDate());
        c.setIdCardNo(body.getIdCardNo());
        c.setMoveInDate(body.getMoveInDate());
        c.setMigrationNote(body.getMigrationNote());
        c.setHealthStatus(body.getHealthStatus());
        return ApiResult.ok(childService.create(c, u.getId()));
    }

    @GetMapping("/{id}")
    public ApiResult<Child> detail(@PathVariable Long id) {
        return ApiResult.ok(childService.getFor(web.currentUser(), id));
    }

    @PutMapping("/{id}/health")
    public ApiResult<Child> health(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResult.ok(childService.updateHealth(id, body.get("healthStatus"), web.currentUser()));
    }

    @PostMapping("/{id}/allergies")
    public ApiResult<Allergy> allergy(@PathVariable Long id, @RequestBody Allergy body) {
        return ApiResult.ok(childService.addAllergy(id, body, web.currentUser()));
    }

    @PostMapping("/{id}/contraindications")
    @Roles({Role.DOCTOR, Role.NURSE, Role.ADMIN})
    public ApiResult<Contraindication> contra(@PathVariable Long id, @RequestBody Contraindication body) {
        return ApiResult.ok(childService.addContra(id, body, web.currentUser()));
    }

    @PostMapping("/{id}/priors")
    public ApiResult<PriorVaccination> prior(@PathVariable Long id, @RequestBody ChildService.PriorRequest req) {
        return ApiResult.ok(childService.addPrior(id, req, web.currentUser()));
    }

    @PostMapping("/priors/{priorId}/verify")
    @Roles({Role.DOCTOR, Role.NURSE, Role.ADMIN})
    public ApiResult<PriorVaccination> verifyPrior(@PathVariable Long priorId,
                                                   @RequestBody Map<String, Boolean> body) {
        return ApiResult.ok(childService.verifyPrior(priorId, Boolean.TRUE.equals(body.get("verified"))));
    }

    @PostMapping("/{id}/replan")
    public ApiResult<List<VaccinationPlan>> replan(@PathVariable Long id) {
        return ApiResult.ok(childService.replan(id, web.currentUser()));
    }

    @GetMapping("/{id}/health-record")
    public ApiResult<Map<String, Object>> healthRecord(@PathVariable Long id) {
        return ApiResult.ok(childService.healthRecord(web.currentUser(), id));
    }

    @GetMapping("/{id}/consultations")
    public ApiResult<List<Consultation>> consultations(@PathVariable Long id) {
        childService.getFor(web.currentUser(), id); // 权限校验
        return ApiResult.ok(consultRepo.findByChildIdOrderByCreatedAtDesc(id));
    }
}
