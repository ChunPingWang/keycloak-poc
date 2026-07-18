package com.shopmall.membership.infrastructure.persistence;

import com.shopmall.membership.application.port.out.MemberRepository;
import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.Member;
import com.shopmall.membership.domain.model.MemberId;
import com.shopmall.membership.domain.model.MemberName;
import com.shopmall.membership.domain.model.MemberStatus;
import com.shopmall.membership.domain.model.MembershipTier;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** MemberRepository 的 JPA 實作：負責聚合 ↔ JPA 實體的雙向轉換。 */
@Component
public class MemberRepositoryAdapter implements MemberRepository {

    private final MemberJpaRepository jpa;

    public MemberRepositoryAdapter(MemberJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Member member) {
        var entity = new MemberJpaEntity();
        entity.setId(member.id().value());
        entity.setIdentityId(member.identityId().value());
        entity.setEmail(member.email().value());
        entity.setName(member.name().value());
        entity.setStatus(MemberJpaEntity.StatusColumn.valueOf(member.status().name()));
        entity.setTier(MemberJpaEntity.TierColumn.valueOf(member.tier().name()));
        entity.setEnrolledAt(member.enrolledAt());
        jpa.save(entity);
    }

    @Override
    public Optional<Member> findById(MemberId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Member> findByIdentityId(IdentityId identityId) {
        return jpa.findByIdentityId(identityId.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email.value());
    }

    private Member toDomain(MemberJpaEntity e) {
        return Member.reconstitute(
                new MemberId(e.getId()),
                new IdentityId(e.getIdentityId()),
                new Email(e.getEmail()),
                new MemberName(e.getName()),
                MemberStatus.valueOf(e.getStatus().name()),
                MembershipTier.valueOf(e.getTier().name()),
                e.getEnrolledAt());
    }
}
