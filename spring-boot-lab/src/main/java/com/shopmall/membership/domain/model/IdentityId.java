package com.shopmall.membership.domain.model;

import java.util.Objects;

/** 會員對應的身分帳號識別（等於 IAM 系統中的使用者 ID / JWT 的 sub）。 */
public record IdentityId(String value) {

    public IdentityId {
        Objects.requireNonNull(value, "IdentityId 不可為 null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("IdentityId 不可為空白");
        }
    }
}
