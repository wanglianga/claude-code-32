package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import com.community.vax.common.Role;
import com.community.vax.entity.Consultation;
import com.community.vax.security.Roles;
import com.community.vax.service.ConsultationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consultations")
public class ConsultationController {

    private final ConsultationService consultationService;
    private final WebSupport web;

    public ConsultationController(ConsultationService consultationService, WebSupport web) {
        this.consultationService = consultationService;
        this.web = web;
    }

    @PostMapping
    @Roles({Role.PARENT})
    public ApiResult<Consultation> ask(@RequestBody ConsultationService.AskRequest req) {
        return ApiResult.ok(consultationService.ask(req, web.currentUser()));
    }

    @PutMapping("/{id}/reply")
    @Roles({Role.DOCTOR, Role.FOLLOWUP, Role.ADMIN})
    public ApiResult<Consultation> reply(@PathVariable Long id,
                                         @RequestBody ConsultationService.ReplyRequest req) {
        return ApiResult.ok(consultationService.reply(id, req, web.currentUser()));
    }

    @GetMapping("/open")
    @Roles({Role.DOCTOR, Role.FOLLOWUP, Role.ADMIN, Role.NURSE})
    public ApiResult<List<Consultation>> open() {
        return ApiResult.ok(consultationService.listOpen());
    }
}
