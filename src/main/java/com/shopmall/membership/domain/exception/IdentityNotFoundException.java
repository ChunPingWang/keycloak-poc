package com.shopmall.membership.domain.exception;

import com.shopmall.membership.domain.model.IdentityId;

public class IdentityNotFoundException extends RuntimeException {

    public IdentityNotFoundException(IdentityId identityId) {
        super("找不到身分帳號: " + identityId.value());
    }
}
