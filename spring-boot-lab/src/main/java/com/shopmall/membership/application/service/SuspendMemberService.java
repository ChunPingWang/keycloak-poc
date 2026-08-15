package com.shopmall.membership.application.service;

import com.shopmall.membership.application.port.in.SuspendMemberUseCase;
import com.shopmall.membership.application.port.out.DomainEventPublisher;
import com.shopmall.membership.application.port.out.IdentityProvisioningPort;
import com.shopmall.membership.application.port.out.MemberRepository;
import com.shopmall.membership.domain.event.MemberSuspended;
import com.shopmall.membership.domain.exception.MemberNotFoundException;
import com.shopmall.membership.domain.model.MemberId;

import java.time.Clock;

public class SuspendMemberService implements SuspendMemberUseCase {

    private final MemberRepository memberRepository;
    private final IdentityProvisioningPort identityProvisioning;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public SuspendMemberService(MemberRepository memberRepository,
                                IdentityProvisioningPort identityProvisioning,
                                DomainEventPublisher eventPublisher,
                                Clock clock) {
        this.memberRepository = memberRepository;
        this.identityProvisioning = identityProvisioning;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public void suspend(MemberId memberId) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));

        member.suspend();                                  // 領域規則（不可重複停權）在聚合內
        memberRepository.save(member);
        identityProvisioning.disable(member.identityId()); // 同步停用登入能力

        eventPublisher.publish(new MemberSuspended(memberId, clock.instant()));
    }
}
