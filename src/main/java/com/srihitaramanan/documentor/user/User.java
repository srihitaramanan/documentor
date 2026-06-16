package com.srihitaramanan.documentor.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping the {@code users} table.
 *
 * <p>Schema is owned by Flyway (V1__init.sql); this class only describes
 * the in-Java shape so JPA can read/write rows.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // JPA requires a no-arg constructor
    protected User() {}

    public User(UUID id, String email, String passwordHash, OffsetDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public static User newUser(String email, String passwordHash) {
        return new User(UUID.randomUUID(), email, passwordHash, OffsetDateTime.now());
    }

    // ---- Getters (no setters — entity should be effectively immutable post-creation)
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    // ---- equals/hashCode by ID (JPA best practice)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}