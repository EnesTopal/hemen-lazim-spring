package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.model.DTOs.CreateUserDTO;
import com.tpl.hemen_lazim.model.DTOs.UserDTO;
import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.model.enums.Role;
import com.tpl.hemen_lazim.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserServices {

    private final UserRepository userRepository;

    public UserServices(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDTO createAccount(CreateUserDTO req) {
        User user = new User();
        user.setUserName(safeTrim(req.getUserName()));
        user.setEmail(normalizeEmail(req.getEmail()));
        user.setPasswordHash(req.getUserPassword()); // already encoded
        user.setEnabled(true);
        user.setRole(Role.USER);

        User saved = userRepository.save(user);
        return toDto(saved);
    }

    public ResponseEntity<ApiResponse<String>> deleteAccount(UUID userId) {
        Optional<User> opt = userRepository.findById(userId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>("User not found"));
        }

        userRepository.delete(opt.get());
        return ResponseEntity.ok(new ApiResponse<>("Success: User deleted"));
    }

    /** Eski proje uyumluluğu için: kullanıcıyı userName ile getir (gerekirse). */
    public User getOneUserByUserName(String userName) {
        return userRepository.findByUserName(userName).orElse(null);
    }

    public boolean existsByUserName(String userName) {
        return userRepository.existsByUserName(userName);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(normalizeEmail(email));
    }

    private static UserDTO toDto(User u) {
        return new UserDTO(
                u.getId(),
                u.getUserName(),
                u.getEmail(),
                u.getRole(),
                u.isEnabled()
        );
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }
}
