package com.shopmall.membership.domain.model;

import java.util.Objects;
import java.util.UUID;

/** 會員在本上下文中的識別（領域自己的 ID，與 Keycloak 無關）。 */
public record MemberId(UUID value) {

    public MemberId {
        Objects.requireNonNull(value, "MemberId 不可為 null");
    }

    public static MemberId newId() {
        return new MemberId(UUID.randomUUID());
    }
}
