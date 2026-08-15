package com.shopmall.membership.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA 實體：純資料載體，與領域聚合分離（領域模型不被 JPA 註解污染）。 */
@Entity
@Table(name = "members")
public class MemberJpaEntity {

    @Id
    private UUID id;

    @Column(name = "identity_id", nullable = false, unique = true)
    private String identityId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusColumn status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TierColumn tier;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    public enum StatusColumn { ACTIVE, SUSPENDED }
    public enum TierColumn { STANDARD, GOLD, PLATINUM }

    protected MemberJpaEntity() {}   // for JPA

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getIdentityId() { return identityId; }
    public void setIdentityId(String identityId) { this.identityId = identityId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public StatusColumn getStatus() { return status; }
    public void setStatus(StatusColumn status) { this.status = status; }

    public TierColumn getTier() { return tier; }
    public void setTier(TierColumn tier) { this.tier = tier; }

    public Instant getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(Instant enrolledAt) { this.enrolledAt = enrolledAt; }
}
