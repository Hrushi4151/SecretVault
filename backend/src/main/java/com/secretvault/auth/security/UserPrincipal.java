package com.secretvault.auth.security;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Spring Security UserDetails implementation holding the authenticated principal identity.
 */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final String fullName;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String sessionIdentifier;
    private final boolean isMachine;
    private final UUID workspaceId;

    public UserPrincipal(UUID id, String email, String password, String fullName, boolean enabled, Collection<? extends GrantedAuthority> authorities, String sessionIdentifier, boolean isMachine, UUID workspaceId) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.enabled = enabled;
        this.authorities = authorities;
        this.sessionIdentifier = sessionIdentifier;
        this.isMachine = isMachine;
        this.workspaceId = workspaceId;
    }

    public UserPrincipal(UUID id, String email, String password, String fullName, boolean enabled, Collection<? extends GrantedAuthority> authorities, String sessionIdentifier) {
        this(id, email, password, fullName, enabled, authorities, sessionIdentifier, false, null);
    }

    public UserPrincipal(UUID id, String email, String password, String fullName, boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        this(id, email, password, fullName, enabled, authorities, null, false, null);
    }

    public static UserPrincipal create(User user, String sessionIdentifier) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        boolean isEnabled = user.getStatus() == UserStatus.ACTIVE;
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFullName(),
                isEnabled,
                authorities,
                sessionIdentifier,
                false,
                null
        );
    }

    public static UserPrincipal create(User user) {
        return create(user, null);
    }

    public static UserPrincipal createMachine(com.secretvault.machine.entity.MachineIdentity machine) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_MACHINE"));
        boolean isEnabled = machine.getStatus() == com.secretvault.machine.model.MachineStatus.ACTIVE && !machine.isExpired(java.time.Instant.now());
        return new UserPrincipal(
                machine.getId(),
                "machine:" + machine.getName(),
                "",
                "Machine: " + machine.getName(),
                isEnabled,
                authorities,
                null,
                true,
                machine.getWorkspaceId()
        );
    }

    public boolean isMachine() {
        return isMachine;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getSessionIdentifier() {
        return sessionIdentifier;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
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
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPrincipal that = (UserPrincipal) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
