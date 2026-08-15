package com.shopmall.membership.application.service;

import com.shopmall.membership.application.port.in.GetMemberProfileUseCase;
import com.shopmall.membership.application.port.in.UpdateMemberProfileUseCase;
import com.shopmall.membership.application.port.out.MemberRepository;
import com.shopmall.membership.domain.exception.MemberNotFoundException;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.MemberName;

public class MemberProfileService
        implements GetMemberProfileUseCase, UpdateMemberProfileUseCase {

    private final MemberRepository memberRepository;

    public MemberProfileService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public MemberProfile byIdentity(IdentityId identityId) {
        var member = memberRepository.findByIdentityId(identityId)
                .orElseThrow(() -> new MemberNotFoundException(identityId));
        return new MemberProfile(
                member.id().value().toString(),
                member.email().value(),
                member.name().value(),
                member.status().name(),
                member.tier().name());
    }

    @Override
    public void update(IdentityId identityId, String newName) {
        // 「只能改自己」的授權在此天然成立：
        // 查詢鍵就是呼叫者自己的 IdentityId（來自已驗證的 JWT sub），
        // 使用案例根本沒有提供「改別人」的入口。
        var member = memberRepository.findByIdentityId(identityId)
                .orElseThrow(() -> new MemberNotFoundException(identityId));
        member.updateProfile(new MemberName(newName));
        memberRepository.save(member);
    }
}
