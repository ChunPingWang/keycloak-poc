package com.shopmall.membership.domain.exception;

import com.shopmall.membership.domain.model.Email;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(Email email) {
        super("Email 已被註冊: " + email.value());
    }
}
