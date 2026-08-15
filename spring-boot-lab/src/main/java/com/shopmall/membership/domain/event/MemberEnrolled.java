package com.shopmall.membership.domain.event;

import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.MemberId;

import java.time.Instant;

public record MemberEnrolled(MemberId memberId, Email email, Instant occurredAt) {}
