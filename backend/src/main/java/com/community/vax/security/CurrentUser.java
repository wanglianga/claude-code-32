package com.community.vax.security;

import com.community.vax.common.Role;

public class CurrentUser {
    private final Long userId;
    private final String username;
    private final Role role;
    private final Long personId;

    public CurrentUser(Long userId, String username, Role role, Long personId) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.personId = personId;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public Long getPersonId() { return personId; }
}
