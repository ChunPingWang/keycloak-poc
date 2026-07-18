package com.shopmall.membership.domain.model;

import com.shopmall.membership.domain.exception.MemberAlreadySuspendedException;
import com.shopmall.membership.domain.exception.MemberSuspendedException;

import java.time.Instant;
import java.util.Objects;

/**
 * 會員聚合根。
 * 不變條件：
 * 1. 停權中的會員不可更新資料、不可再次停權。
 * 2. Email、名稱恆為合法值（由值物件保證）。
 */
public class Member {

    private final MemberId id;
    private final IdentityId identityId;
    private Email email;
    private MemberName name;
    private MemberStatus status;
    private MembershipTier tier;
    private final Instant enrolledAt;

    private Member(MemberId id, IdentityId identityId, Email email,
                   MemberName name, MemberStatus status,
                   MembershipTier tier, Instant enrolledAt) {
        this.id = Objects.requireNonNull(id);
        this.identityId = Objects.requireNonNull(identityId);
        this.email = Objects.requireNonNull(email);
        this.name = Objects.requireNonNull(name);
        this.status = Objects.requireNonNull(status);
        this.tier = Objects.requireNonNull(tier);
        this.enrolledAt = Objects.requireNonNull(enrolledAt);
    }

    /** 工廠方法：註冊新會員。新會員一律為 ACTIVE / STANDARD。 */
    public static Member enroll(IdentityId identityId, Email email,
                                MemberName name, Instant now) {
        return new Member(MemberId.newId(), identityId, email, name,
                MemberStatus.ACTIVE, MembershipTier.STANDARD, now);
    }

    /** 還原（reconstitute）：僅供持久化層由資料庫重建聚合使用。 */
    public static Member reconstitute(MemberId id, IdentityId identityId,
                                      Email email, MemberName name,
                                      MemberStatus status, MembershipTier tier,
                                      Instant enrolledAt) {
        return new Member(id, identityId, email, name, status, tier, enrolledAt);
    }

    /** 更新個人資料。停權中的會員不可更新。 */
    public void updateProfile(MemberName newName) {
        requireActive();
        this.name = newName;
    }

    /** 停權。 */
    public void suspend() {
        if (status == MemberStatus.SUSPENDED) {
            throw new MemberAlreadySuspendedException(id);
        }
        this.status = MemberStatus.SUSPENDED;
    }

    private void requireActive() {
        if (status != MemberStatus.ACTIVE) {
            throw new MemberSuspendedException(id);
        }
    }

    public MemberId id() { return id; }
    public IdentityId identityId() { return identityId; }
    public Email email() { return email; }
    public MemberName name() { return name; }
    public MemberStatus status() { return status; }
    public MembershipTier tier() { return tier; }
    public Instant enrolledAt() { return enrolledAt; }
}
