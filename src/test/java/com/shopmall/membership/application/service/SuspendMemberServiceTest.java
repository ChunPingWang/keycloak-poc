package com.shopmall.membership.application.service;

import com.shopmall.membership.application.port.in.EnrollMemberUseCase.EnrollMemberCommand;
import com.shopmall.membership.application.service.support.InMemoryIdentityProvisioning;
import com.shopmall.membership.application.service.support.InMemoryMemberRepository;
import com.shopmall.membership.application.service.support.RecordingEventPublisher;
import com.shopmall.membership.domain.event.MemberSuspended;
import com.shopmall.membership.domain.exception.MemberAlreadySuspendedException;
import com.shopmall.membership.domain.model.MemberStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuspendMemberServiceTest {

    InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
    InMemoryIdentityProvisioning identityProvisioning = new InMemoryIdentityProvisioning();
    RecordingEventPublisher events = new RecordingEventPublisher();
    Clock clock = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC);

    EnrollMemberService enroll = new EnrollMemberService(
            memberRepository, identityProvisioning, events, clock);
    SuspendMemberService service = new SuspendMemberService(
            memberRepository, identityProvisioning, events, clock);

    @Test
    void 停權會更新會員狀態_並同步停用IAM帳號() {
        var memberId = enroll.enroll(new EnrollMemberCommand(
                "alice@example.com", "Alice", "alice-secret-123"));

        service.suspend(memberId);

        var member = memberRepository.findById(memberId).orElseThrow();
        assertEquals(MemberStatus.SUSPENDED, member.status());
        assertTrue(identityProvisioning.disabled.contains(member.identityId().value()));
        assertInstanceOf(MemberSuspended.class, events.published().getLast());
    }

    @Test
    void 重複停權擲出例外_且不重複呼叫IAM() {
        var memberId = enroll.enroll(new EnrollMemberCommand(
                "alice@example.com", "Alice", "alice-secret-123"));
        service.suspend(memberId);

        assertThrows(MemberAlreadySuspendedException.class, () -> service.suspend(memberId));
        assertEquals(1, identityProvisioning.disabled.size());
    }
}
