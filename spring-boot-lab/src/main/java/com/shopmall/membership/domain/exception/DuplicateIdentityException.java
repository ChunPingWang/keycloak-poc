package com.shopmall.membership.domain.exception;

import com.shopmall.membership.domain.model.Email;

public class DuplicateIdentityException extends RuntimeException {

    public DuplicateIdentityException(Email email) {
        super("身分帳號已存在: " + email.value());
    }
}
