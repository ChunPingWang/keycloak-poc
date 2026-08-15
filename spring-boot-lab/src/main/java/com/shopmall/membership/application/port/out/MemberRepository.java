package com.shopmall.membership.application.port.out;

import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.Member;
import com.shopmall.membership.domain.model.MemberId;

import java.util.Optional;

public interface MemberRepository {

    void save(Member member);

    Optional<Member> findById(MemberId id);

    Optional<Member> findByIdentityId(IdentityId identityId);

    boolean existsByEmail(Email email);
}
