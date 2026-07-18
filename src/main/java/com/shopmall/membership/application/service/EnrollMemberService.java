package com.shopmall.membership.application.service;

import com.shopmall.membership.application.port.in.EnrollMemberUseCase;
import com.shopmall.membership.application.port.out.DomainEventPublisher;
import com.shopmall.membership.application.port.out.IdentityProvisioningPort;
import com.shopmall.membership.application.port.out.IdentityProvisioningPort.RawPassword;
import com.shopmall.membership.application.port.out.MemberRepository;
import com.shopmall.membership.domain.event.MemberEnrolled;
import com.shopmall.membership.domain.exception.DuplicateEmailException;
import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.Member;
import com.shopmall.membership.domain.model.MemberId;
import com.shopmall.membership.domain.model.MemberName;

import java.time.Clock;
import java.time.Instant;

/**
 * 註冊使用案例：
 * 1. 檢查 Email 未被使用
 * 2. 在 IAM 建立帳號（取得 IdentityId）
 * 3. 建立會員聚合並持久化（失敗時補償：停用剛建立的帳號）
 * 4. 發布 MemberEnrolled 事件
 */
public class EnrollMemberService implements EnrollMemberUseCase {

    private final MemberRepository memberRepository;
    private final IdentityProvisioningPort identityProvisioning;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public EnrollMemberService(MemberRepository memberRepository,
                               IdentityProvisioningPort identityProvisioning,
                               DomainEventPublisher eventPublisher,
                               Clock clock) {
        this.memberRepository = memberRepository;
        this.identityProvisioning = identityProvisioning;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public MemberId enroll(EnrollMemberCommand command) {
        var email = new Email(command.email());
        var name = new MemberName(command.name());

        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        // 先建 IAM 帳號：失敗則整個註冊失敗，不會產生「有會員沒帳號」的狀態。
        var identityId = identityProvisioning.provision(
                email, name, new RawPassword(command.rawPassword()));

        Instant now = clock.instant();
        var member = Member.enroll(identityId, email, name, now);
        try {
            memberRepository.save(member);
        } catch (RuntimeException e) {
            // 補償：避免留下「有帳號、無會員」的孤兒帳號（詳見附錄 A.3）
            identityProvisioning.disable(identityId);
            throw e;
        }

        eventPublisher.publish(new MemberEnrolled(member.id(), email, now));
        return member.id();
    }
}
