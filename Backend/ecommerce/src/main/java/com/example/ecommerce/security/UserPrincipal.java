package com.example.ecommerce.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.Role;

import lombok.Getter;

/**
 * The logged-in user as Spring Security sees it. Controllers receive it through
 * {@code @AuthenticationPrincipal UserPrincipal principal}, so the user id always comes from
 * the verified token — never from a request parameter the browser could tamper with.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String fullName;
    private final String email;
    private final String password;
    private final Role role;
    private final boolean emailVerified;
    private final boolean active;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.role = user.getRole();
        this.emailVerified = user.isEmailVerified();
        this.active = user.isActive();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return emailVerified;
    }
}
