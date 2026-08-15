package com.shopmall.membership.application.service;

import com.shopmall.membership.application.port.in.EnrollMemberUseCase.EnrollMemberCommand;
import com.shopmall.membership.application.service.support.InMemoryIdentityProvisioning;
import com.shopmall.membership.application.service.support.InMemoryMemberRepository;
import com.shopmall.membership.application.service.support.RecordingEventPublisher;
import com.shopmall.membership.domain.event.MemberEnrolled;
import com.shopmall.membership.domain.exception.DuplicateEmailException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnrollMemberServiceTest {

    InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
    InMemoryIdentityProvisioning identityProvisioning = new InMemoryIdentityProvisioning();
    RecordingEventPublisher events = new RecordingEventPublisher();
    Clock clock = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC);

    EnrollMemberService service = new EnrollMemberService(
            memberRepository, identityProvisioning, events, clock);

    @Test
    void 註冊成功_會建立IAM帳號_存會員_發事件() {
        var memberId = service.enroll(new EnrollMemberCommand(
                "alice@example.com", "Alice", "alice-secret-123"));

        var member = memberRepository.findById(memberId).orElseThrow();
        assertEquals("kc-1", member.identityId().value());
        assertEquals(1, identityProvisioning.provisioned.size());
        assertInstanceOf(MemberEnrolled.class, events.published().getFirst());
    }

    @Test
    void email_重複時擲出_DuplicateEmailException_且不建立IAM帳號() {
        service.enroll(new EnrollMemberCommand("alice@example.com", "Alice", "alice-secret-123"));

        assertThrows(DuplicateEmailException.class, () ->
                service.enroll(new EnrollMemberCommand("alice@example.com", "Bob", "bob-secret-123")));
        assertEquals(1, identityProvisioning.provisioned.size());
    }
}
