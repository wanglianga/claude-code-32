package com.community.vax.repo;

import com.community.vax.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsername(String username);
    Optional<SysUser> findByPersonId(Long personId);
    List<SysUser> findByRole(com.community.vax.common.Role role);
}
