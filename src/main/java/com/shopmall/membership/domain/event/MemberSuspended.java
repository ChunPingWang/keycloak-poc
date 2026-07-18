package com.shopmall.membership.domain.event;

import com.shopmall.membership.domain.model.MemberId;

import java.time.Instant;

public record MemberSuspended(MemberId memberId, Instant occurredAt) {}
