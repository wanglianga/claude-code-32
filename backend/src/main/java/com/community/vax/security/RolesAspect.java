package com.community.vax.security;

import com.community.vax.common.ApiResult;
import com.community.vax.common.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RolesAspect {

    @Around("@annotation(rolesAnn)")
    public Object check(ProceedingJoinPoint pjp, Roles rolesAnn) throws Throwable {
        CurrentUser cu = JwtAuthFilter.current();
        if (cu == null) {
            throw new BizException("未登录", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        boolean allowed = false;
        for (var r : rolesAnn.value()) {
            if (r == cu.getRole()) { allowed = true; break; }
        }
        if (!allowed) {
            throw new BizException("当前角色无权执行该操作", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return pjp.proceed();
    }
}
