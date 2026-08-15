# spring-verify:教材 Spring 程式碼的配套驗證工程

這個小專案用來**實際編譯並執行**教材中的全部 Spring 程式碼,證明它們不只是紙上範例:

- `SecurityConfig.java` — **逐字取自** [`CURRICULUM.md`](../CURRICULUM.md) Module 7.2(含 `keycloakRoleConverter`);教材省略的 `package` 與 `import` 為編譯所補,class 本體未動
- `application.yml` — 結構取自 README 7.6 與 M7.2 的 Resource Server + OAuth2 Client 兩段設定,僅把示例主機 `sso.example.com/realms/bank` 換成本機教材環境 `localhost:8080/realms/demo`
- `TestController.java` — 兩個測試端點:`/api/hello`(登入即可)與 `/api/admin/ping`(需 `account-admin` realm role)

## 執行方式

先照 README 教材第 1、3 章啟動 Keycloak 並建立 `demo` realm、`web-app` client 與使用者 `alice`,然後:

```bash
KC_CLIENT_SECRET=web-app-secret mvn spring-boot:run
```

應用跑在 <http://localhost:8081>。

## 驗證項目(2026-08-15 實測全數通過)

| 測試 | 預期 |
|------|------|
| 無 token 呼叫 `/api/hello` | 401 |
| alice 的 access token 呼叫 `/api/hello` | 200,回應可見 `ROLE_*` 權限(converter 映射生效) |
| alice(未授role)呼叫 `/api/admin/ping` | 403(`hasRole("account-admin")` 閘門) |
| 指派 `account-admin` role、重取 token 後再呼叫 | 200 |
| 竄改 token 簽章 | 401(離線驗章生效) |

取 alice 的 token(教材第 4 章同款指令):

```bash
AT=$(curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d grant_type=password -d client_id=web-app -d client_secret=web-app-secret \
  -d username=alice -d password=alice-password -d 'scope=openid profile email' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
curl -s http://localhost:8081/api/hello -H "Authorization: Bearer $AT"
```

需求:Java 21、Maven(教材以 Spring Boot 3.5.4 驗證)。
