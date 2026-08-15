package com.shopmall.membership.domain.exception;

import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.MemberId;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(MemberId id) {
        super("找不到會員: " + id.value());
    }

    public MemberNotFoundException(IdentityId identityId) {
        super("找不到對應此身分的會員: " + identityId.value());
    }
}
