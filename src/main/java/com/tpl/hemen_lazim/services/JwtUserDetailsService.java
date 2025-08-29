package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.repositories.UserRepository;
import com.tpl.hemen_lazim.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    // JWT filtresi için:
    public UserDetails loadUserById(UUID id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        return JwtUserDetails.create(user);
    }

    // login sırasında username (kullanıcı adınla giriş yapıyorsan):
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return JwtUserDetails.create(user);
    }

    // e-posta ile login edeceksen yukarıyı şu şekilde değiştir:
    // var user = userRepository.findByEmail(username)...
}