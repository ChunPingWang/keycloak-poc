# 第 4 章：SOLID 原則的落實

本章逐一檢視五個 SOLID 原則如何體現在第 3 章的架構與第 5、6 章的程式碼中。每一節都包含：原則定義 → 本專案的落實 → 反例（如果不這樣做會怎樣）。

## 4.1 SRP：單一職責原則

> 一個模組應該只有一個變更的理由（只對一類利害關係人負責）。

### 落實

註冊流程被拆成職責單一的類別，每個類別的「變更理由」都不同：

| 類別 | 職責 | 變更理由 |
|---|---|---|
| `MemberController` | HTTP 協定轉換（DTO ↔ 使用案例參數） | API 規格改版 |
| `EnrollMemberService` | 編排註冊使用案例（先開帳號、再存會員、發事件） | 註冊流程步驟改變 |
| `Member` | 會員的狀態與不變條件 | 業務規則改變（例如新增停權原因） |
| `KeycloakIdentityProvisioningAdapter` | 呼叫 Keycloak Admin API 與模型轉換 | Keycloak 升版或換 IAM |
| `KeycloakRealmRoleConverter` | 從 JWT 取出角色轉成 Spring 權限 | Token 結構或角色策略改變 |

### 反例

把「呼叫 Keycloak 建帳號 + 寫會員資料表 + 寄歡迎信」全部寫在 Controller 裡。當行銷部門要改歡迎信邏輯、資安要求改 Keycloak 呼叫方式、前端要改 API 格式時，三類人都在改同一個檔案——衝突與回歸風險集中。

## 4.2 OCP：開放封閉原則

> 對擴充開放，對修改封閉。

### 落實 1：角色轉換器以組合擴充

第 5 章的 `KeycloakRealmRoleConverter` 實作 Spring 的 `Converter<Jwt, Collection<GrantedAuthority>>` 介面。若日後要加上 client roles（`resource_access`）或自訂 claim 的權限來源，是**新增**另一個 Converter 並在組合處合併，而不是修改既有類別：

```java
// 新需求：同時支援 realm roles 與 client roles —— 用組合，不改舊類別
Converter<Jwt, Collection<GrantedAuthority>> combined =
    jwt -> Stream.of(realmRoleConverter, clientRoleConverter)
                 .flatMap(c -> c.convert(jwt).stream())
                 .toList();
```

### 落實 2：晉升策略

會員等級晉升規則（消費滿額升 GOLD 等）以策略介面 `TierPromotionPolicy` 表達，新等級規則＝新增一個實作類別，`Member.promoteTo(...)` 不需修改。

### 反例

在 `Member` 裡寫 `if (tier == STANDARD && spent > 10000) … else if (tier == GOLD && …)` 的長鏈。每加一個等級就要改動聚合核心，並重測所有既有規則。

## 4.3 LSP：里氏替換原則

> 子型別必須能替換其基底型別而不破壞程式正確性。

### 落實

`IdentityProvisioningPort` 的所有實作都必須遵守相同的**行為契約**，而不只是簽章相同：

```java
public interface IdentityProvisioningPort {

    /**
     * 建立身分帳號。
     * 契約：
     * - 成功時回傳非 null 的 IdentityId；
     * - Email 已存在時擲出 DuplicateIdentityException（不得回傳 null 或默默成功）；
     * - 不得部分成功（帳號建立失敗時不得留下殘留狀態）。
     */
    IdentityId provision(Email email, MemberName name, RawPassword password);

    /** 停用帳號。契約：帳號不存在時擲出 IdentityNotFoundException。 */
    void disable(IdentityId identityId);
}
```

- `KeycloakIdentityProvisioningAdapter`（正式）與測試用的 `InMemoryIdentityProvisioningAdapter`（第 7 章）都遵守同一契約，因此應用層測試的結論可以外推到正式環境。
- 契約以 Javadoc 明文寫出，並用**針對介面的契約測試**（同一組測試跑在每個實作上）保證替換性。

### 反例

