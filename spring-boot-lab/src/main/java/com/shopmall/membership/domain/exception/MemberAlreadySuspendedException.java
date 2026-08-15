package com.shopmall.membership.domain.exception;

import com.shopmall.membership.domain.model.MemberId;

public class MemberAlreadySuspendedException extends RuntimeException {

    public MemberAlreadySuspendedException(MemberId id) {
        super("會員已處於停權狀態: " + id.value());
    }
}
