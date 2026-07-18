package com.shopmall.membership.domain.model;

import com.shopmall.membership.domain.exception.MemberAlreadySuspendedException;
import com.shopmall.membership.domain.exception.MemberSuspendedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberTest {

    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");

    private Member newMember() {
        return Member.enroll(
                new IdentityId("kc-user-1"),
                new Email("alice@example.com"),
                new MemberName("Alice"),
                NOW);
    }

    @Test
    void 新註冊會員為_ACTIVE_且_STANDARD() {
        var member = newMember();
        assertEquals(MemberStatus.ACTIVE, member.status());
        assertEquals(MembershipTier.STANDARD, member.tier());
    }

    @Test
    void 停權後不可更新資料() {
        var member = newMember();
        member.suspend();
        assertThrows(MemberSuspendedException.class,
                () -> member.updateProfile(new MemberName("New Name")));
    }

    @Test
    void 不可重複停權() {
        var member = newMember();
        member.suspend();
        assertThrows(MemberAlreadySuspendedException.class, member::suspend);
    }

    @Test
    void email_格式錯誤時無法建立值物件() {
        assertThrows(IllegalArgumentException.class, () -> new Email("not-an-email"));
    }

    @Test
    void email_一律轉為小寫() {
        assertEquals("alice@example.com", new Email("Alice@Example.COM").value());
    }
}
