package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import com.community.vax.common.Role;
import com.community.vax.entity.Appointment;
import com.community.vax.repo.AppointmentRepository;
import com.community.vax.security.Roles;
import com.community.vax.service.AppointmentService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentRepository appointmentRepo;
    private final WebSupport web;

    public AppointmentController(AppointmentService appointmentService,
                                 AppointmentRepository appointmentRepo, WebSupport web) {
        this.appointmentService = appointmentService;
        this.appointmentRepo = appointmentRepo;
        this.web = web;
    }

    /** 可约时段：?planId=&vaccineCode=（可传同组替代苗）&days=10 */
    @GetMapping("/slots")
    public ApiResult<List<AppointmentService.SlotView>> slots(
            @RequestParam Long planId,
            @RequestParam(required = false) String vaccineCode,
            @RequestParam(defaultValue = "10") int days) {
        return ApiResult.ok(appointmentService.availableSlots(planId, vaccineCode, Math.min(days, 21)));
    }

    @PostMapping
    @Roles({Role.PARENT})
    public ApiResult<Appointment> book(@RequestBody AppointmentService.BookRequest req) {
        return ApiResult.ok(appointmentService.book(req, web.currentUser()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResult<Appointment> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        boolean byClinic = web.currentUser().getRole() != Role.PARENT;
        String reason = body == null ? null : body.get("reason");
        return ApiResult.ok(appointmentService.cancel(id, reason, web.currentUser(), byClinic));
    }

    @PostMapping("/{id}/check-in")
    @Roles({Role.NURSE, Role.ADMIN})
    public ApiResult<Appointment> checkIn(@PathVariable Long id) {
        return ApiResult.ok(appointmentService.checkIn(id, web.currentUser()));
    }

    /** 家长：我的预约 */
    @GetMapping("/mine")
    @Roles({Role.PARENT})
    public ApiResult<List<Appointment>> mine() {
        Long personId = web.currentUser().getPersonId();
        return ApiResult.ok(appointmentRepo.findAll().stream()
                .filter(a -> a.getChild().getGuardian() != null
                        && a.getChild().getGuardian().getId().equals(personId))
                .sorted((x, y) -> y.getAppointmentDate().compareTo(x.getAppointmentDate()))
                .toList());
    }

    /** 门诊工作日视图（默认今天，含已预约/到诊/已接种） */
    @GetMapping("/daily")
    public ApiResult<List<Appointment>> daily(@RequestParam(required = false) String date) {
        LocalDate d = date == null ? LocalDate.now() : LocalDate.parse(date);
        return ApiResult.ok(appointmentRepo.findByAppointmentDateBetweenAndStatusIn(
                d, d, List.of("BOOKED", "CHECKED_IN", "VACCINATED")));
    }
}
