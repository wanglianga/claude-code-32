package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import com.community.vax.common.BizException;
import com.community.vax.common.Role;
import com.community.vax.entity.*;
import com.community.vax.repo.*;
import com.community.vax.security.Roles;
import com.community.vax.service.NotificationService;
import com.community.vax.service.PlanService;
import com.community.vax.service.ReminderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 基础数据：疫苗目录、批号库存/冷链、门诊容量、医生排班；以及库存到货联动重算 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final VaccineRepository vaccineRepo;
    private final VaccineBatchRepository batchRepo;
    private final ClinicCapacityRepository capacityRepo;
    private final DoctorScheduleRepository scheduleRepo;
    private final ReminderService reminderService;
    private final NotificationService notifier;
    private final WebSupport web;

    public CatalogController(VaccineRepository vaccineRepo, VaccineBatchRepository batchRepo,
                             ClinicCapacityRepository capacityRepo, DoctorScheduleRepository scheduleRepo,
                             ReminderService reminderService, NotificationService notifier, WebSupport web) {
        this.vaccineRepo = vaccineRepo;
        this.batchRepo = batchRepo;
        this.capacityRepo = capacityRepo;
        this.scheduleRepo = scheduleRepo;
        this.reminderService = reminderService;
        this.notifier = notifier;
        this.web = web;
    }

    @GetMapping("/vaccines")
    public ApiResult<List<Vaccine>> vaccines() {
        return ApiResult.ok(vaccineRepo.findAll());
    }

    @GetMapping("/batches")
    public ApiResult<List<VaccineBatch>> batches() {
        return ApiResult.ok(batchRepo.findAllByOrderByArrivalDateDesc());
    }

    @PostMapping("/batches")
    @Roles({Role.ADMIN})
    public ApiResult<VaccineBatch> createBatch(@RequestBody VaccineBatch body) {
        if (batchRepo.findByBatchNo(body.getBatchNo()).isPresent()) {
            throw new BizException("批号已存在");
        }
        body.setId(null);
        if (body.getArrivalDate() == null) body.setArrivalDate(LocalDate.now());
        if (body.getColdChainStatus() == null) body.setColdChainStatus("NORMAL");
        body.setCreatedAt(LocalDateTime.now());
        body.setColdChainCheckedAt(LocalDateTime.now());
        VaccineBatch saved = batchRepo.save(body);
        afterStockChange(saved, "新批号入库：" + saved.getVaccineName() + " 批号 " + saved.getBatchNo()
                + "，数量 " + saved.getQuantity());
        return ApiResult.ok(saved);
    }

    @PutMapping("/batches/{id}")
    @Roles({Role.ADMIN})
    public ApiResult<VaccineBatch> updateBatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        VaccineBatch b = batchRepo.findById(id).orElseThrow(() -> new BizException("批号不存在"));
        if (body.containsKey("quantity")) b.setQuantity((Integer) body.get("quantity"));
        if (body.containsKey("safetyStock")) b.setSafetyStock((Integer) body.get("safetyStock"));
        if (body.containsKey("expiryDate")) b.setExpiryDate(LocalDate.parse((String) body.get("expiryDate")));
        if (body.containsKey("coldChainStatus")) {
            b.setColdChainStatus((String) body.get("coldChainStatus"));
            b.setColdChainCheckedAt(LocalDateTime.now());
            if (body.containsKey("coldChainNote")) b.setColdChainNote((String) body.get("coldChainNote"));
        }
        b = batchRepo.save(b);
        afterStockChange(b, "批号 " + b.getBatchNo() + " 库存/冷链信息更新（剩余 " + b.getQuantity() + "）");
        return ApiResult.ok(b);
    }

    /** 库存/冷链变化后：重算所有儿童计划（到货 → WAIT_STOCK 自动转可约） */
    private void afterStockChange(VaccineBatch b, String note) {
        reminderService.refreshAllPlansAndRemind();
        if (b.isAvailable()) {
            notifier.notifyRole("ADMIN", "STOCK", "INFO", "库存到货更新",
                    note + "，等待该疫苗的儿童计划已重新评估并开放可约时段。", null, "BATCH", b.getId());
        }
    }

    @GetMapping("/capacities")
    public ApiResult<List<ClinicCapacity>> capacities(@RequestParam(required = false) String from,
                                                      @RequestParam(required = false) String to) {
        LocalDate f = from == null ? LocalDate.now() : LocalDate.parse(from);
        LocalDate t = to == null ? LocalDate.now().plusDays(14) : LocalDate.parse(to);
        return ApiResult.ok(capacityRepo.findByClinicDateBetweenOrderByClinicDateAscTimeSlotAsc(f, t));
    }

    @PostMapping("/capacities")
    @Roles({Role.ADMIN})
    public ApiResult<ClinicCapacity> createCapacity(@RequestBody ClinicCapacity body) {
        body.setId(null);
        if (body.getBookedCount() == null) body.setBookedCount(0);
        return ApiResult.ok(capacityRepo.save(body));
    }

    @PutMapping("/capacities/{id}")
    @Roles({Role.ADMIN})
    public ApiResult<ClinicCapacity> updateCapacity(@PathVariable Long id,
                                                    @RequestBody ClinicCapacity body) {
        ClinicCapacity c = capacityRepo.findById(id).orElseThrow(() -> new BizException("容量记录不存在"));
        c.setMaxCapacity(body.getMaxCapacity());
        c.setOpen(body.getOpen());
        return ApiResult.ok(capacityRepo.save(c));
    }

    @GetMapping("/schedules")
    public ApiResult<List<DoctorSchedule>> schedules(@RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        LocalDate f = from == null ? LocalDate.now() : LocalDate.parse(from);
        LocalDate t = to == null ? LocalDate.now().plusDays(14) : LocalDate.parse(to);
        return ApiResult.ok(scheduleRepo.findByWorkDateBetweenOrderByWorkDateAsc(f, t));
    }

    @PostMapping("/schedules")
    @Roles({Role.ADMIN})
    @Transactional
    public ApiResult<DoctorSchedule> createSchedule(@RequestBody DoctorSchedule body) {
        body.setId(null);
        return ApiResult.ok(scheduleRepo.save(body));
    }
}
