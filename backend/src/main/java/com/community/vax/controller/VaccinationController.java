package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import com.community.vax.common.BizException;
import com.community.vax.common.Role;
import com.community.vax.entity.Appointment;
import com.community.vax.entity.VaccinationRecord;
import com.community.vax.repo.AppointmentRepository;
import com.community.vax.repo.VaccinationRecordRepository;
import com.community.vax.security.Roles;
import com.community.vax.service.ConsultationService;
import com.community.vax.service.VaccinationService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vaccinations")
public class VaccinationController {

    private final VaccinationService vaccinationService;
    private final VaccinationRecordRepository recordRepo;
    private final AppointmentRepository appointmentRepo;
    private final ConsultationService consultationService;
    private final WebSupport web;

    public VaccinationController(VaccinationService vaccinationService,
                                 VaccinationRecordRepository recordRepo,
                                 AppointmentRepository appointmentRepo,
                                 ConsultationService consultationService, WebSupport web) {
        this.vaccinationService = vaccinationService;
        this.recordRepo = recordRepo;
        this.appointmentRepo = appointmentRepo;
        this.consultationService = consultationService;
        this.web = web;
    }

    /** 到诊五项核验 + 接种扣库存（withhold=true 表示暂缓） */
    @PostMapping("/appointments/{appointmentId}/verify")
    @Roles({Role.NURSE, Role.ADMIN})
    public ApiResult<VaccinationRecord> verify(@PathVariable Long appointmentId,
                                               @RequestBody VaccinationService.VerifyRequest req) {
        return ApiResult.ok(vaccinationService.verifyAndVaccinate(appointmentId, req, web.currentUser()));
    }

    /** 结束留观（abnormal=true 自动建 AEFI） */
    @PostMapping("/{id}/observation")
    @Roles({Role.NURSE, Role.ADMIN})
    public ApiResult<VaccinationRecord> observation(@PathVariable Long id,
                                                    @RequestBody VaccinationService.ObserveRequest req) {
        return ApiResult.ok(vaccinationService.completeObservation(id, req, web.currentUser()));
    }

    @PostMapping("/{id}/doctor-review")
    @Roles({Role.DOCTOR, Role.ADMIN})
    public ApiResult<VaccinationRecord> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResult.ok(vaccinationService.doctorReview(id,
                (String) body.get("note"), Boolean.TRUE.equals(body.get("approved")), web.currentUser()));
    }

    @PostMapping("/{id}/resolve-abnormal")
    @Roles({Role.DOCTOR, Role.NURSE, Role.ADMIN})
    public ApiResult<VaccinationRecord> resolve(@PathVariable Long id) {
        return ApiResult.ok(vaccinationService.resolveAbnormal(id));
    }

    @GetMapping("/child/{childId}")
    public ApiResult<List<VaccinationRecord>> byChild(@PathVariable Long childId) {
        return ApiResult.ok(recordRepo.findByChildIdOrderByVaccinationDateDesc(childId));
    }

    /** 到诊核验页：现有接种记录（如有）+ 该儿童接种前需提醒的家长咨询要点 */
    @GetMapping("/appointments/{appointmentId}/checklist")
    @Roles({Role.NURSE, Role.DOCTOR, Role.ADMIN})
    public ApiResult<Map<String, Object>> checklist(@PathVariable Long appointmentId) {
        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new BizException("预约不存在"));
        VaccinationRecord rec = recordRepo.findByAppointmentId(appointmentId).orElse(null);
        Map<String, Object> data = new HashMap<>();
        data.put("appointment", appt);
        data.put("record", rec);
        data.put("preVaccineAlerts", consultationService.pendingAlertsForChild(appt.getChild().getId()));
        return ApiResult.ok(data);
    }
}
