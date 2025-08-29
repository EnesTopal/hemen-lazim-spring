package com.tpl.hemen_lazim.security;

import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.model.enums.Role;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Data
public class JwtUserDetails implements UserDetails {

    private final UUID userId;
    private final String username;   // Şu an userName kullanıyoruz; eğer e-mail ile login edeceksen user.getEmail() ver
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public JwtUserDetails(UUID userId,
                          String username,
                          String password,
                          boolean enabled,
                          Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    public static JwtUserDetails create(User user) {
        Role role = user.getRole() != null ? user.getRole() : Role.USER;
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.name());

        return new JwtUserDetails(
                user.getId(),
                user.getUserName(),          // e-posta ile login'e geçersen burada user.getEmail()
                user.getPasswordHash(),
                user.isEnabled(),
                List.of(authority)           // tek elemanlı liste
        );
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }
}
