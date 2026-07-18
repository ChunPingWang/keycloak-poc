# 第 5 章：Spring Boot 與 Keycloak 整合

本章建立 Spring Boot 專案，設定為 OAuth2 Resource Server，完成 JWT 驗證、角色轉換與授權規則。

## 5.1 專案相依（`pom.xml` 重點）

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
</parent>

<properties>
    <java.version>21</java.version>
    <keycloak.version>26.0.3</keycloak.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- Resource Server：驗證 Keycloak 簽發的 JWT -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <!-- Keycloak Admin Client：後端程式化管理使用者（僅 infrastructure 層使用） -->
    <dependency>
        <groupId>org.keycloak</groupId>
        <artifactId>keycloak-admin-client</artifactId>
        <version>${keycloak.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

> 注意：**不要**使用已停止維護的 `keycloak-spring-boot-starter`（Keycloak 官方已於多年前棄用）。Spring Security 原生的 Resource Server 支援就是正解。

## 5.2 `application.yml`

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/shopmall

  datasource:
    url: jdbc:postgresql://localhost:5432/shopmall
    username: shopmall
    password: shopmall
  jpa:
    hibernate:
      ddl-auto: validate

server:
  port: 9090

shopmall:
  keycloak:
    server-url: http://localhost:8080
    realm: shopmall
    admin-client-id: shopmall-backend
    admin-client-secret: ${KEYCLOAK_ADMIN_CLIENT_SECRET}   # 由環境變數注入，勿寫死
    member-role: member
```

`issuer-uri` 是唯一必要設定：Spring 啟動時會抓取 `.well-known/openid-configuration`，自動取得 JWKS 位址並快取公鑰，之後每個請求都**離線**驗證 JWT 簽章、`iss`、`exp`。

## 5.3 角色轉換器：`KeycloakRealmRoleConverter`

Keycloak 把 Realm 角色放在 `realm_access.roles`，但 Spring Security 預設只讀 `scope` claim（產生 `SCOPE_xxx` 權限）。我們需要一個轉換器（SRP：這個類別唯一的職責就是「JWT → 權限」的對應）：

```java
package com.shopmall.membership.infrastructure.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 把 Keycloak JWT 中 realm_access.roles 轉為 Spring Security 權限。
 * 例："member" -> ROLE_member
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null || !(realmAccess.get(ROLES_KEY) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(Object::toString)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }
}
```

## 5.4 Security 設定：`SecurityConfig`

```java
package com.shopmall.membership.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 啟用 @PreAuthorize
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Resource Server 是無狀態 API：關閉 session 與 CSRF
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // 註冊是匿名端點（訪客才需要註冊）
                .requestMatchers(HttpMethod.POST, "/api/members").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // 其餘一律需要認證；細部授權交給方法級註解與領域規則
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
```

設計說明：

- **URL 規則只做粗粒度控制**（登入與否）；「客服才能停權」放在方法級 `@PreAuthorize`，「只能改自己的資料」放在應用層——授權的三個層次各就各位（呼應 1.5 節）。
- `SessionCreationPolicy.STATELESS` + 關閉 CSRF 是 Bearer Token API 的標準組合（CSRF 攻擊依賴瀏覽器自動附加的憑證，Bearer Token 不會被自動附加）。

## 5.5 從 JWT 取得「目前登入者」：`AuthenticatedIdentity`

Controller 需要知道「呼叫者的 IdentityId」。我們不讓 Controller 直接翻 `SecurityContextHolder`（那是靜態依賴、難以測試），而是包成一個小元件（SRP + DIP）：

```java
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
```

## 5.6 方法級授權

在後台 Controller 上使用角色檢查（第 6 章有完整程式碼）：

```java
@PreAuthorize("hasRole('customer-service')")
@PostMapping("/api/admin/members/{memberId}/suspension")
public ResponseEntity<Void> suspend(@PathVariable UUID memberId) { ... }
```

`hasRole('customer-service')` 會比對 `ROLE_customer-service` 權限——正是 5.3 節轉換器產生的格式。

## 5.7 快速驗證

寫一個臨時端點檢查整合是否成功：

```java
@RestController
class WhoAmIController {
    @GetMapping("/api/whoami")
    Map<String, Object> whoami(@AuthenticationPrincipal Jwt jwt,
                               Authentication authentication) {
        return Map.of(
            "sub", jwt.getSubject(),
            "email", String.valueOf(jwt.getClaimAsString("email")),
            "authorities", authentication.getAuthorities().toString());
    }
}
```

```bash
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/shopmall/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=shopmall-web" \
  -d "username=cs-bob" -d "password=bob-secret" | jq -r .access_token)

curl -s http://localhost:9090/api/whoami -H "Authorization: Bearer $TOKEN" | jq
```

預期輸出：

```json
{
  "sub": "f3a1c2d4-....",
  "email": "bob@shopmall.dev",
  "authorities": "[ROLE_customer-service, ROLE_member, ...]"
}
```

看到角色以 `ROLE_` 前綴出現，整合就完成了。驗證後請移除此臨時端點。

---

下一章：[第 6 章：會員管理實作](06-member-management.md)
