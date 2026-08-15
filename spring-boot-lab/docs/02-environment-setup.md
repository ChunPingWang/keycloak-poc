# 第 2 章：環境建置

本章用 Docker Compose 建立本機開發環境：Keycloak（含專用資料庫）與應用程式資料庫，接著設定 Realm、Client、角色與測試使用者。

## 2.1 Docker Compose

在專案根目錄建立 `docker-compose.yml`：

```yaml
services:
  keycloak-db:
    image: postgres:16
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: keycloak
    volumes:
      - keycloak-db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U keycloak"]
      interval: 5s
      timeout: 5s
      retries: 10

  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    command: start-dev
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://keycloak-db:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: keycloak
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
      KC_HTTP_PORT: 8080
    ports:
      - "8080:8080"
    depends_on:
      keycloak-db:
        condition: service_healthy

  shopmall-db:
    image: postgres:16
    environment:
      POSTGRES_DB: shopmall
      POSTGRES_USER: shopmall
      POSTGRES_PASSWORD: shopmall
    ports:
      - "5432:5432"
    volumes:
      - shopmall-db-data:/var/lib/postgresql/data

volumes:
  keycloak-db-data:
  shopmall-db-data:
```

啟動：

```bash
docker compose up -d
```

- Keycloak 管理主控台：<http://localhost:8080>（帳密 `admin` / `admin`）
- 應用程式資料庫：`localhost:5432`（`shopmall` / `shopmall`）

> `start-dev` 只適合開發環境（HTTP、寬鬆的主機檢查）。正式環境請改用 `start` 並設定 TLS、`KC_HOSTNAME` 等，詳見附錄 A。

## 2.2 建立 Realm

1. 登入管理主控台，左上角 Realm 下拉選單 → **Create realm**。
2. **Realm name** 輸入 `shopmall` → **Create**。

之後所有設定都在 `shopmall` Realm 底下進行（注意左上角不要停留在 `master`）。

## 2.3 建立 Realm 角色

**Realm roles** → **Create role**，建立兩個角色：

| 角色名稱 | 用途 |
|---|---|
| `member` | 一般會員，註冊後自動賦予 |
| `customer-service` | 客服人員，可停權會員 |

### 讓新使用者自動獲得 `member` 角色

**Realm settings** → **User registration** 分頁 → **Default roles**，把 `member` 加入預設角色。這樣後端透過 Admin API 建立的使用者也會自動有 `member` 角色（若你的 Keycloak 版本沒有此介面，可在建立使用者後由後端指派角色，第 6 章的程式碼即採取顯式指派，兩者擇一即可）。

## 2.4 建立 Clients

### 2.4.1 前端 Client：`shopmall-web`（Public）

**Clients** → **Create client**：

| 設定 | 值 |
|---|---|
| Client type | OpenID Connect |
| Client ID | `shopmall-web` |
| Client authentication | **Off**（Public client，SPA 無法保存秘密） |
| Standard flow | ✅（Authorization Code Flow） |
| Direct access grants | 開發測試時可暫時開啟（見 2.6），正式環境建議關閉 |
| Valid redirect URIs | `http://localhost:3000/*` |
| Web origins | `http://localhost:3000` |

> Public client 務必搭配 **PKCE**：在 Client 的 **Advanced** 分頁將 *Proof Key for Code Exchange Code Challenge Method* 設為 `S256`。

### 2.4.2 後端 Client：`shopmall-backend`（Confidential，Service Account）

後端需要呼叫 Keycloak Admin API（建立使用者、停用帳號），因此需要一個有服務帳號的 confidential client：

| 設定 | 值 |
|---|---|
| Client ID | `shopmall-backend` |
| Client authentication | **On**（Confidential） |
| Standard flow | Off |
| Service accounts roles | **On** |

建立後：

1. **Credentials** 分頁 → 複製 **Client Secret**（供 Spring 設定使用）。
2. **Service accounts roles** 分頁 → **Assign role** → 篩選 *Filter by clients* → 指派 `realm-management` 底下的：
   - `manage-users`（建立、停用使用者）
   - `view-users`（查詢使用者）

> **最小權限原則**：只給 `manage-users` 與 `view-users`，不要偷懶指派 `realm-admin`。這也是 SOLID 之外的資安基本功。

## 2.5 建立測試使用者（客服人員）

一般會員將由第 6 章的註冊 API 建立，這裡先手動建一個客服帳號方便測試：

1. **Users** → **Create new user**：Username `cs-bob`、Email `bob@shopmall.dev`、Email verified 開啟。
2. **Credentials** 分頁 → **Set password**：`bob-secret`，**Temporary 關閉**。
3. **Role mapping** 分頁 → **Assign role** → 指派 `customer-service` 與 `member`。

## 2.6 驗證環境：手動取得 Token

開發階段可暫時開啟 `shopmall-web` 的 **Direct access grants**（Resource Owner Password Flow）來快速取 Token 測試：

```bash
curl -s -X POST \
  "http://localhost:8080/realms/shopmall/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=shopmall-web" \
  -d "username=cs-bob" \
  -d "password=bob-secret" | jq -r .access_token
```

把回傳的 `access_token` 貼到 <https://jwt.io> 解碼，確認：

- `iss` 為 `http://localhost:8080/realms/shopmall`
- `realm_access.roles` 包含 `customer-service` 與 `member`

> **提醒**：Password Flow 已在 OAuth 2.1 中被移除，僅供本機開發驗證使用，正式環境一律使用 Authorization Code Flow + PKCE。

## 2.7 重要端點整理

| 端點 | URL |
|---|---|
| OIDC 探索文件 | `http://localhost:8080/realms/shopmall/.well-known/openid-configuration` |
| 簽發者（issuer） | `http://localhost:8080/realms/shopmall` |
| Token | `http://localhost:8080/realms/shopmall/protocol/openid-connect/token` |
| JWKS（公鑰） | `http://localhost:8080/realms/shopmall/protocol/openid-connect/certs` |
| Admin API 根路徑 | `http://localhost:8080/admin/realms/shopmall` |

---

下一章：[第 3 章：DDD 戰略與戰術設計](03-ddd-design.md)
