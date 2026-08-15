package com.shopmall.membership.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakRealmRoleConverterTest {

    KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private Jwt jwtWithClaims(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("user-1");
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void realm_roles_轉為_ROLE_前綴權限() {
        var jwt = jwtWithClaims(Map.of(
                "realm_access", Map.of("roles", List.of("member", "customer-service"))));

        var authorities = converter.convert(jwt).stream()
                .map(GrantedAuthority::getAuthority).toList();

        assertEquals(List.of("ROLE_member", "ROLE_customer-service"), authorities);
    }

    @Test
    void 缺少_realm_access_時回傳空集合() {
        var jwt = jwtWithClaims(Map.of("scope", "openid"));
        assertTrue(converter.convert(jwt).isEmpty());
    }

    @Test
    void realm_access_無_roles_鍵時回傳空集合() {
        var jwt = jwtWithClaims(Map.of("realm_access", Map.of("other", "x")));
        assertTrue(converter.convert(jwt).isEmpty());
    }
}
