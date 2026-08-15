package com.shopmall.membership.application.port.out;

import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.MemberName;

/**
 * 身分供裝 Port：會員上下文對 IAM 系統的全部需求。
 * 介面形狀由使用案例決定，與 Keycloak API 無關（防腐層）。
 */
public interface IdentityProvisioningPort {

    /**
     * 建立可登入的身分帳號並賦予會員角色。
     * 契約：
     * - 成功時回傳非 null 的 IdentityId；
     * - Email 已存在時擲出 DuplicateIdentityException（不得回傳 null 或默默成功）；
     * - 不得部分成功（帳號建立失敗時不得留下殘留狀態）。
     */
    IdentityId provision(Email email, MemberName name, RawPassword password);

    /**
     * 停用帳號，使其無法再登入、既有 session 失效。
     * 契約：帳號不存在時擲出 IdentityNotFoundException。
     */
    void disable(IdentityId identityId);

    /** 密碼包裝型別：toString 遮罩，避免意外進入日誌。 */
    record RawPassword(String value) {
        public RawPassword {
            if (value == null || value.length() < 8) {
                throw new IllegalArgumentException("密碼至少 8 碼");
            }
        }

        @Override
        public String toString() { return "RawPassword[****]"; }
    }
}
