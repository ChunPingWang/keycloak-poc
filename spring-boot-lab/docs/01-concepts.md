# 第 1 章：核心概念

在動手整合之前，先建立正確的心智模型。本章介紹 OAuth 2.0 / OIDC 的角色分工，以及 Keycloak 的核心名詞。

## 1.1 為什麼需要獨立的 IAM 伺服器？

傳統做法是每個應用程式自己管理帳號密碼（自建 `users` 資料表、自己雜湊密碼、自己寫登入頁）。這種做法的問題：

1. **安全風險集中在你身上**：密碼雜湊、暴力破解防護、MFA、密碼重設流程……每一項都是專業工程，自己做很容易出錯。
2. **無法單一登入（SSO）**：電商系統通常不只一個應用（商城、後台、客服系統），各自管帳號會讓使用者反覆登入。
3. **違反關注點分離**：「驗證你是誰」與「你在系統中的領域身分（會員）」是兩件事，混在一起會讓領域模型被認證細節污染。

**Keycloak** 是開源的 IAM（Identity and Access Management）伺服器，把上述通用問題整包解決：登入頁、密碼原則、MFA、社群登入（Google/Facebook）、SSO、Token 簽發與管理。

> **DDD 視角**：認證是「通用子領域（Generic Subdomain）」——它重要，但不是你的競爭優勢，應該用現成方案。你的建模精力應該花在核心領域（例如訂單、定價）與支撐子領域（例如會員管理）上。

## 1.2 OAuth 2.0 與 OpenID Connect（OIDC）

- **OAuth 2.0**：授權（Authorization）框架，解決「應用程式如何在使用者同意下取得存取資源的權限」。產出 **Access Token**。
- **OpenID Connect（OIDC）**：建立在 OAuth 2.0 之上的認證（Authentication）層，解決「使用者是誰」。額外產出 **ID Token**，並定義標準的使用者資訊端點（UserInfo Endpoint）。

### 四種角色

| OAuth2 角色 | 在本專案中對應 |
|---|---|
| Resource Owner（資源擁有者） | 會員本人 |
| Client（用戶端） | 前端應用（SPA / Mobile App） |
| Authorization Server（授權伺服器） | **Keycloak** |
| Resource Server（資源伺服器） | **我們的 Spring Boot 後端** |

### Authorization Code Flow（含 PKCE）

現代 Web/Mobile 應用建議使用的流程：

```
會員          前端(Client)         Keycloak              Spring Boot 後端
 │  點「登入」    │                    │                        │
 │──────────────>│  重導向至登入頁      │                        │
 │               │───────────────────>│                        │
 │  輸入帳密（在 Keycloak 頁面上）      │                        │
 │──────────────────────────────────->│                        │
 │               │  授權碼(code)       │                        │
 │               │<───────────────────│                        │
 │               │  用 code+PKCE 換 Token                      │
 │               │───────────────────>│                        │
 │               │  Access/ID/Refresh Token                    │
 │               │<───────────────────│                        │
 │               │  帶著 Access Token 呼叫 API                  │
 │               │────────────────────────────────────────────>│
 │               │                    │   驗證 JWT 簽章與內容    │
 │               │                    │<──(離線驗證，用 JWKS)──>│
 │               │  回傳資源           │                        │
 │               │<────────────────────────────────────────────│
```

**關鍵**：後端（Resource Server）**從不經手密碼**，也不需要呼叫 Keycloak 驗證每個請求——它下載 Keycloak 的公鑰（JWKS），離線驗證 JWT 簽章即可。

## 1.3 JWT（JSON Web Token）

Keycloak 簽發的 Access Token 是 JWT，由三段組成：`header.payload.signature`。解碼後的 payload（Claims）範例：

```json
{
  "exp": 1752830000,
  "iat": 1752829700,
  "iss": "http://localhost:8080/realms/shopmall",
  "sub": "f3a1c2d4-5678-90ab-cdef-1234567890ab",
  "typ": "Bearer",
  "azp": "shopmall-web",
  "preferred_username": "alice",
  "email": "alice@example.com",
  "realm_access": {
    "roles": ["member"]
  },
  "resource_access": {
    "shopmall-web": { "roles": [] }
  },
  "scope": "openid profile email"
}
```

重要的 Claims：

| Claim | 意義 | 本專案的用法 |
|---|---|---|
| `iss` | 簽發者（Realm URL） | Spring 用來驗證 Token 來源 |
| `sub` | 使用者在 Keycloak 的唯一 ID | **會員聚合與 Keycloak 帳號的對應鍵**（值物件 `IdentityId`） |
| `exp` / `iat` | 過期／簽發時間 | Spring 自動驗證 |
| `realm_access.roles` | Realm 層級角色 | 轉換為 Spring Security 的 `GrantedAuthority` |
| `preferred_username` / `email` | 使用者名稱／Email | 顯示用途 |

## 1.4 Keycloak 核心名詞

| 名詞 | 說明 | 本專案的值 |
|---|---|---|
| **Realm** | 隔離的租戶空間，各自擁有使用者、Client、角色。 | `shopmall` |
| **Client** | 向 Keycloak 註冊、要求 Token 的應用程式。 | `shopmall-web`（前端，public）、`shopmall-backend`（後端服務帳號，confidential） |
| **Realm Role** | 整個 Realm 通用的角色。 | `member`、`customer-service` |
| **Client Role** | 只屬於某個 Client 的角色。 | 本專案未使用（保持簡單） |
| **User** | Realm 中的使用者帳號。 | 由會員註冊流程建立 |
| **Service Account** | Client 以自己的身分（非使用者）取得 Token 的機制，用於機器對機器呼叫。 | 後端呼叫 Keycloak Admin API 時使用 |
| **JWKS Endpoint** | 提供驗證 JWT 用的公鑰。 | `/realms/shopmall/protocol/openid-connect/certs` |

## 1.5 認證（Authentication） vs. 授權（Authorization）

- **認證**：你是誰？→ Keycloak 全權負責。
- **授權**：你能做什麼？→ 分兩層：
  - **粗粒度**（你有沒有 `customer-service` 角色？）→ Spring Security + Keycloak 角色。
  - **細粒度**（你只能改「自己的」個人資料）→ 這是**領域規則**，寫在應用層／領域層，不塞進 Security 設定裡。

這個區分在第 3、5 章會反覆出現，是本指南最重要的設計判準之一。

---

下一章：[第 2 章：環境建置](02-environment-setup.md)
