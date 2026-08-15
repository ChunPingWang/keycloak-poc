package com.shopmall.membership.application.port.in;

import com.shopmall.membership.domain.model.IdentityId;

public interface GetMemberProfileUseCase {

    MemberProfile byIdentity(IdentityId identityId);

    record MemberProfile(String memberId, String email, String name,
                         String status, String tier) {}
}
