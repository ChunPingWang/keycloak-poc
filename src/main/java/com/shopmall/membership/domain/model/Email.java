package com.shopmall.membership.domain.model;

import java.util.regex.Pattern;

/** Email 值物件：自我驗證，聚合內不可能存在非法 Email。 */
public record Email(String value) {

    private static final Pattern FORMAT =
            Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    public Email {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Email 格式不正確: " + value);
        }
        value = value.toLowerCase();
    }
}
