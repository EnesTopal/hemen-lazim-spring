package com.tpl.hemen_lazim.model;

import com.tpl.hemen_lazim.model.enums.Role;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_name", nullable = false, length = 150)
    private String userName;

    @Column(name = "email", nullable = false, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    @OneToMany(mappedBy = "requester", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = false)
    private Set<MaterialRequest> requests;

    // JWT tarafında ileride işine yarayabilir, şart değil ama koydum
//    @Column(name = "token_version", nullable = false)
//    private int tokenVersion = 0;

}