某實作在 Email 重複時回傳 `null` 而非擲例外。呼叫端為了它加上 `if (id == null)` 特判——這就是 LSP 違反的臭味：呼叫端被迫知道「是哪個實作」。

## 4.4 ISP：介面隔離原則

> 用戶端不應被迫依賴它不使用的方法。

### 落實 1：入向 Port 一案例一介面

```java
public interface EnrollMemberUseCase {
    MemberId enroll(EnrollMemberCommand command);
}

public interface SuspendMemberUseCase {
    void suspend(SuspendMemberCommand command);
}
```

`MemberController`（會員自助）只依賴 `EnrollMemberUseCase`、`GetMemberProfileUseCase`；後台的 `AdminMemberController` 只依賴 `SuspendMemberUseCase`。任一使用案例的簽章改動不會波及無關的 Controller。

### 落實 2：出向 Port 依「呼叫端需求」切分

`IdentityProvisioningPort` 只有 `provision` / `disable` 兩個方法——它是為「會員上下文的需求」量身定義的，而不是把 Keycloak Admin API 的幾十個能力（群組、憑證、session 管理…）全部鏡射成一個巨型介面。

### 反例

定義一個 `KeycloakService` 介面，上面有 30 個方法對應 Admin API 的所有功能。每個呼叫端都依賴了 28 個它用不到的方法；mock 時要 stub 一堆無關方法；Keycloak 任何 API 變動都震盪全專案。

## 4.5 DIP：依賴反轉原則

> 高層模組不應依賴低層模組，兩者都應依賴抽象；抽象不應依賴細節，細節應依賴抽象。

這是本專案的**骨幹原則**，前面三章的架構圖就是 DIP 的具象化：

```
        （高層）                    （抽象）                  （低層／細節）
  EnrollMemberService ──依賴──> IdentityProvisioningPort <──實作── KeycloakIdentityProvisioningAdapter
  EnrollMemberService ──依賴──> MemberRepository         <──實作── MemberRepositoryAdapter(JPA)
```

### 落實要點

1. **Port 介面定義在應用層**（`application.port.out`），不是基礎設施層。「抽象由高層擁有」是 DIP 的關鍵——介面的形狀由使用案例的需求決定，不是由 Keycloak 的 API 形狀決定。
2. Port 的方法簽章使用**領域語言**（`Email`、`IdentityId`），不出現 `UserRepresentation` 這種 Keycloak 型別。
3. Spring 的建構子注入完成「控制反轉」的接線；但注意 **DIP ≠ DI**：DI 是技術手段，DIP 是設計方向（誰擁有抽象）。

### 具體收益（不是紙上談兵）

- **可測試性**：第 7 章中，應用層測試用記憶體版 Adapter，秒級跑完，完全不需啟動 Keycloak。
- **可替換性**：把 Keycloak 換成 Auth0 時，變更範圍 = `infrastructure.identity` 一個 package。
- **可理解性**：讀 `EnrollMemberService` 就能看懂註冊流程的業務意圖，不會被 REST 呼叫細節淹沒。

### 反例

`EnrollMemberService` 直接 `import org.keycloak.admin.client.Keycloak` 並呼叫 `realm().users().create(...)`。後果：應用層測試必須啟動 Keycloak（或 mock 一長串流式 API）；Keycloak 升版改 API 時，業務程式碼跟著改；「註冊」的業務語意被 HTTP 狀態碼判斷淹沒。

## 4.6 小結：SOLID 與 DDD 的關係

SOLID 與 DDD 不是兩套互斥的方法論，而是互相成就：

- **DDD 的分層與防腐層**，本質上就是大尺度的 **SRP + DIP**；
- **值物件的不可變與自我驗證**，讓 LSP 的行為契約更容易成立；
- **以使用案例為單位的 Port**，就是 ISP 在應用邊界上的應用；
- **策略化的領域規則**（晉升政策），是 OCP 在戰術設計中的體現。

下一章開始把這些設計化為可執行的程式碼。

---

下一章：[第 5 章：Spring Boot 與 Keycloak 整合](05-spring-keycloak-integration.md)
