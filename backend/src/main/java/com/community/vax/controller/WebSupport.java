package com.community.vax.controller;

import com.community.vax.common.BizException;
import com.community.vax.entity.SysUser;
import com.community.vax.repo.SysUserRepository;
import com.community.vax.security.CurrentUser;
import com.community.vax.security.JwtAuthFilter;
import org.springframework.stereotype.Component;

@Component
public class WebSupport {

    private final SysUserRepository userRepo;

    public WebSupport(SysUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public SysUser currentUser() {
        CurrentUser cu = JwtAuthFilter.current();
        if (cu == null) throw new BizException("未登录", org.springframework.http.HttpStatus.UNAUTHORIZED);
        return userRepo.findById(cu.getUserId()).orElseThrow();
    }
}
