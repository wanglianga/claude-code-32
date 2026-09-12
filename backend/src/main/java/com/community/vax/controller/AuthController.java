package com.community.vax.controller;

import com.community.vax.common.ApiResult;
import com.community.vax.common.Role;
import com.community.vax.entity.SysUser;
import com.community.vax.repo.SysUserRepository;
import com.community.vax.security.CurrentUser;
import com.community.vax.security.JwtAuthFilter;
import com.community.vax.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    private final SysUserRepository userRepo;
    private final JwtService jwtService;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;

    public AuthController(SysUserRepository userRepo, JwtService jwtService,
                          org.springframework.security.crypto.password.PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.jwtService = jwtService;
        this.encoder = encoder;
    }

    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        SysUser user = userRepo.findByUsername(req.username().trim())
                .orElseThrow(() -> new com.community.vax.common.BizException("用户名或密码错误",
                        org.springframework.http.HttpStatus.UNAUTHORIZED));
        if (!Boolean.TRUE.equals(user.getEnabled()) || !encoder.matches(req.password(), user.getPassword())) {
            throw new com.community.vax.common.BizException("用户名或密码错误",
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        String token = jwtService.issue(user.getId(), user.getUsername(), user.getRole(), user.getPersonId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", profile(user));
        return ApiResult.ok(data);
    }

    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me() {
        CurrentUser cu = JwtAuthFilter.current();
        SysUser user = userRepo.findById(cu.getUserId()).orElseThrow();
        return ApiResult.ok(profile(user));
    }

    private Map<String, Object> profile(SysUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("name", u.getName());
        m.put("role", u.getRole().name());
        m.put("roleName", roleName(u.getRole()));
        m.put("personId", u.getPersonId());
        m.put("title", u.getTitle());
        return m;
    }

    public static String roleName(Role r) {
        return switch (r) {
            case PARENT -> "家长";
            case NURSE -> "护士";
            case DOCTOR -> "预防接种医生";
            case FOLLOWUP -> "随访人员";
            case ADMIN -> "管理员";
        };
    }
}
