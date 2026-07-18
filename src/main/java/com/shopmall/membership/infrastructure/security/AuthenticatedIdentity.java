package com.shopmall.membership.infrastructure.security;

import com.shopmall.membership.domain.model.IdentityId;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** 提供目前請求的登入者身分（來自 JWT 的 sub claim）。 */
@Component
public class AuthenticatedIdentity {

    public IdentityId currentIdentityId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return new IdentityId(jwt.getSubject());
        }
        throw new IllegalStateException("目前請求沒有已認證的 JWT");
    }
}
