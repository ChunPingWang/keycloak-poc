# 附錄 A：常見問題與最佳實踐

## A.1 Token 驗證失敗排查

### 401 Unauthorized，日誌顯示 `Invalid issuer`

Token 的 `iss` 與 Spring 設定的 `issuer-uri` 必須**逐字元相同**。常見坑：

- 前端從 `http://keycloak:8080` 取 Token（Docker 網路名），後端設定 `http://localhost:8080` → 不一致。
- 解法：所有參與者使用同一個對外主機名；正式環境設定 Keycloak 的 `KC_HOSTNAME` 固定 issuer。

### 401，日誌顯示 `Jwt expired`

- Access Token 預設壽命短（Keycloak 預設 5 分鐘上下）。前端要用 Refresh Token 換新，不要調長 Access Token 壽命來「解決」。
- 容器時鐘漂移也會造成，檢查 `docker` 主機時間。

### 403 Forbidden 但角色明明有

- 檢查 `hasRole('customer-service')` 對應的權限是 `ROLE_customer-service`——確認 `KeycloakRealmRoleConverter` 有掛進 `JwtAuthenticationConverter`（第 5.4 節）。
- 用第 5.7 節的 `/api/whoami` 直接看解析出的 authorities。

## A.2 停權後 Token 還能用？

會的——這是 JWT 離線驗證的本質：已簽發的 Access Token 在 `exp` 之前都驗得過，即使帳號已停用。緩解手段（按成本排序）：

1. **短壽命 Access Token**（5 分鐘）+ Refresh Token：停權後最多 5 分鐘失效，因為 Refresh 時 Keycloak 會拒絕已停用帳號。多數電商場景可接受。
2. 敏感操作（改密碼、付款）前，後端額外檢查會員狀態——我們的領域模型本來就會做（`requireActive()`），這是「領域規則兜底」的好例子。
3. 極端要求即時性時，才考慮 Token Introspection（每請求回問 Keycloak），代價是失去無狀態優勢。

## A.3 Keycloak 與會員資料庫的一致性

註冊橫跨兩個系統（Keycloak + 本地 DB），沒有分散式交易。實務建議：

1. **順序**：先建 Keycloak 帳號、再存會員。反過來會出現「會員存在但無法登入」，比「孤兒帳號」更糟。
2. **補償**：存會員失敗時，catch 中呼叫 `identityProvisioning.disable(...)`（或 delete）做補償；補償也失敗就記入待對帳清單。
3. **對帳批次**：定期比對 Keycloak 使用者與會員資料表，清理孤兒帳號。
4. **更嚴謹**：導入 Transactional Outbox——會員與「待供裝身分」記錄同交易寫入，由背景工作呼叫 Keycloak 並重試。此時 `MemberEnrolled` 事件也應改走 Outbox 保證至少一次投遞。

## A.4 該把哪些資料放進 Token？

原則：**Token 只放授權判斷需要的最小集合**（角色、`sub`）。

- ❌ 會員等級、紅利點數放進 custom claims：Token 是簽發當下的快照，點數變動後 Token 裡的值就是錯的；而且加大每個請求的 header。
- ✅ 需要領域資料時，用 `sub` 查會員資料庫——單一事實來源。

## A.5 為什麼不用 `keycloak-spring-boot-starter`？

Keycloak 官方的 Spring adapter 早已棄用（Keycloak 17+ 不再提供）。Spring Security 內建的 OAuth2 Resource Server / Client 是官方建議路徑，也是本指南採用的方式。搜尋到舊教學（`KeycloakWebSecurityConfigurerAdapter` 之類）請直接跳過。

## A.6 正式環境檢查清單

- [ ] Keycloak 以 `start`（production mode）啟動，強制 HTTPS，設定 `KC_HOSTNAME`。
- [ ] 管理主控台不對公網開放（反向代理擋 `/admin`）。
- [ ] `shopmall-web` 關閉 Direct Access Grants；redirect URI 收斂為正式網域（不留 `*`）。
- [ ] `shopmall-backend` 的 client secret 由密鑰管理系統（Vault、KMS）注入，並定期輪替。
- [ ] 服務帳號權限維持最小（`manage-users`、`view-users`），定期覆核。
- [ ] Access Token 壽命 ≤ 15 分鐘；Refresh Token 啟用 rotation。
- [ ] 密碼原則（長度、常見密碼阻擋）、暴力破解偵測（Realm Settings → Security defenses）開啟。
- [ ] Keycloak 資料庫獨立備份；Realm 設定以 `kc.sh export` 納入版控。
- [ ] Spring 端 `ddl-auto: validate` + Flyway 管 schema，不讓 Hibernate 自動改表。

## A.7 延伸閱讀

- Keycloak 官方文件：<https://www.keycloak.org/documentation>
- Spring Security OAuth2 Resource Server：<https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html>
- Eric Evans,《Domain-Driven Design》；Vaughn Vernon,《Implementing Domain-Driven Design》
- Robert C. Martin,《Clean Architecture》（SOLID 與依賴規則）
- Tom Hombergs,《Get Your Hands Dirty on Clean Architecture》（六角形架構實作，本指南的套件結構即類似其風格）

---

[回到目錄](../README.md)
