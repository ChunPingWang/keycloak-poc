package com.shopmall.membership.application.port.in;

import com.shopmall.membership.domain.model.MemberId;

public interface SuspendMemberUseCase {

    void suspend(MemberId memberId);
}
