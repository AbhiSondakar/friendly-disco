package com.ecoloop.identity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserPrincipal implements UserDetails, Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String role;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String email, String passwordHash, String role, boolean active) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
            user.getId(),
            user.getEmail(),
            user.getPasswordHash(),
            user.getRole(),
            user.isActive()
        );
    }

    public UUID getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
