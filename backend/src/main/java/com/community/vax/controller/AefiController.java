package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import com.community.vax.common.Role;
import com.community.vax.entity.AefiCase;
import com.community.vax.entity.FollowUp;
import com.community.vax.security.Roles;
import com.community.vax.service.AefiService;
import com.community.vax.service.FollowUpService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 异常反应个案与随访；支持按儿童、疫苗、批号追踪及区级上报 */
@RestController
@RequestMapping("/api/aefi")
public class AefiController {

    private final AefiService aefiService;
    private final FollowUpService followUpService;
    private final WebSupport web;

    public AefiController(AefiService aefiService, FollowUpService followUpService, WebSupport web) {
        this.aefiService = aefiService;
        this.followUpService = followUpService;
        this.web = web;
    }

    /** ?childId=&vaccineCode=&batchNo=&openOnly= */
    @GetMapping
    public ApiResult<List<AefiCase>> search(@RequestParam(required = false) Long childId,
                                            @RequestParam(required = false) String vaccineCode,
                                            @RequestParam(required = false) String batchNo,
                                            @RequestParam(defaultValue = "false") boolean openOnly) {
        return ApiResult.ok(aefiService.search(childId, vaccineCode, batchNo, openOnly));
    }

    @PostMapping
    @Roles({Role.DOCTOR, Role.NURSE, Role.FOLLOWUP, Role.ADMIN})
    public ApiResult<AefiCase> create(@RequestBody AefiService.AefiCreateRequest req) {
        return ApiResult.ok(aefiService.createManual(req, web.currentUser()));
    }

    @PutMapping("/{id}/medical")
    @Roles({Role.DOCTOR, Role.FOLLOWUP, Role.ADMIN})
    public ApiResult<AefiCase> medical(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResult.ok(aefiService.updateMedical(id, body.get("hospital"), body.get("medicalRecord")));
    }

    @PutMapping("/{id}/conclusion")
    @Roles({Role.DOCTOR, Role.ADMIN})
    public ApiResult<AefiCase> conclusion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResult.ok(aefiService.conclude(id,
                (String) body.get("conclusion"),
                (String) body.get("note"),
                Boolean.TRUE.equals(body.get("closeCase"))));
    }

    @PostMapping("/{id}/report-district")
    @Roles({Role.DOCTOR, Role.ADMIN})
    public ApiResult<AefiCase> report(@PathVariable Long id) {
        return ApiResult.ok(aefiService.reportDistrict(id));
    }

    @GetMapping("/{id}/follow-ups")
    public ApiResult<List<FollowUp>> followUps(@PathVariable Long id) {
        return ApiResult.ok(followUpService.listByAefi(id));
    }

    @PostMapping("/{id}/follow-ups")
    @Roles({Role.FOLLOWUP, Role.DOCTOR, Role.NURSE, Role.ADMIN})
    public ApiResult<FollowUp> addFollowUp(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        FollowUpService.FollowRequest req = new FollowUpService.FollowRequest(
                id,
                body.get("followDate") == null ? null : java.time.LocalDate.parse((String) body.get("followDate")),
                (String) body.get("method"),
                body.get("temperature") == null ? null : ((Number) body.get("temperature")).doubleValue(),
                (String) body.get("symptomsStatus"),
                (String) body.get("advice"),
                (String) body.get("outcome"),
                body.get("nextFollowDate") == null ? null : java.time.LocalDate.parse((String) body.get("nextFollowDate")));
        return ApiResult.ok(followUpService.create(req, web.currentUser()));
    }
}
