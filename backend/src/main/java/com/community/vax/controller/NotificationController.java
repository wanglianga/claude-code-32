package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import com.community.vax.entity.Notification;
import com.community.vax.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final WebSupport web;

    public NotificationController(NotificationService notificationService, WebSupport web) {
        this.notificationService = notificationService;
        this.web = web;
    }

    @GetMapping
    public ApiResult<List<Notification>> list() {
        return ApiResult.ok(notificationService.listFor(web.currentUser()));
    }

    @GetMapping("/unread-count")
    public ApiResult<Map<String, Long>> unread() {
        return ApiResult.ok(Map.of("count", notificationService.unreadCount(web.currentUser())));
    }

    @PostMapping("/{id}/read")
    public ApiResult<Void> read(@PathVariable Long id) {
        notificationService.markRead(id);
        return ApiResult.ok();
    }

    @PostMapping("/read-all")
    public ApiResult<Void> readAll() {
        notificationService.markAllRead(web.currentUser());
        return ApiResult.ok();
    }
}
