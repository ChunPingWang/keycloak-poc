# Keycloak 初學者完整學習教材(實作驗證版)

> 這是一份**從零開始、每個指令都實際驗證過**的 Keycloak 動手教材。
> 所有指令與輸出均於 **Keycloak 26.2.5**(Docker, 2026-08-13)實測通過,共 40+ 項驗證全數成功(詳見[附錄 A:驗證報告](#附錄-a驗證報告))。
>
## 本儲存庫包含三個部分

| 內容 | 定位 | 什麼時候讀 |
|------|------|-----------|
| **README.md(本文)** | 初學者動手教材 — 用 `curl` 純手工走完每個流程,建立第一手體感與正確心智模型 | **從這裡開始** |
| **[`keycloak-poc.md`](./keycloak-poc.md)** | 完整進階課綱(13 個 Module,從協定原理到生產部署) | 動手做完本教材後,依其學習路徑深入 |
| **[`spring-boot-lab/`](./spring-boot-lab/)** | **可建置執行的整合實作** — Spring Boot 3 + Keycloak 26 的電商會員管理(DDD × 六角形架構 × SOLID,含測試) | 想看「真實專案怎麼寫」時;對應進階課綱的 Module 7 |

```
概念與協定原理 ──▶ 純手工實作(curl)──▶ 真實專案程式碼
keycloak-poc.md      README.md            spring-boot-lab/
```

---

---

## 目錄

- [第 0 章:Keycloak 是什麼?為什麼需要它?](#第-0-章keycloak-是什麼為什麼需要它)
- [第 1 章:環境準備與啟動](#第-1-章環境準備與啟動)
- [第 2 章:核心概念地圖](#第-2-章核心概念地圖)
- [第 3 章:建立你的第一個 Realm、Client 與 User](#第-3-章建立你的第一個-realmclient-與-user)
- [第 4 章:取得第一個 Token 並解剖 JWT](#第-4-章取得第一個-token-並解剖-jwt)
- [第 5 章:Authorization Code Flow + PKCE 完整實作](#第-5-章authorization-code-flow--pkce-完整實作)
- [第 6 章:動手驗證安全機制](#第-6-章動手驗證安全機制)
- [第 7 章:其他必會端點](#第-7-章其他必會端點)
- [第 8 章:角色、群組與授權(RBAC 動手做)](#第-8-章角色群組與授權rbac-動手做)
- [第 9 章:登出與 Session 終止](#第-9-章登出與-session-終止)
- [第 10 章:Token 生命週期與 Session 模型](#第-10-章token-生命週期與-session-模型)
- [第 11 章:密碼儲存與簽章金鑰](#第-11-章密碼儲存與簽章金鑰)
- [第 12 章:組態匯出與環境管理入門](#第-12-章組態匯出與環境管理入門)
- [第 13 章:清理環境與下一步學習路徑](#第-13-章清理環境與下一步學習路徑)
- [附錄 A:驗證報告](#附錄-a驗證報告)
- [附錄 B:疑難排解 FAQ](#附錄-b疑難排解-faq)
- [附錄 C:術語速查表](#附錄-c術語速查表)
- [附錄 D:用 Docker Compose + PostgreSQL 建立可保存的環境](#附錄-d用-docker-compose--postgresql-建立可保存的環境)
- [附錄 E:啟用雙因子認證(TOTP)](#附錄-e啟用雙因子認證totp)

---

## 第 0 章:Keycloak 是什麼?為什麼需要它?

### 0.1 問題:每套系統都自己管帳號密碼

想像公司有三套系統,各自維護自己的使用者資料表:

```mermaid
flowchart LR
    A["App A<br/>自建帳密"]
    B["App B<br/>自建帳密"]
    C["App C<br/>自建帳密"]
    A ~~~ B ~~~ C
```

問題馬上浮現:

- 使用者要記三組密碼,或到處用同一組(更危險)
- 員工離職,要到三個地方停權,漏一個就是資安漏洞
- 密碼策略、雙因子認證(MFA)、稽核紀錄,三套系統各做各的

### 0.2 解法:集中式身分與存取管理(IAM)

**Keycloak** 是一套開源的 IAM(Identity and Access Management)伺服器,把「登入」這件事從所有應用程式抽出來,集中處理:

```mermaid
flowchart TB
    subgraph apps["應用程式不再碰密碼"]
        A[App A]
        B[App B]
        C[App C]
    end
    KC["Keycloak<br/>唯一的登入入口與信任來源"]
    A -->|登入委託| KC
    B -->|登入委託| KC
    C -->|登入委託| KC
```

用 **C4 Model 的系統情境圖(Level 1)** 看 Keycloak 在整體架構中的位置 — 它對內是所有應用的登入委託對象,對外可以接既有目錄與第三方身分來源:

```mermaid
C4Context
    title 系統情境圖(C4 Level 1):Keycloak 在整體架構中的位置

    Person(user, "使用者", "員工或客戶")
    System_Boundary(org, "你的組織") {
        System(appA, "Web 應用", "傳統有後端的應用")
        System(appB, "SPA / Mobile", "純前端或行動應用")
        System(api, "後端 API", "Resource Server")
        System(keycloak, "Keycloak", "集中式 IAM:認證、授權、SSO")
    }
    System_Ext(ldap, "LDAP / AD", "既有企業目錄")
    System_Ext(extidp, "外部 IdP", "Google、Azure AD 等")

    Rel(user, appA, "使用")
    Rel(user, appB, "使用")
    Rel(appA, keycloak, "登入委託", "OIDC")
    Rel(appB, keycloak, "登入委託", "OIDC + PKCE")
    Rel(appA, api, "呼叫", "Bearer token")
    Rel(api, keycloak, "取公鑰離線驗 token", "JWKS")
    Rel(keycloak, ldap, "User Federation")
    Rel(keycloak, extidp, "Identity Brokering")
```

它提供:

| 能力 | 說明 |
|------|------|
| **認證(AuthN)** | 「你是誰?」— 帳密、OTP、Passkey、社群登入 |
| **授權(AuthZ)** | 「你能做什麼?」— 角色、群組、細緻政策 |
| **單一登入(SSO)** | 登入一次,所有接入的系統都通行 |
| **身分聯邦** | 接既有的 LDAP / Active Directory |
| **身分代理** | 委託 Google、Azure AD 等第三方登入 |

### 0.3 兩個必須先分清楚的協定

Keycloak 實作了兩個業界標準,初學最容易混淆:

- **OAuth 2.0**:是「**授權**」框架 — 解決「App 如何在不拿到你密碼的情況下,取得存取資源的受限權限」
- **OpenID Connect(OIDC)**:建立在 OAuth 2.0 之上的「**認證**」層 — 補上「登入的人是誰」

> 一句話記憶:**OAuth 給你一張門禁卡(Access Token),OIDC 給你一張身分證(ID Token)。**
> 深入原理見 `keycloak-poc.md` 的 Module 2 與 Module 3。

---

## 第 1 章:環境準備與啟動

### 1.1 前置需求

- Docker(本教材以 Docker 29.x 驗證)
- `curl`、`openssl`、`python3`(解析 JSON 與解碼 JWT 用)
- `jq`(第 8、9 章使用;沒有的話可用 `python3 -c` 替代,但 `jq` 會讓指令短很多)

### 1.2 啟動 Keycloak(開發模式)

```bash
docker run -d --name keycloak \
  -p 8080:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:26.2 start-dev
```

✅ **已驗證**:啟動約 7 秒完成,日誌顯示 `Keycloak 26.2.5 on JVM (powered by Quarkus 3.20.1) started`。

> **版本注意**:Keycloak 26 起,管理員帳號的環境變數是 `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD`(舊版教學常見的 `KEYCLOAK_ADMIN` 已淘汰)。這組帳號是「臨時開機帳號」,生產環境應在首次登入後建立正式管理員並移除它。

確認啟動成功(啟動需要約 10~15 秒,太早打端點會得到 `Expecting value: line 1 column 1` 這種 JSON 解析錯誤 — 那只是伺服器還沒就緒,不是你做錯了):

```bash
# 等待就緒(每 2 秒重試,最多 60 秒)
until curl -sf http://localhost:8080/realms/master/.well-known/openid-configuration >/dev/null; do
  echo "等待 Keycloak 啟動中..."; sleep 2
done

# 打 OIDC Discovery 端點確認
curl -s http://localhost:8080/realms/master/.well-known/openid-configuration | python3 -m json.tool | head -20
```

### 1.3 登入管理主控台

瀏覽器開啟 <http://localhost:8080>,以 `admin` / `admin` 登入 **Admin Console**。

### 1.4 `start-dev` 與 `start`(生產模式)的差異

| | `start-dev`(本教材) | `start`(生產) |
|---|---|---|
| 資料庫 | 內嵌 H2(檔案型) | 必須外接 DB(建議 PostgreSQL) |
| HTTPS | 關閉 | 強制(或明確設定反向代理) |
| Hostname 檢查 | 寬鬆 | 嚴格(必設 `KC_HOSTNAME`) |
| 用途 | 學習、開發 | 正式環境 |

> ⚠️ **`start-dev` 絕對不可用於生產環境**。生產部署(外接 DB、HA 叢集、Kubernetes)見 `keycloak-poc.md` Module 6 與 Module 11。

---

## 第 2 章:核心概念地圖

### 2.1 先把名詞放到正確的層次

初學者最大的困擾不是某個名詞難懂,而是**一堆名詞混在一起分不清誰是誰**:IdP、SSO、OAuth 2.0、OIDC、JWT、RBAC、Keycloak…… 它們其實**不是互相競爭的技術**,而是分屬五個不同層次、一起組成整套架構:

```mermaid
flowchart TB
    subgraph L1["① 身分層:誰負責確認你是誰"]
        IDP["<b>IdP</b> 身分提供者(一種角色,不是特定產品)<br/>Keycloak / Entra ID / Okta / Google"]
    end
    subgraph L2["② 協定層:用什麼標準溝通"]
        OIDC["<b>OIDC</b> = OAuth 2.0 + 身分層<br/>回答「登入的人是誰」"]
        OAUTH["<b>OAuth 2.0</b> 授權框架<br/>回答「這個應用能存取什麼」"]
        SAML["<b>SAML 2.0</b><br/>傳統企業 SSO 協定"]
    end
    subgraph L3["③ 憑證層:拿到什麼、長什麼樣"]
        JWT["<b>JWT</b> 只是 Token 的一種<b>格式</b><br/>Header.Payload.Signature"]
    end
    subgraph L4["④ 權限層:能不能做這件事"]
        RBAC["<b>RBAC</b> 用 Role 決定<br/>「你是什麼角色」"]
        SCOPE["<b>Scope</b> 決定<br/>「這張 token 被授權做什麼」"]
    end
    subgraph L5["⑤ 產品層"]
        KC["<b>Keycloak</b> = IAM 產品<br/>把上面全部實作出來"]
    end

    IDP -->|實作| OIDC
    OIDC -->|建立在| OAUTH
    IDP -.->|也支援| SAML
    OAUTH -->|簽發 Access Token,常以| JWT
    OIDC -->|加發 ID Token,格式必為| JWT
    JWT -->|裡面帶著 roles / scope| RBAC
    JWT --> SCOPE
    KC -.->|扮演| IDP
```

用一句話串起來:

> **Keycloak 作為 IdP 與授權伺服器,透過 OIDC 完成認證與 SSO、透過 OAuth 2.0 簽發 Access Token;Token 採 JWT 格式,API 驗完 JWT 後再用 Role / Scope 做授權判斷。**

一張表對照「每個名詞解決什麼問題」:

| 名詞 | 它是什麼 | 解決什麼問題 |
|------|---------|-------------|
| Authentication(認證) | 過程 | **你是誰?** |
| Authorization(授權) | 過程 | **你能做什麼?** |
| IdP | 一種**角色** | 誰負責確認身分 |
| OAuth 2.0 | 協定/框架 | 應用如何取得存取資源的權限 |
| OIDC | 協定(蓋在 OAuth 上) | 如何知道登入者是誰 |
| JWT | **資料格式** | Token 如何攜帶 claims 並可離線驗證 |
| RBAC | 權限模型 | 依角色決定能做什麼 |
| SSO | 一種**架構能力/體驗** | 登入一次,多系統通行 |
| Keycloak | **產品** | 把以上能力整包實作出來 |

**三個最常見的名詞誤解,先講清楚:**

- ❌ **「SSO 是一種協定」** → SSO 不是協定,是一種**能力**;它可以用 OIDC 實現,也可以用 SAML 實現。
- ❌ **「JWT = OAuth = OIDC」** → JWT 只是**格式**。OAuth 的 Access Token 不一定是 JWT(也可以是不透明字串);而 OIDC 的 ID Token 則規定必須是 JWT。
- ❌ **「Keycloak 就是 OAuth」** → Keycloak 是**產品**,它「實作」了 OAuth 2.0、OIDC、SAML 等標準,兩者是「產品」與「規格」的關係。

### 2.2 Keycloak 內部的結構地圖

上面是名詞之間的關係,這裡是 Keycloak **內部**的組成 — 之後每個操作你都知道自己在動哪一層:

```
Keycloak 伺服器
└── Realm(領域)= 完全隔離的租戶邊界
    ├── Clients(客戶端)= 每一個接入 SSO 的「應用程式」
    │   ├── Protocol Mappers(決定 token 裡放哪些欄位)
    │   └── Roles(client 層級角色)
    ├── Users(使用者)
    │   ├── Credentials(密碼、OTP、Passkey…)
    │   ├── Role Mappings(角色指派)
    │   └── Groups(群組,可繼承角色)
    ├── Roles(realm 層級角色)
    ├── Authentication Flows(登入流程編排)
    └── Keys(簽發 token 用的金鑰組)
```

三個新手最常犯的觀念錯誤,先打預防針:

1. **`master` realm 只用來管理 Keycloak 本身** — 永遠不要把業務應用掛在 `master`,一律另建 realm。
2. **Realm 不是按「應用程式」切分的** — 一個 realm 裝多個 client(應用)。切分 realm 的依據是「使用者群體與政策是否獨立」,例如 `員工` 與 `客戶` 分兩個 realm。
3. **Client ≠ 使用者端** — 在 OAuth 術語中,Client 指「代表使用者存取資源的應用程式」,例如你的網站後端或 SPA,不是指瀏覽器或人。

---

## 第 3 章:建立你的第一個 Realm、Client 與 User

本章用 **Admin Console(UI)** 和 **Admin REST API(curl)** 各做一次 — UI 適合探索理解,API 適合自動化(生產環境的正規做法是組態即程式碼)。

### 3.1 方式一:Admin Console 操作

1. **建立 Realm**:左上角 realm 下拉選單 → **Create realm** → Realm name 輸入 `demo` → **Create**
2. **建立 Client**(機密客戶端,模擬有後端的 Web App):
   - 左側 **Clients** → **Create client**
   - Client ID:`web-app`,Client type:`OpenID Connect` → Next
   - **Client authentication: On**(這就是「機密客戶端」,會有 client secret)→ Next
   - Valid redirect URIs:`http://localhost:3000/callback` → Save
   - 到 **Credentials** 頁籤可看到 Client Secret
3. **建立 User**:
   - 左側 **Users** → **Create new user** → Username:`alice` → Create
   - **Credentials** 頁籤 → **Set password** → 密碼 `alice-password`,**Temporary 關閉** → Save

### 3.2 方式二:Admin REST API(本教材實測採用)

先取得管理員的 access token:

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -d grant_type=password -d client_id=admin-cli \
  -d username=admin -d password=admin \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
```

建立 realm、兩個 client(機密 + 公開)、一個使用者:

```bash
# 建立 realm「demo」
curl -s -X POST http://localhost:8080/admin/realms \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"realm": "demo", "enabled": true}'

# 機密客戶端 web-app(有 secret,模擬有後端的傳統 Web App)
curl -s -X POST http://localhost:8080/admin/realms/demo/clients \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "clientId": "web-app", "enabled": true, "protocol": "openid-connect",
    "publicClient": false, "secret": "web-app-secret",
    "standardFlowEnabled": true, "directAccessGrantsEnabled": true,
    "redirectUris": ["http://localhost:3000/callback"]
  }'

# 公開客戶端 spa-app(無 secret,模擬 SPA;強制 PKCE)
curl -s -X POST http://localhost:8080/admin/realms/demo/clients \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "clientId": "spa-app", "enabled": true, "protocol": "openid-connect",
    "publicClient": true, "standardFlowEnabled": true,
    "redirectUris": ["http://localhost:3000/callback"],
    "attributes": {"pkce.code.challenge.method": "S256"}
  }'

# 使用者 alice
curl -s -X POST http://localhost:8080/admin/realms/demo/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "username": "alice", "enabled": true, "emailVerified": true,
    "email": "alice@example.com", "firstName": "Alice", "lastName": "Wang",
    "credentials": [{"type": "password", "value": "alice-password", "temporary": false}]
  }'
```

✅ **已驗證**:以上指令在 26.2.5 全部成功(回傳 201)。

> **為什麼建兩種 client?**
> - `web-app`(機密客戶端):程式碼跑在伺服器上,能安全保存 `client_secret`
> - `spa-app`(公開客戶端):程式碼跑在瀏覽器裡,人人可讀,**無法**保密 secret → 必須用 PKCE(第 5 章)

---

## 第 4 章:取得第一個 Token 並解剖 JWT

### 4.1 先用最簡單的方式拿到 token

用 Password Grant(直接對 token 端點送帳密)先拿到一組 token 來觀察:

```bash
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=web-app -d client_secret=web-app-secret \
  -d username=alice -d password=alice-password \
  -d 'scope=openid profile email' \
  | python3 -m json.tool
```

> ⚠️ **Password Grant(ROPC)已被 OAuth 2.1 廢棄,僅供本章教學觀察用** — 它讓應用程式直接經手使用者密碼,違背 OAuth 的設計初衷。真實應用一律使用第 5 章的 Authorization Code + PKCE。

回應包含三種 token(✅ 實測輸出):

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJIUzUxMiIs...",
  "id_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "session_state": "…", "scope": "openid email profile"
}
```

### 4.2 三種 Token 各司其職(必背)

| | ID Token | Access Token | Refresh Token |
|---|---|---|---|
| 給誰用 | **Client**(你的應用) | **Resource Server**(API) | 只還給 Keycloak 的 token 端點 |
| 回答什麼 | 「登入的人是誰」 | 「能存取什麼」 | 「幫我換一組新的」 |
| 實測壽命 | 跟隨 session | **300 秒(5 分鐘)** | **1800 秒(30 分鐘,滑動)** |
| 常見錯誤 | ❌ 拿去呼叫 API | ✅ 放在 `Authorization: Bearer` 標頭 | ❌ 傳給任何其他服務 |

### 4.3 解剖 JWT:三段式結構

JWT 長這樣:`Header.Payload.Signature`,前兩段只是 **Base64URL 編碼(不是加密!)**,任何人都能解開來讀。

先把 token 抓進變數(不必手動複製貼上 — 重打一次 4.1 的請求,順手把 access_token 與 refresh_token 存起來,後面章節都會用到):

```bash
RESP=$(curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=web-app -d client_secret=web-app-secret \
  -d username=alice -d password=alice-password \
  -d 'scope=openid profile email')
AT=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
RT=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["refresh_token"])')
```

然後解碼 header 與 payload:

```bash
echo $AT | cut -d. -f1 | python3 -c 'import sys,base64,json; s=sys.stdin.read().strip(); print(json.dumps(json.loads(base64.urlsafe_b64decode(s+"=="))), sep="\n")'
echo $AT | cut -d. -f2 | python3 -c 'import sys,base64,json; s=sys.stdin.read().strip(); print(json.dumps(json.loads(base64.urlsafe_b64decode(s+"==")), indent=2))'
```

**Header(✅ 實測輸出):**

```json
{ "alg": "RS256", "typ": "JWT", "kid": "A5_GdxMGJgYaEwJh1rFqQGn7iWcMKjBZXmb4vczPpow" }
```

- `alg`:簽章演算法(RS256 = RSA + SHA-256)
- `kid`:Key ID — 告訴驗證方去 JWKS 端點拿「哪一把」公鑰來驗章

**Payload 重點欄位(✅ 實測值):**

| Claim | 實測值 | 意義 |
|-------|--------|------|
| `iss` | `http://localhost:8080/realms/demo` | 簽發者 = realm URL,驗證方必須核對 |
| `sub` | `ffb6bcf4-…`(UUID) | 使用者的唯一識別 |
| `exp` − `iat` | 300 秒 | 有效期 5 分鐘 |
| `typ` | `Bearer` | Token 類型 |
| `azp` | `web-app` | 哪個 client 請求的 |
| `realm_access.roles` | `["offline_access", "uma_authorization", "default-roles-demo"]` | realm 層級角色 |
| `preferred_username` | `alice` | 使用者名稱 |
| `scope` | `openid email profile` | 授權範圍 |

而 **ID Token** 的 `aud`(audience)實測為 `web-app` —— 它的觀眾是 client 自己,**這就是 ID Token 不能拿去呼叫 API 的原因**:API 驗 `aud` 時本來就該拒絕它。

### 4.4 為什麼 API 驗 token 不用連 Keycloak?(核心觀念)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client(你的應用)
    participant KC as Keycloak(簽發方)
    participant API as 你的 API(驗證方)

    C->>KC: 登入流程(第 5 章)
    KC->>KC: 用【私鑰】對 payload 簽章
    KC-->>C: access_token(JWT)
    C->>API: GET /resource(Authorization Bearer JWT)
    API->>KC: 首次:GET /certs 取【公鑰】
    KC-->>API: JWKS(依 kid 對應,可長期快取)
    API->>API: 離線驗章 + 核對 iss / aud / exp
    API-->>C: 200 資源內容
    Note over API: 之後每個請求都離線驗證,不再回呼 Keycloak
```

- 沒有私鑰,任何人都**偽造不了**簽章 → API 只要用公鑰驗章即可信任 token 內容
- 這是**離線行為** → API 可水平擴展、Keycloak 不會成為每個請求的瓶頸
- **代價**:簽出去的 token 過期前**無法撤回** → 所以 Access Token 才設計成只有 5 分鐘壽命

公鑰在這裡(✅ 已驗證,`kid` 與 token header 對得上):

```bash
curl -s http://localhost:8080/realms/demo/protocol/openid-connect/certs | python3 -m json.tool
```

---

## 第 5 章:Authorization Code Flow + PKCE 完整實作

這是**真實應用該用的登入流程**。我們不用任何 SDK,純手工走一遍,徹底理解每個參數。

### 5.1 流程總覽

```mermaid
sequenceDiagram
    autonumber
    actor U as 使用者(瀏覽器)
    participant C as 你的應用(spa-app)
    participant KC as Keycloak

    U->>C: 點「登入」
    C->>C: 產生 code_verifier、code_challenge、state
    C-->>U: 302 導向 Keycloak /auth<br/>(client_id、redirect_uri、state、code_challenge)
    U->>KC: GET /auth
    KC-->>U: 顯示登入頁
    U->>KC: 輸入帳密(密碼只給 Keycloak,應用永遠看不到!)
    KC-->>U: 302 導回 redirect_uri?code=xxx&state=xxx<br/>並發 SSO session cookie
    U->>C: 帶 code 回應用
    C->>C: 比對 state 是否為自己發出的值
    C->>KC: POST /token(code + code_verifier)
    KC->>KC: 驗證 SHA256(code_verifier) == 當初的 code_challenge
    KC-->>C: access_token + refresh_token + id_token
```

**為什麼繞這麼一大圈?**

| 設計 | 理由 |
|------|------|
| 不直接回傳 token,先給一次性的 `code` | token 若走瀏覽器 URL 會留在歷史紀錄、Referer、代理 log 裡 |
| `state` 隨機值來回比對 | 防 CSRF:確認這個 callback 是自己發起的 |
| `redirect_uri` 必須預先註冊且精確比對 | 攻擊者無法把 code 導到自己的網址 |
| PKCE(`code_challenge` / `code_verifier`) | 公開客戶端沒有 secret,PKCE 充當「動態的一次性 secret」 |

### 5.2 完整實作腳本(✅ 已驗證)

**逐段貼到目前的終端機執行**(或存成 `authcode-lab.sh` 後用 `source authcode-lab.sh` 執行)。
注意:**不要用 `bash authcode-lab.sh` 跑** — 那會在子 shell 執行,`$CODE`、`$VERIFIER`、cookie jar 等變數會隨腳本結束消失,第 6 章的實驗就接不上了:

```bash
BASE=http://localhost:8080
REALM=demo
CLIENT=spa-app
REDIRECT=http://localhost:3000/callback
JAR=$(mktemp)   # cookie jar,模擬瀏覽器

# ── 步驟 1:產生 PKCE 參數 ──────────────────────────────
# code_verifier:高熵隨機字串(43~128 字元)
VERIFIER=$(openssl rand -base64 60 | tr -d '=+/\n' | cut -c1-64)
# code_challenge = BASE64URL( SHA256(code_verifier) )
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary \
            | openssl base64 -A | tr '+/' '-_' | tr -d '=')
STATE=$(openssl rand -hex 12)

# ── 步驟 2:GET 授權端點,拿到登入頁(保留 cookie)──────────
AUTH_URL="$BASE/realms/$REALM/protocol/openid-connect/auth?response_type=code&client_id=$CLIENT&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fcallback&scope=openid%20profile&state=$STATE&code_challenge=$CHALLENGE&code_challenge_method=S256"
LOGIN_PAGE=$(curl -s -c "$JAR" "$AUTH_URL")
# 登入表單的提交網址藏在 HTML 的 action 屬性裡
ACTION=$(printf '%s' "$LOGIN_PAGE" | grep -oP 'action="\K[^"]+' | head -1 | sed 's/&amp;/\&/g')

# ── 步驟 3:提交帳密,Keycloak 302 導回 redirect_uri 帶 code ──
LOC=$(curl -s -b "$JAR" -c "$JAR" -o /dev/null -w '%{redirect_url}' \
  -d 'username=alice' -d 'password=alice-password' "$ACTION")
CODE=$(printf '%s' "$LOC" | grep -oP 'code=\K[^&]+' | head -1)
echo "拿到 authorization code:${CODE:0:20}..."

# ── 步驟 4:用 code + code_verifier 兌換 token(公開客戶端,無 secret!)──
TOKENS=$(curl -s -X POST "$BASE/realms/$REALM/protocol/openid-connect/token" \
  -d grant_type=authorization_code -d client_id=$CLIENT \
  -d code="$CODE" -d redirect_uri="$REDIRECT" \
  -d code_verifier="$VERIFIER")
echo "$TOKENS" | python3 -m json.tool
# 存下 access token,第 6 章的實驗會用到
AT=$(echo "$TOKENS" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
```

✅ **實測結果**:成功取得 access token —— 全程沒有用到任何 client secret,這就是 PKCE 讓公開客戶端安全走完授權流程的價值。

---

## 第 6 章:動手驗證安全機制

教材說的安全設計,別背,**動手打打看**。以下四個實驗全部 ✅ 實測通過。

### 實驗 1:Authorization Code 是一次性的

拿第 5 章用過的 code 再兌換一次:

```bash
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d grant_type=authorization_code -d client_id=spa-app \
  -d code="$CODE" -d redirect_uri=http://localhost:3000/callback \
  -d code_verifier="$VERIFIER"
```

**實測結果**:HTTP 400 `invalid_grant` —— code 只能用一次。

**更精彩的是**:Keycloak 偵測到 code 重放後,會把**第一次兌換成功發出的 token 一併撤銷**(RFC 6749 的建議行為)。用 introspection 檢查剛才拿到的 access token:

```bash
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token/introspect \
  -u web-app:web-app-secret -d token="$AT"
# 實測:{"active": false} ← 已被撤銷
```

> 這個設計的威力:即使攻擊者偷到 code 搶先兌換,只要合法 client 也去兌換,雙方的 token 全部作廢,攻擊者拿到的東西立刻失效。

### 實驗 2:錯誤的 code_verifier 會被拒絕

第 5 章的 code 已在實驗 1 用掉了,先拿一個新的 code(沿用 cookie jar,你會發現**不需要重新登入** — 這正是實驗 4 要講的 SSO):

```bash
VERIFIER2=$(openssl rand -base64 60 | tr -d '=+/\n' | cut -c1-64)
CHALLENGE2=$(printf '%s' "$VERIFIER2" | openssl dgst -sha256 -binary \
             | openssl base64 -A | tr '+/' '-_' | tr -d '=')
AUTH_URL2="$BASE/realms/$REALM/protocol/openid-connect/auth?response_type=code&client_id=$CLIENT&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fcallback&scope=openid&state=demo2&code_challenge=$CHALLENGE2&code_challenge_method=S256"
LOC2=$(curl -s -b "$JAR" -o /dev/null -w '%{redirect_url}' "$AUTH_URL2")
CODE2=$(printf '%s' "$LOC2" | grep -oP 'code=\K[^&]+' | head -1)
```

然後用正確的 code、**錯誤的 verifier** 兌換:

```bash
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d grant_type=authorization_code -d client_id=spa-app \
  -d code="$CODE2" -d redirect_uri=http://localhost:3000/callback \
  -d code_verifier="wrong-verifier-wrong-verifier-wrong-verifier-1234567"
```

**實測結果**:

```json
{"error": "invalid_grant", "error_description": "PKCE verification failed: Code mismatch"}
```

Keycloak 把收到的 `code_verifier` 重算 SHA-256,與當初的 `code_challenge` 比對,不符就拒發。**攻擊者就算攔截到 code 與 code_challenge,因 SHA-256 的單向性也推不回 verifier。**

### 實驗 3:state 原樣返回(CSRF 防護)

觀察第 5 章步驟 3 的 redirect URL:`?state=<你送出的隨機值>&code=…` —— ✅ 實測 state 原封不動返回。真實應用要比對它與自己發出的值,不符即中止流程。

### 實驗 4:SSO 的本質 —— 一顆發給 Keycloak 網域的 Cookie

其實你在實驗 2 已經親眼看過了:拿新 code 時,只因為帶著第 5 章的 cookie jar(裡面有 `AUTH_SESSION_ID`、`KEYCLOAK_IDENTITY` 等 cookie),Keycloak **沒有再顯示登入頁**,直接 302 發新的 code 回來。把 redirect URL 印出來看:

```bash
echo "$LOC2"
# http://localhost:3000/callback?state=demo2&session_state=…&code=…  ← 免登入直接發 code
```

這就是 SSO 的全部秘密:
- 使用者的登入狀態存在「**對 Keycloak 網域**」的 session cookie 裡(不是對各應用)
- App B 把使用者導到 Keycloak → Keycloak 看到有效 cookie → 免登入直接發 code
- 對使用者來說就是「登入一次,到處通行」

```mermaid
sequenceDiagram
    autonumber
    actor U as 使用者(瀏覽器)
    participant A as App A
    participant B as App B
    participant KC as Keycloak

    U->>A: 存取 App A
    A-->>U: 302 導向 Keycloak /auth
    U->>KC: GET /auth(無 cookie)
    KC-->>U: 登入頁 → 輸入帳密
    KC-->>U: 發 SSO session cookie<br/>+ 302 code 給 App A
    Note over U,KC: 稍後,同一個瀏覽器…
    U->>B: 存取 App B
    B-->>U: 302 導向 Keycloak /auth
    U->>KC: GET /auth(帶有效 session cookie)
    KC->>KC: cookie 對應到有效 SSO session
    KC-->>U: 免登入,直接 302 code 給 App B
```

---

## 第 7 章:其他必會端點

### 7.1 Discovery:一個 URL 自動發現所有端點

```bash
curl -s http://localhost:8080/realms/demo/.well-known/openid-configuration | python3 -m json.tool
```

✅ 實測回傳所有端點位置與能力宣告,包括(擷取):

| 欄位 | 值 |
|------|-----|
| `issuer` | `http://localhost:8080/realms/demo` |
| `token_endpoint` | `…/protocol/openid-connect/token` |
| `jwks_uri` | `…/protocol/openid-connect/certs` |
| `code_challenge_methods_supported` | 含 `S256`(PKCE) |
| `grant_types_supported` | 含 `authorization_code`、`client_credentials`、`refresh_token`、`device_code`、`token-exchange`、`ciba` 等 |

各語言的 OIDC 函式庫(如 Spring Security)只需設定 issuer URL,其餘全部自動發現 — 這就是第 7.6 節 Spring 設定只有一行的原因。

### 7.2 Refresh Token:換發新的 Access Token

```bash
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d grant_type=refresh_token -d client_id=web-app -d client_secret=web-app-secret \
  -d refresh_token="$RT" | python3 -m json.tool
```

✅ 實測成功取得一組**新的** access token。Access Token 5 分鐘就過期,應用靠這個機制在背景無感續期。

### 7.3 Token Introspection:線上查驗 token(RFC 7662)

```bash
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token/introspect \
  -u web-app:web-app-secret -d token="$AT" | python3 -m json.tool
# 實測:{"active": true, "username": "alice", ...}
```

與「離線驗 JWT」互為權衡:introspection **能即時反映撤銷**,但每次都要一趟網路呼叫。

### 7.4 UserInfo:用 Access Token 換使用者屬性

```bash
curl -s http://localhost:8080/realms/demo/protocol/openid-connect/userinfo \
  -H "Authorization: Bearer $AT" | python3 -m json.tool
```

✅ 實測回傳:

```json
{
  "sub": "ffb6bcf4-…", "email_verified": true, "name": "Alice Wang",
  "preferred_username": "alice", "given_name": "Alice",
  "family_name": "Wang", "email": "alice@example.com"
}
```

### 7.5 Client Credentials:服務對服務(M2M),沒有使用者

批次程式、微服務之間的呼叫,沒有「人」參與,用 client 自己的憑證換 token:

```bash
# 建立一個啟用 service account 的 client
curl -s -X POST http://localhost:8080/admin/realms/demo/clients \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"clientId": "batch-service", "enabled": true, "protocol": "openid-connect",
       "publicClient": false, "secret": "batch-secret",
       "standardFlowEnabled": false, "serviceAccountsEnabled": true}'

# 用 client 憑證直接換 token(沒有登入頁、沒有使用者)
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=batch-service -d client_secret=batch-secret | python3 -m json.tool
```

✅ 實測:token 的 `preferred_username` 為 `service-account-batch-service` —— Keycloak 為每個啟用 service account 的 client 建立一個影子使用者。

### 7.6 一行設定接上 Spring Boot(延伸)

你的 API(Resource Server)只需要:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/demo
```

Spring 會自動抓 discovery → 取得 JWKS → 快取公鑰 → 離線驗每個請求的 JWT。完整整合(含角色映射、BFF、token exchange)見 `keycloak-poc.md` Module 7。

### 7.7 Grant Type 選型速查

```
你的客戶端是?
├─ 有使用者互動
│   ├─ 有後端的 Web App ──▶ Authorization Code(+ PKCE)
│   ├─ SPA / Mobile     ──▶ Authorization Code + PKCE(公開客戶端)
│   └─ 無瀏覽器裝置(TV) ──▶ Device Authorization Grant
└─ 服務對服務(M2M)     ──▶ Client Credentials

❌ 永遠不要再用:Implicit Flow、Password Grant(ROPC)
```

---

## 第 8 章:角色、群組與授權(RBAC 動手做)

> 📌 **本章與第 9 章、附錄 D、附錄 E 為後續補充內容**,指令依 Keycloak 26.2 的 Admin REST API 撰寫,但**未納入附錄 A 那批 2026-08-13 的自動化實測**。若執行結果與描述不符,請以你的版本為準並回報。

到目前為止,你的 token 只回答了「你是誰」。這一章補上另一半:**「你能做什麼」**。

### 8.1 Keycloak 的三層權限模型

```
Realm Role(realm 層級)      跨應用共通的身分,如 customer、employee
    ↑ 可組合(Composite)
Client Role(client 層級)    某個應用內的權限,如 account-api 的 account-viewer
    ↑ 透過
Group(群組)                 把「人」分群,群組帶角色 → 使用者繼承
```

**設計準則(先講結論,做完你會有體感):**

- **角色代表權限,群組代表組織** — 用群組管「誰」,用角色管「能做什麼」,兩者不要混用
- 應用程式內**不要硬編角色名稱四處判斷**;把細粒度權限收斂成少數幾個角色
- 一個 realm role 可以是多個 client role 的組合(Composite Role)— 這是「職務」的建模方式

### 8.2 建立角色並指派

先準備好本章要用的變數(承接第 3 章的 `$ADMIN_TOKEN`;過期就重取):

```bash
BASE=http://localhost:8080
H="Authorization: Bearer $ADMIN_TOKEN"

# 取得 web-app 這個 client 的內部 UUID(注意:不是 clientId,Admin API 用 UUID)
CID=$(curl -s -H "$H" "$BASE/admin/realms/demo/clients?clientId=web-app" | jq -r '.[0].id')
# 取得 alice 的 UUID
UID_ALICE=$(curl -s -H "$H" "$BASE/admin/realms/demo/users?username=alice" | jq -r '.[0].id')
```

建立一個 realm role 與一個 client role:

```bash
# realm role:customer(跨應用的身分)
curl -s -X POST "$BASE/admin/realms/demo/roles" -H "$H" -H "Content-Type: application/json" \
  -d '{"name": "customer", "description": "一般客戶"}'

# client role:web-app 底下的 account-viewer(應用內的權限)
curl -s -X POST "$BASE/admin/realms/demo/clients/$CID/roles" -H "$H" -H "Content-Type: application/json" \
  -d '{"name": "account-viewer", "description": "可檢視帳戶"}'
```

指派給 alice(**注意:role-mappings 端點要的是完整的 role 物件陣列,不是名稱字串**,這是初學者最常卡住的地方):

```bash
# 指派 realm role
REALM_ROLE=$(curl -s -H "$H" "$BASE/admin/realms/demo/roles/customer")
curl -s -X POST "$BASE/admin/realms/demo/users/$UID_ALICE/role-mappings/realm" \
  -H "$H" -H "Content-Type: application/json" -d "[$REALM_ROLE]"

# 指派 client role
CLIENT_ROLE=$(curl -s -H "$H" "$BASE/admin/realms/demo/clients/$CID/roles/account-viewer")
curl -s -X POST "$BASE/admin/realms/demo/users/$UID_ALICE/role-mappings/clients/$CID" \
  -H "$H" -H "Content-Type: application/json" -d "[$CLIENT_ROLE]"
```

**重新取一次 token,親眼看見角色出現在裡面:**

```bash
AT2=$(curl -s -X POST "$BASE/realms/demo/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=web-app -d client_secret=web-app-secret \
  -d username=alice -d password=alice-password -d 'scope=openid profile email' | jq -r .access_token)

echo "$AT2" | cut -d. -f2 | python3 -c 'import sys,base64,json;s=sys.stdin.read().strip();print(json.dumps(json.loads(base64.urlsafe_b64decode(s+"==")),indent=2,ensure_ascii=False))' \
  | jq '{realm_access, resource_access}'
```

預期會看到:

```json
{
  "realm_access": { "roles": ["customer", "offline_access", "uma_authorization", "default-roles-demo"] },
  "resource_access": { "web-app": { "roles": ["account-viewer"] } }
}
```

**這裡有兩個關鍵觀念:**

1. **realm role 放在 `realm_access.roles`,client role 放在 `resource_access.<clientId>.roles`** — Spring Security 預設不認得這個結構,所以需要自訂 Converter(見 `keycloak-poc.md` §7.2)
2. 角色是**簽發當下的快照**。你現在改了角色,alice 手上那張舊 token 不會變 — 要等它過期(5 分鐘)或重新登入

### 8.3 用群組管理「人」

```bash
# 建立群組
curl -s -X POST "$BASE/admin/realms/demo/groups" -H "$H" -H "Content-Type: application/json" \
  -d '{"name": "branch-taipei"}'
GID=$(curl -s -H "$H" "$BASE/admin/realms/demo/groups?search=branch-taipei" | jq -r '.[0].id')

# 群組帶角色
curl -s -X POST "$BASE/admin/realms/demo/groups/$GID/role-mappings/realm" \
  -H "$H" -H "Content-Type: application/json" -d "[$REALM_ROLE]"

# 把 alice 加進群組(注意是 PUT,不是 POST)
curl -s -X PUT "$BASE/admin/realms/demo/users/$UID_ALICE/groups/$GID" -H "$H"
```

之後新進的分行同仁只要加進群組就自動有權限 — 這就是「用群組管人」的價值:**人員異動不用動角色設定**。

> 群組成員資格預設**不會**出現在 token 裡。需要的話得加一個 Group Membership mapper(下一節就是在做這件事)。

### 8.4 Protocol Mapper:把自訂資料放進 Token

實務需求:token 裡要帶「分行代碼」,讓 API 直接依此做資料隔離。

**⚠️ 先過 26.x 的一道關卡** — Keycloak 24 起啟用了 Declarative User Profile,**沒有事先宣告的自訂屬性預設會被丟棄**。這是「明明 PUT 成功,屬性卻不見了」的元凶:

```bash
# 開啟未受管屬性(學習環境的快解;生產環境建議正式宣告屬性)
curl -s -H "$H" "$BASE/admin/realms/demo/users/profile" \
  | jq '. + {unmanagedAttributePolicy: "ENABLED"}' \
  | curl -s -X PUT "$BASE/admin/realms/demo/users/profile" -H "$H" -H "Content-Type: application/json" -d @-
```

然後給 alice 一個屬性,並建立 mapper 把它送進 token:

```bash
# 寫入使用者屬性
curl -s -X PUT "$BASE/admin/realms/demo/users/$UID_ALICE" -H "$H" -H "Content-Type: application/json" \
  -d '{"attributes": {"branch_code": ["001"]}}'

# 建立 protocol mapper:user attribute → token claim
curl -s -X POST "$BASE/admin/realms/demo/clients/$CID/protocol-mappers/models" \
  -H "$H" -H "Content-Type: application/json" -d '{
    "name": "branch-code",
    "protocol": "openid-connect",
    "protocolMapper": "oidc-usermodel-attribute-mapper",
    "config": {
      "user.attribute": "branch_code",
      "claim.name": "branch_code",
      "jsonType.label": "String",
      "access.token.claim": "true",
      "id.token.claim": "true",
      "userinfo.token.claim": "true"
    }
  }'
```

再取一次 token,payload 裡就會多出 `"branch_code": "001"`。

> **架構意涵:** Token 是 IdP 與應用之間的 **API 契約**。加一個 claim 看似小事,但下游可能有十個系統依賴它 — Mapper 的變更應該跟 API 變更一樣納入版控與變更管理。

### 8.5 Audience Mapper:讓 token 能被指定的 API 接受

第 4 章說過「API 必須驗 `aud`」。那麼一張給 `web-app` 的 token,要怎麼讓 `account-api` 願意接受?

```bash
curl -s -X POST "$BASE/admin/realms/demo/clients/$CID/protocol-mappers/models" \
  -H "$H" -H "Content-Type: application/json" -d '{
    "name": "account-api-audience",
    "protocol": "openid-connect",
    "protocolMapper": "oidc-audience-mapper",
    "config": {
      "included.client.audience": "account-api",
      "access.token.claim": "true"
    }
  }'
```

新取的 access token,`aud` 會包含 `account-api`。**這才是微服務間傳遞 token 的正規做法** — 而不是讓每個 API 都放寬 `aud` 檢查(那等於把安全門拆掉)。

### 8.6 Role 與 Scope 的分工(很多人混在一起用)

你在第 4 章看過 `scope`,這一章又做了 role。兩者都在 token 裡、都跟權限有關,差別是什麼?

```json
{
  "sub": "ffb6bcf4-…",
  "scope": "openid profile email order.read order.write",
  "realm_access": { "roles": ["customer", "order-admin"] }
}
```

| | Role | Scope |
|---|------|-------|
| 回答的問題 | **這個「人」是什麼角色** | **這張 token 被授權做什麼** |
| 綁在誰身上 | 使用者(或 service account) | 這次授權請求 / 這個 client |
| 誰決定 | 管理者指派 | 授權請求時要求 + client 允許的範圍 |
| 典型值 | `customer`、`order-admin` | `order.read`、`order.write` |

**為什麼需要兩者?** 想像同一位 `order-admin` 使用者:

- 從公司後台登入 → token 拿到 `order.read order.write`(完整權限)
- 授權某個第三方報表工具存取 → 只給 `order.read`(即使他本人是 admin,**這張 token 也只能讀**)

也就是說:**Role 描述「人的權限上限」,Scope 描述「這次授權實際開放多少」**。真實的判斷是兩者的交集:

```
最終能做的事 = 使用者的 Role 允許的 ∩ 這張 token 的 Scope 允許的
```

實務建議(避免權限模型爆炸):

- API 的判斷條件寫成「**需要 scope `order.write` 且 role 為 `order-admin`**」,而不是把所有情況都塞成一個新角色
- 角色數量要收斂;每多一個角色就多一份治理成本,幾十個角色之後沒有人說得清誰能做什麼
- 別讓應用程式到處硬編 `if role == "ADMIN"` — 這是角色爆炸的起點

> Keycloak 中的 scope 由 **Client Scopes** 管理(可設為 Default 或 Optional),細節見 `keycloak-poc.md` §5.5;更細緻的「這一筆資料只有本人能看」屬於 ABAC 範疇,見 Module 9。

### 8.7 本章重點回顧

| 你做了什麼 | 對應的真實用途 |
|-----------|--------------|
| realm role / client role | 跨應用身分 vs 應用內權限的分層 |
| 指派角色 → 觀察 token 變化 | 理解授權資訊如何隨 token 傳遞 |
| 群組帶角色 | 用組織架構管理權限,人員異動零成本 |
| Protocol Mapper | 把企業自有資料(分行、風險等級)帶進 token |
| Audience Mapper | 讓 token 能被正確的 Resource Server 接受 |

進階的集中式授權(細緻到「這筆帳戶只有本人和客服主管能看」)屬於 Keycloak Authorization Services,見 `keycloak-poc.md` Module 9。

---

## 第 9 章:登出與 Session 終止

登入只做一次就會了,**登出卻是導入案最常出包的地方**。這章把三種登出方式做過一遍,並釐清一個關鍵誤解。

### 9.1 三種登出,語意完全不同

| 方式 | 做法 | 銷毀範圍 |
|------|------|---------|
| **RP-Initiated Logout** | 瀏覽器導向 `/protocol/openid-connect/logout` | 整個 SSO session(所有應用一起登出) |
| **後端撤銷** | 後端 POST logout 端點,帶 `refresh_token` | 該 session |
| **管理者強制登出** | Admin API `/users/{id}/logout` | 該使用者的全部 session |

### 9.2 動手做:後端撤銷(最容易驗證的一種)

先拿一組 token(承接第 4 章的做法),然後撤銷它:

```bash
RESP=$(curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d grant_type=password -d client_id=web-app -d client_secret=web-app-secret \
  -d username=alice -d password=alice-password -d 'scope=openid profile email')
AT3=$(echo "$RESP" | jq -r .access_token)
RT3=$(echo "$RESP" | jq -r .refresh_token)

# 登出(銷毀 session)
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  http://localhost:8080/realms/demo/protocol/openid-connect/logout \
  -d client_id=web-app -d client_secret=web-app-secret -d refresh_token="$RT3"
# 預期:204
```

驗證 refresh token 真的失效了:

```bash
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d grant_type=refresh_token -d client_id=web-app -d client_secret=web-app-secret \
  -d refresh_token="$RT3"
# 預期:{"error":"invalid_grant","error_description":"Session not active"} 之類
```

### 9.3 ⚠️ 最重要的一個觀念:登出銷毀的是 session,不是 access token

繼續用剛才**已經登出**的那張 access token:

```bash
# introspection:會說失效(因為它會回頭查 session)
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token/introspect \
  -u web-app:web-app-secret -d token="$AT3" | jq .active
# 預期:false
```

但是 —— **一個只做離線驗章的 API(第 4.4 節那種,也就是絕大多數 Spring Boot Resource Server)看不到這件事**。它只驗簽章、`iss`、`aud`、`exp`,這些全都還成立,所以**它會繼續接受這張 token 直到過期(最多 5 分鐘)**。

```
登出時刻 ──────────── 最多 5 分鐘 ──────────▶ token 自然過期
   │                                              │
   ├─ session 立刻銷毀,refresh 立刻失效             │
   └─ 但離線驗章的 API 仍會放行這張 access token ────┘
```

這不是 bug,是第 4.4 節「離線驗證」那個設計的**必然代價**。實務上的處理:

| 需求 | 做法 |
|------|------|
| 一般業務 API | 接受這幾分鐘的空窗(把 access token 壽命設短) |
| 高風險操作(轉帳、改個資) | 該筆請求改用 **introspection** 線上查驗 |
| 使用者已被停權 | Admin 強制登出 + 停用帳號,並確保關鍵 API 走線上查驗 |

### 9.4 RP-Initiated Logout(使用者按「登出」按鈕)

真實的網頁登出是把瀏覽器導到:

```
http://localhost:8080/realms/demo/protocol/openid-connect/logout
  ?id_token_hint=<ID Token>
  &post_logout_redirect_uri=http%3A%2F%2Flocalhost%3A3000%2F
```

- `id_token_hint`:告訴 Keycloak 是誰要登出(沒帶的話 Keycloak 會顯示確認頁)
- `post_logout_redirect_uri`:**必須事先在 client 註冊**,否則 26.x 會拒絕:

```bash
curl -s -X PUT "$BASE/admin/realms/demo/clients/$CID" -H "$H" -H "Content-Type: application/json" \
  -d '{"attributes": {"post.logout.redirect.uris": "http://localhost:3000/*"}}'
```

> 小技巧:把值設為 `+` 表示「沿用已註冊的 redirect URIs」。

### 9.5 其他應用怎麼知道使用者登出了?(Back-Channel Logout)

Keycloak 銷毀 session 後,會主動通知每個 client:

```mermaid
sequenceDiagram
    autonumber
    actor U as 使用者
    participant KC as Keycloak
    participant A as App A(後端)
    participant B as App B(後端)

    U->>KC: 點「登出」
    KC->>KC: 銷毀 SSO session
    par 伺服器對伺服器通知
        KC->>A: POST backchannel_logout_uri(logout_token)
        A->>A: 驗章後殺掉自己的本地 session
        KC->>B: POST backchannel_logout_uri(logout_token)
        B->>B: 同上
    end
    KC-->>U: 導回 post_logout_redirect_uri
```

- `logout_token` 是一張 JWT,內含 `sid`(session id)與登出事件標記;應用要**驗簽後**再依 `sid` 終止本地 session
- 設定位置:Client → Settings → **Backchannel logout URL**
- 另有 Front-Channel Logout(用 iframe),但現代瀏覽器封鎖第三方 cookie 後常失效 —— **新專案一律優先用 Back-Channel**

### 9.6 管理者強制登出(集中停權的實作基礎)

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  "$BASE/admin/realms/demo/users/$UID_ALICE/logout" -H "$H"
# 預期:204 — alice 的所有 session 立即銷毀
```

這就是第 0 章說的「員工離職,一個地方停權」實際長什麼樣子。

---

## 第 10 章:Token 生命週期與 Session 模型

### 10.1 實測的預設值(Keycloak 26.2.5)

| 設定 | 實測預設值 | 設計理由 |
|------|-----------|---------|
| Access Token Lifespan | **300 秒(5 分鐘)** | JWT 簽出去就收不回,短命縮小暴露窗 |
| Authorization Code | **60 秒** | 只是兌換憑證,用完即丟 |
| SSO Session Idle | **1800 秒(30 分鐘)** | 閒置逾時(refresh token 壽命跟著它) |
| SSO Session Max | **36000 秒(10 小時)** | 無論多活躍,最長登入時間 |

位置:Admin Console → 選 `demo` realm → **Realm settings** → **Sessions / Tokens** 頁籤。

### 10.2 Session 與 Token 的關係

```mermaid
flowchart LR
    subgraph browser["瀏覽器"]
        cookie["Cookie:<br/>AUTH_SESSION_ID<br/>KEYCLOAK_IDENTITY"]
    end
    subgraph kc["Keycloak"]
        sso["SSO Session<br/>(登入狀態本體)"]
        cs1["Client Session: web-app"]
        cs2["Client Session: spa-app"]
        cs3["…每個應用一個"]
        sso --> cs1
        sso --> cs2
        sso --> cs3
    end
    cookie <-->|對應| sso
```

- **登入狀態的本體是 Keycloak 的 session**,token 只是它的「短期產物」
- Refresh token 綁著 session:session 逾時/被登出 → refresh 立刻失效
- 26.x 起 session **預設持久化到資料庫**(`persistent-user-sessions` 功能),伺服器重啟使用者不再被登出

### 10.3 觀察線上 session

Admin Console → **Users** → alice → **Sessions** 頁籤,可看到她目前的 SSO session 與登入的 client,並可強制登出(session 銷毀後,她的 refresh token 立即失效 — 這就是「集中停權」的實作基礎)。

---

## 第 11 章:密碼儲存與簽章金鑰

### 11.1 密碼是怎麼存的?(✅ 實測)

用 Admin API 查 alice 的密碼憑證中繼資料:

```bash
UID_ALICE=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8080/admin/realms/demo/users?username=alice" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8080/admin/realms/demo/users/$UID_ALICE/credentials" | python3 -m json.tool
```

> 變數名刻意用 `UID_ALICE` 而不是 `UID`:**`UID` 在 bash / zsh 中是唯讀的內建變數**,直接指派會得到 `UID: readonly variable` 而中斷。

實測的 `credentialData`:

```json
{
  "hashIterations": 5,
  "algorithm": "argon2",
  "additionalParameters": {
    "hashLength": ["32"], "memory": ["7168"],
    "type": ["id"], "version": ["1.3"], "parallelism": ["1"]
  }
}
```

解讀:

- 密碼以 **Argon2id** 雜湊儲存(**Keycloak 24 起的預設**;更早是 PBKDF2-SHA512)— 永遠不存明文
- `memory: 7168` KB:Argon2 刻意吃記憶體,讓 GPU 大規模暴力破解變得昂貴
- 每個使用者的 salt 都不同 → 彩虹表無效
- 伺服器同時內建 `argon2`、`pbkdf2-sha512` 等 provider(✅ 實測),可用 Password Policy 切換與調參 — 這是「安全 vs 登入吞吐量」的權衡點(調高成本,登入 TPS 直接下降)

### 11.2 簽章金鑰與輪替

```bash
curl -s http://localhost:8080/realms/demo/protocol/openid-connect/certs | python3 -m json.tool
```

✅ 實測:JWKS 回傳兩把金鑰 — `RS256`(簽章用)與 `RSA-OAEP`(加密用),各有唯一 `kid`。

**金鑰輪替的原理**(為什麼換金鑰不會讓服務中斷):

1. 新增一把新金鑰設為 Active → 之後的新 token 用新鑰簽(新 `kid`)
2. 舊金鑰保留為 Passive → 尚未過期的舊 token 仍能靠舊 `kid` 找到公鑰驗章
3. 等舊 token 全部過期(最長 5 分鐘)→ 安全移除舊金鑰

驗證方永遠靠 token header 的 `kid` 選對公鑰 — 這就是 `kid` 存在的意義。

---

## 第 12 章:組態匯出與環境管理入門

### 12.1 用 Admin API 匯出 realm 組態(✅ 實測可用)

```bash
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8080/admin/realms/demo/partial-export?exportClients=true&exportGroupsAndRoles=true" \
  > demo-realm-export.json
```

✅ 實測成功匯出 realm 與全部 client 定義(注意:partial-export **不含使用者與 secret**)。

### 12.2 ⚠️ 陷阱實錄:`kc.sh export` 在 dev 模式會失敗

教科書上的完整匯出指令:

```bash
docker exec keycloak /opt/keycloak/bin/kc.sh export --dir /tmp/export --realm demo
```

**實測結果:失敗** — `ERROR: Database may be already in use: "/opt/keycloak/data/h2/keycloakdb.mv.db"`。

原因:`start-dev` 用的內嵌 H2 是單連線檔案型資料庫,被運行中的伺服器鎖住。解法:

1. 先 `docker stop keycloak`,再用同一個資料卷跑一次性的 export 容器;或
2. 使用外接 PostgreSQL(生產模式本來就必須),export 即可與服務並行;或
3. 用 10.1 的 partial-export API(不停機,但不含使用者)

### 12.3 組態即程式碼(原則)

生產環境的鐵律:**Admin Console 只用來探索,正式變更一律走版控** — 工具有 `keycloak-config-cli`(宣告式 JSON)與 Terraform Keycloak Provider。詳見 `keycloak-poc.md` Module 6。

---

## 第 13 章:清理環境與下一步學習路徑

### 13.1 清理

```bash
docker rm -f keycloak
```

### 13.2 你已經學會了什麼

完成本教材後,你已親手驗證:

- [x] 啟動 Keycloak、建立 realm / client / user(UI 與 API 雙路徑)
- [x] 三種 token 的分工與 JWT 三段式結構、每個核心 claim 的意義
- [x] Authorization Code + PKCE 完整流程(不靠 SDK)
- [x] 四個安全機制:code 一次性(+重放觸發撤銷)、PKCE 驗證、state、SSO cookie
- [x] Discovery、JWKS、introspection、userinfo、refresh、client credentials
- [x] 角色/群組/Protocol Mapper/Audience Mapper — 授權資訊如何進到 token
- [x] 三種登出方式,以及「登出不等於 access token 立刻失效」的關鍵代價
- [x] Token 生命週期預設值與 session 模型
- [x] Argon2 密碼儲存與金鑰輪替原理

### 13.3 下一步:接上進階課綱

依 [`keycloak-poc.md`](./keycloak-poc.md) 的學習路徑繼續:

| 你的下一步 | 對應 Module | 內容 |
|-----------|-------------|------|
| 補齊協定理論 | M0–M4 | 密碼學基礎、OAuth/OIDC 規格細節、SAML |
| 理解產品內部 | M5–M6 | 架構剖析、Authentication Flow 引擎、生產組態 |
| 實戰整合 | M7 | Spring Boot / SPA / API Gateway 完整 Lab |
| 企業功能 | M8–M10 | LDAP 聯邦、身分代理、授權服務、SPI 擴充開發 |
| 生產部署 | M11–M13 | Kubernetes HA 叢集、安全維運、金融業實戰場景 |

**想直接看真實專案怎麼寫?** 本儲存庫的 [`spring-boot-lab/`](./spring-boot-lab/) 是一份可建置執行的完整實作:Spring Boot 3 + Keycloak 26 的電商會員管理,用 DDD 與六角形架構把「認證」與「會員領域」的邊界切開,含 21 個測試(領域、授權、ArchUnit 架構規則)。你在本教材手工做過的每個概念,在那裡都能看到它在真實程式碼中的樣子。

---

## 附錄 A:驗證報告

**驗證環境**:Keycloak 26.2.5(`quay.io/keycloak/keycloak:26.2`)、Docker 29.6、WSL2,驗證日期 2026-08-13。方式:實際啟動伺服器,以腳本執行 40+ 項自動化檢查。

> **驗證範圍說明(誠實揭露):** 下列報告涵蓋**第 0~7 章與第 10~13 章**。後續補充的**第 8 章(RBAC)、第 9 章(登出)、附錄 D、附錄 E** 依 Keycloak 26.2 官方 Admin REST API 與 OIDC 規格撰寫,**尚未納入這批自動化實測**;執行時若與描述有出入,請以你的版本行為為準。

### A.1 驗證通過的重要宣稱(節錄)

| `keycloak-poc.md` 宣稱 | 實測結果 |
|------------------------|---------|
| `KC_BOOTSTRAP_ADMIN_*` 啟動指令(M6) | ✅ 26.2.5 啟動成功 |
| JWKS 端點 `/realms/{realm}/protocol/openid-connect/certs`(M1) | ✅ 存在,回傳含 `kid` 的公鑰 |
| Access Token 預設 5 分鐘(M1/M12) | ✅ `accessTokenLifespan=300` |
| Authorization code 短命「預設 60 秒」(M2) | ✅ `accessCodeLifespan=60` |
| Refresh token 預設 30 分滑動(M3) | ✅ `refresh_expires_in=1800`,綁 SSO idle |
| code 一次性;重放會撤銷已發 token(M2) | ✅ 重放回 400,且原 token introspect `active=false` |
| PKCE S256 驗證(M2) | ✅ 錯誤 verifier 回 `PKCE verification failed` |
| SSO = Keycloak 網域的 session cookie(M3) | ✅ 第二次授權請求免登入直接發 code |
| ID Token `aud` = client_id(M3) | ✅ 實測 `aud=web-app` |
| Token 端點支援 device_code / token-exchange / uma-ticket / CIBA(M2/M3/M9) | ✅ discovery `grant_types_supported` 列出 |
| PAR 端點存在(M3) | ✅ `…/ext/par/request` |
| 密碼以 Argon2(id)儲存(M1) | ✅ `algorithm=argon2, type=id, memory=7168` |
| FAPI client profiles 內建含 `fapi-2-security-profile`(M3/M13) | ✅ 另含 fapi-1、oauth-2-1 等 8 組 |
| `persistent-user-sessions` 26.x 預設啟用(M5/M11) | ✅ feature 為 `DEFAULT, enabled`(25 為 preview,26.0 起預設)|
| Service account = client credentials 的化身(M5) | ✅ username 為 `service-account-…` |

### A.2 發現的偏差與修正(讀 `keycloak-poc.md` 時請注意)

| 位置 | 原文 | 實測/查證修正 |
|------|------|--------------|
| M1 §1.1 | 「預設使用 Argon2(**26.x 起**)」 | Argon2 自 **Keycloak 24** 起即為非 FIPS 環境預設([KC 24 release notes](https://www.keycloak.org/docs/25.0.6/release_notes/index.html)) |
| M5 §5.6 | 「`sessions` 快取 Distributed(**預設 2 owners**)」 | 26.2 預設組態實測為 `sessions`/`clientSessions` **`owners=1`** 且 `max-count=10000`(因 session 已預設落 DB,Infinispan 退為快取);`owners=2` 僅 `authenticationSessions`,或關閉 persistent sessions 的舊制部署 |
| M3 §3.6 / M12 | DPoP 列為可用規格 | DPoP 在 26.2 為 **PREVIEW feature,預設停用**,需 `--features=dpop` 啟用 |
| M6 §6.3 | `kc.sh export` 作為匯出手段 | 對**運行中的 dev 模式(H2)**執行會因資料庫檔案鎖而失敗;需先停機、改用外部 DB,或改用 partial-export API |

### A.3 其他查證

- RFC 編號(6749/7636/7519/7662/8693/8628/9126/9449/8705/9700)與書目作者均正確
- SSO Session Max 預設為 10 小時(36000s),與 M12 建議值「8~10 小時」一致

## 附錄 B:疑難排解 FAQ

**Q:`curl` 打 token 端點回 `401 invalid_client`?**
A:機密客戶端(`publicClient: false`)必須帶 `client_secret`;公開客戶端則**不能**帶。確認 client 型別與參數一致。

**Q:授權請求回「Invalid parameter: redirect_uri」?**
A:redirect_uri 必須與 client 註冊值**精確比對**(這是防 code 竊取的安全設計,不是 bug)。檢查結尾斜線、埠號、http/https。

**Q:token 拿去驗證說 issuer 不符?**
A:`iss` 是簽發當下的 URL。你用 `localhost:8080` 拿的 token,`iss` 就是它;API 若設定 issuer 為 `127.0.0.1` 或容器內部主機名就會不符。生產環境務必設 `KC_HOSTNAME` 固定 issuer。

**Q:重啟容器後之前的設定不見了?**
A:本教材的 `docker run` 沒掛資料卷,`docker rm` 後 H2 資料就沒了。要保留:`-v keycloak-data:/opt/keycloak/data`。

**Q:管理員 token 一下就過期?**
A:admin token 也是 access token,預設 60 秒(master realm 的 admin-cli)~5 分鐘。重跑第 3.2 節第一條指令重取即可。

**Q:為什麼我照做 Password Grant 卻被拒?**
A:client 需開啟 `directAccessGrantsEnabled`(Admin Console 中叫「Direct access grants」)。再次強調:此 grant 僅供教學觀察。

**Q:我用 Admin API 幫使用者加了自訂屬性,回傳成功但查不到?**
A:Keycloak 24 起的 Declarative User Profile 預設丟棄未宣告的屬性。依第 8.4 節開啟 Unmanaged Attributes,或正式在 User Profile 宣告該屬性。

**Q:指派角色回 400 或沒反應?**
A:`role-mappings` 端點收的是**完整 role 物件的陣列**(要有 `id` 與 `name`),不是名稱字串;而且路徑上的 client 要用**內部 UUID**,不是 `clientId`。見第 8.2 節取 UUID 的寫法。

**Q:改了使用者的角色,為什麼 token 裡沒變?**
A:token 是簽發當下的快照。等它過期(預設 5 分鐘)重新換發,或重新登入。

**Q:已經登出了,為什麼 API 還讓我進去?**
A:登出銷毀的是 session,不是已簽發的 access token。離線驗章的 API 會接受它直到過期 — 完整說明見第 9.3 節。

**Q:登出時回「Invalid redirect uri」?**
A:`post_logout_redirect_uri` 必須事先在 client 註冊(`post.logout.redirect.uris`),見第 9.4 節。

**Q:token 突然變得很大,或呼叫 API 出現奇怪的 400/431?**
A:角色、群組、屬性塞太多會讓 JWT 膨脹,撐爆反向代理的 header 上限。關閉 client 的 Full scope allowed、精簡 claim,詳見 `keycloak-poc.md` §5.5。

## 附錄 C:術語速查表

| 術語 | 白話解釋 |
|------|---------|
| Realm | 一個完全隔離的「租戶」:自己的使用者、應用、政策 |
| Client | 在 Keycloak 註冊的「應用程式」(不是指瀏覽器) |
| 機密/公開客戶端 | 能不能安全保存 secret:後端能(機密)、瀏覽器/App 不能(公開) |
| Access Token | 門禁卡:呼叫 API 用,短命(5 分鐘) |
| ID Token | 身分證:告訴應用「登入的是誰」,不可拿去呼叫 API |
| Refresh Token | 換卡憑證:只還給 Keycloak 換新的 access token |
| JWT | 三段式(Header.Payload.Signature)可離線驗證的 token 格式 |
| Claim | JWT payload 裡的一筆欄位(如 `sub`、`email`) |
| JWKS | Keycloak 公開「驗章公鑰」的標準端點 |
| `kid` | Key ID:token 標頭指名「用哪把公鑰驗我」 |
| PKCE | 公開客戶端的動態一次性 secret(SHA-256 挑戰/應答) |
| Grant Type | 取得 token 的方式(authorization_code、client_credentials…) |
| Scope | 授權範圍(`openid profile email`…) |
| SSO Session | 使用者在 Keycloak 的登入狀態本體(cookie 對應) |
| Introspection | 線上向 Keycloak 查驗 token 是否有效(可反映撤銷) |
| Federation | 接既有使用者來源(LDAP/AD) |
| Brokering | 把認證委託給第三方 IdP(Google、Azure AD) |
| SPI | Keycloak 的擴充點:自訂認證步驟、事件監聽等 |
| Realm Role / Client Role | 跨應用的身分 vs 單一應用內的權限 |
| Protocol Mapper | 決定 token 裡放哪些欄位的「生產線」 |
| Audience(`aud`) | 這張 token 是「發給誰用」的;API 必須驗它 |
| Back-Channel Logout | Keycloak 直接呼叫各應用後端通知登出(不經瀏覽器) |
| Required Action | 登入後強制使用者完成的動作(改密碼、設定 OTP) |
| TOTP | 以時間為基礎的一次性密碼(Google Authenticator 那類) |

---

## 附錄 D:用 Docker Compose + PostgreSQL 建立可保存的環境

第 1 章的 `docker run` 用內嵌 H2,容器一刪設定就沒了,而且第 12.2 節的 `kc.sh export` 也會因檔案鎖失敗。想長期保留學習成果,改用這份 compose:

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: keycloak
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U keycloak"]
      interval: 5s
      retries: 10

  keycloak:
    image: quay.io/keycloak/keycloak:26.2
    command: start                      # 生產模式(非 start-dev)
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: keycloak
      KC_HOSTNAME: http://localhost:8080   # 固定 issuer,避免 token 驗證出錯
      KC_HOSTNAME_STRICT: "false"
      KC_HTTP_ENABLED: "true"              # 學習環境才這樣;生產一定要 TLS
      KC_HEALTH_ENABLED: "true"
    ports:
      - "8080:8080"
      - "9000:9000"                        # 26.x 的 health/metrics 在獨立的管理埠
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  pgdata:
```

```bash
docker compose up -d
curl -sf http://localhost:9000/health/ready && echo " ready"
```

**幾個值得注意的細節:**

- `start`(非 `start-dev`)會在啟動時做一次 build,首次啟動比較慢是正常的
- **`/health/ready` 在 26.x 走 9000 管理埠**,不在 8080 — 這是常見的健康檢查設定錯誤來源
- `KC_HOSTNAME` 固定後,所有 token 的 `iss` 就穩定了(對照附錄 B 的 issuer 問題)
- 有了外接 DB,第 12.2 節那個 `kc.sh export` 的檔案鎖問題自然消失
- 清理:`docker compose down`(保留資料)、`docker compose down -v`(連資料一起刪)

---

## 附錄 E:啟用雙因子認證(TOTP)

最小可行的 MFA 體驗,兩步驟(承接第 8 章設定好的 `$BASE`、`$H`、`$UID_ALICE`):

**1. 要求 alice 下次登入時設定 OTP**

```bash
curl -s -X PUT "$BASE/admin/realms/demo/users/$UID_ALICE" -H "$H" -H "Content-Type: application/json" \
  -d '{"requiredActions": ["CONFIGURE_TOTP"]}'
```

**2. 用瀏覽器走一次登入**(第 5 章的授權 URL,或直接開 Account Console `http://localhost:8080/realms/demo/account`)

Keycloak 會在密碼驗證後跳出 QR Code,用任何 TOTP App(Google Authenticator、1Password…)掃描並輸入驗證碼完成綁定。之後每次登入都會多問一次 6 位數字。

**延伸觀念(這才是重點):**

- OTP 的參數(演算法、位數、時間窗)在 **Realm settings → Authentication → OTP Policy**
- 「**所有人都要 MFA**」與「**只有特定族群才要**」的差別,在於 Authentication Flow 裡用的是 `Required` 還是 **Conditional 子流程** — 這是 Keycloak 認證流程引擎的核心能力
- 更強的因子是 **WebAuthn / Passkey**(抗釣魚),Keycloak 原生支援;高風險交易則用 **Step-up Authentication**(`acr_values`)只在需要時追加驗證
- 別忘了設計**救援路徑**(遺失手機怎麼辦)—— MFA 專案最常被攻破的是客服流程,不是密碼學

完整機制見 `keycloak-poc.md` §12.2。

---

*教材驗證與撰寫:2026-08-13,基於 Keycloak 26.2.5。第 8、9 章與附錄 D、E 為後續補充(見附錄 A 的驗證範圍說明)。進階內容請接續 [`keycloak-poc.md`](./keycloak-poc.md)。*
