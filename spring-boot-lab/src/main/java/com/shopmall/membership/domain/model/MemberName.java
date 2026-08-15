package com.shopmall.membership.domain.model;

/** 會員顯示名稱：1~50 字。 */
public record MemberName(String value) {

    public MemberName {
        if (value == null || value.isBlank() || value.strip().length() > 50) {
            throw new IllegalArgumentException("會員名稱必須為 1~50 字");
        }
        value = value.strip();
    }
}
