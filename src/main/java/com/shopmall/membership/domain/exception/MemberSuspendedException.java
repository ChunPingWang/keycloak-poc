package com.shopmall.membership.domain.exception;

import com.shopmall.membership.domain.model.MemberId;

public class MemberSuspendedException extends RuntimeException {

    public MemberSuspendedException(MemberId id) {
        super("會員已停權，不可執行此操作: " + id.value());
    }
}
