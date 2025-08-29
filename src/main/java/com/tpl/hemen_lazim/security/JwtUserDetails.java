package com.tpl.hemen_lazim.security;

import com.tpl.hemen_lazim.model.User;
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
        List<GrantedAuthority> roles = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        return new JwtUserDetails(
                user.getId(),
                user.getUserName(),      // Eğer email ile giriş olacaksa: user.getEmail()
                user.getPasswordHash(),
                user.isEnabled(),
                roles
        );
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }
}
