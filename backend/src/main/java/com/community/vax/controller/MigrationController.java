package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import com.community.vax.common.Role;
import com.community.vax.entity.MigrationDocument;
import com.community.vax.entity.PriorVaccination;
import com.community.vax.entity.SysUser;
import com.community.vax.security.Roles;
import com.community.vax.service.ChildService;
import com.community.vax.service.MigrationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 外地接种本上传识别 + 人工核验队列 */
@RestController
@RequestMapping("/api")
public class MigrationController {

    private final MigrationService migrationService;
    private final ChildService childService;
    private final WebSupport web;

    public MigrationController(MigrationService migrationService, ChildService childService, WebSupport web) {
        this.migrationService = migrationService;
        this.childService = childService;
        this.web = web;
    }

    @PostMapping("/children/{childId}/migration-docs")
    public ApiResult<Map<String, Object>> upload(@PathVariable Long childId,
                                                 @RequestParam("file") MultipartFile file) {
        SysUser user = web.currentUser();
        childService.requireAccessible(childId, user); // 家长只能传自己孩子
        return ApiResult.ok(migrationService.uploadAndParse(childId, file, user));
    }

    @GetMapping("/children/{childId}/migration-docs")
    public ApiResult<List<MigrationDocument>> docs(@PathVariable Long childId) {
        childService.requireAccessible(childId, web.currentUser());
        return ApiResult.ok(migrationService.listDocs(childId));
    }

    @GetMapping("/children/{childId}/migration-records")
    public ApiResult<List<PriorVaccination>> records(@PathVariable Long childId) {
        childService.requireAccessible(childId, web.currentUser());
        return ApiResult.ok(migrationService.listRecords(childId));
    }

    @GetMapping("/migration-docs/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        MigrationDocument doc = migrationService.getDoc(id);
        childService.requireAccessible(doc.getChildId(), web.currentUser());
        MediaType type = doc.getContentType() != null && doc.getContentType().startsWith("image")
                ? MediaType.parseMediaType(doc.getContentType())
                : MediaType.TEXT_PLAIN;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, type.toString())
                .body(doc.getData());
    }

    /** 人工提醒队列（待核验 + 模糊） */
    @GetMapping("/migration/queue")
    @Roles({Role.DOCTOR, Role.NURSE, Role.ADMIN, Role.FOLLOWUP})
    public ApiResult<List<Map<String, Object>>> queue() {
        return ApiResult.ok(migrationService.reviewQueue());
    }

    @PostMapping("/migration/priors/{priorId}/confirm")
    @Roles({Role.DOCTOR, Role.NURSE, Role.ADMIN})
    public ApiResult<PriorVaccination> confirm(@PathVariable Long priorId,
                                               @RequestBody(required = false) MigrationService.ConfirmRequest req) {
        MigrationService.ConfirmRequest r = req == null
                ? new MigrationService.ConfirmRequest(null, null, null, null, null, null) : req;
        return ApiResult.ok(migrationService.confirm(priorId, r, web.currentUser()));
    }

    public record RejectRequest(String note) {}

    @PostMapping("/migration/priors/{priorId}/reject")
    @Roles({Role.DOCTOR, Role.NURSE, Role.ADMIN})
    public ApiResult<PriorVaccination> reject(@PathVariable Long priorId,
                                              @RequestBody(required = false) RejectRequest req) {
        return ApiResult.ok(migrationService.reject(priorId,
                req == null ? null : req.note(), web.currentUser()));
    }
}
