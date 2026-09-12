package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        Integer ok = jdbc.queryForObject("SELECT 1", Integer.class);
        return ApiResult.ok(Map.of(
                "status", "UP",
                "db", ok != null && ok == 1 ? "UP" : "DOWN",
                "app", "vaccine-platform"));
    }
}
