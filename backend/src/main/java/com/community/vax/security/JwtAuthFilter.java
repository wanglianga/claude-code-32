package com.community.vax.security;

import com.community.vax.common.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                CurrentUser cu = jwtService.parse(header.substring(7));
                var auth = new UsernamePasswordAuthenticationToken(
                        cu, null, List.of(new SimpleGrantedAuthority("ROLE_" + cu.getRole().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                // token 无效 -> 匿名访问，由 Security 链拒绝受保护接口
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    public static CurrentUser current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser cu) {
            return cu;
        }
        return null;
    }

    public static boolean hasRole(Role role) {
        CurrentUser cu = current();
        return cu != null && cu.getRole() == role;
    }
}
