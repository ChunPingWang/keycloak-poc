package com.shopmall.membership.domain.exception;

public class IdentityProvisioningException extends RuntimeException {

    public IdentityProvisioningException(String message) {
        super(message);
    }

    public IdentityProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
