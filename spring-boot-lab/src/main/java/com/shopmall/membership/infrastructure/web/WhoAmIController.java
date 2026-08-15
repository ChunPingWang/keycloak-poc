package com.shopmall.membership.infrastructure.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 教學用端點：檢視 Authentication 結果（JWT claims 與轉換後的權限）。 */
@RestController
public class WhoAmIController {

    @GetMapping("/api/whoami")
    Map<String, Object> whoami(@AuthenticationPrincipal Jwt jwt,
                               Authentication authentication) {
        return Map.of(
                "sub", jwt.getSubject(),
                "email", String.valueOf(jwt.getClaimAsString("email")),
                "authorities", authentication.getAuthorities().toString());
    }
}
