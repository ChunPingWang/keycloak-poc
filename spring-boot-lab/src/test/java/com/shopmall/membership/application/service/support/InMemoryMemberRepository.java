package com.shopmall.membership.application.service.support;

import com.shopmall.membership.application.port.out.MemberRepository;
import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.Member;
import com.shopmall.membership.domain.model.MemberId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryMemberRepository implements MemberRepository {

    private final Map<MemberId, Member> store = new HashMap<>();

    @Override
    public void save(Member member) {
        store.put(member.id(), member);
    }

    @Override
    public Optional<Member> findById(MemberId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Member> findByIdentityId(IdentityId identityId) {
        return store.values().stream()
                .filter(m -> m.identityId().equals(identityId))
                .findFirst();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return store.values().stream().anyMatch(m -> m.email().equals(email));
    }
}
