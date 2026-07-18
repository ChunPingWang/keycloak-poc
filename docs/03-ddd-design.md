# 第 3 章：DDD 戰略與戰術設計

本章是整份指南的設計核心：先用戰略設計界定「會員」與「帳號」的邊界，再用戰術設計建出會員模型，最後決定分層架構。

## 3.1 戰略設計（Strategic Design）

### 3.1.1 子領域劃分

以 ShopMall 電商系統整體來看：

| 子領域 | 類型 | 處理方式 |
|---|---|---|
| 訂單、定價、促銷 | **核心領域（Core Domain）** | 自行精心建模（不在本指南範圍） |
| **會員管理** | **支撐子領域（Supporting Subdomain）** | 自行建模，但保持精簡 —— 本指南主角 |
| **身分認證** | **通用子領域（Generic Subdomain）** | 採購現成方案 → **Keycloak** |

這個劃分直接回答了「為什麼不把密碼存進會員資料表」：認證不是我們的領域問題，是所有系統共通的通用問題。

### 3.1.2 限界上下文與通用語言

**會員上下文（Membership Context）** 的通用語言（Ubiquitous Language）：

| 術語 | 定義 |
|---|---|
| 會員（Member） | 已完成註冊、可在 ShopMall 消費的個人 |
| 身分識別（Identity） | 會員在 IAM 系統中的帳號對應（以 `IdentityId` 表示） |
| 會員狀態（MemberStatus） | `ACTIVE`（正常）、`SUSPENDED`（停權） |
| 會員等級（MembershipTier） | `STANDARD`、`GOLD`、`PLATINUM`，依消費行為晉升 |
| 註冊（Enroll） | 訪客成為會員的過程 |
| 停權（Suspend） | 客服對違規會員停止其使用權 |

注意：通用語言裡**沒有** "Keycloak"、"JWT"、"Token" 這些詞——它們是基礎設施細節，不屬於領域。

### 3.1.3 上下文映射（Context Mapping）

會員上下文與 Keycloak 的關係是 **防腐層（Anticorruption Layer, ACL）+ 開放主機服務（OHS）**：

```
┌─────────────────────────────┐          ┌──────────────────┐
│  Membership Context          │          │   Keycloak        │
│  （我們的模型）               │          │  （外部通用模型）  │
│                              │          │                   │
│  Member ──uses──> Identity   │   ACL    │  UserRepresentation│
│  Provisioning Port ─────────────────────>  Admin REST API   │
│              （防腐層轉換）    │          │                   │
└─────────────────────────────┘          └──────────────────┘
```

- Keycloak 提供的是**它的**模型（`UserRepresentation`、`RoleRepresentation`）；
- 我們在基礎設施層寫一個 **Adapter** 做模型轉換，領域與應用層只看到自己定義的 **Port 介面**；
- 若未來換成 Auth0，只需要重寫 Adapter。

### 3.1.4 資料切分：誰擁有什麼？

| 資料 | 擁有者 | 理由 |
|---|---|---|
| 帳號、密碼（雜湊）、MFA 設定、登入紀錄 | Keycloak | 認證關注點 |
| Email、顯示名稱 | **兩邊都有**，以會員上下文為主，註冊時同步寫入 Keycloak | Email 是登入帳號也是領域屬性 |
| 會員等級、紅利點數、會員狀態、註冊時間 | 會員上下文 | 純領域概念，Keycloak 不該知道 |
| 對應鍵 | 會員聚合持有 `IdentityId`（= Keycloak 的 `sub`） | 單向參考，領域 → 身分 |

> **反模式警告**：不要把會員等級、點數塞進 Keycloak 的 user attributes 或 token claims 裡當資料庫用。Token 是快照，領域資料要有單一事實來源（會員資料庫）。Token 裡只放授權需要的最小資訊（角色）。

## 3.2 戰術設計（Tactical Design）

### 3.2.1 聚合設計

**`Member` 聚合**（聚合根：`Member`）：

```
Member（聚合根 / Entity）
 ├─ MemberId          （值物件：領域自己的 UUID，非 Keycloak ID）
 ├─ IdentityId        （值物件：Keycloak sub，防腐對應鍵）
 ├─ Email             （值物件：含格式驗證）
 ├─ MemberName        （值物件：含長度規則）
 ├─ MemberStatus      （enum：ACTIVE / SUSPENDED）
 ├─ MembershipTier    （enum：STANDARD / GOLD / PLATINUM）
 └─ enrolledAt        （Instant）
```

設計決策說明：

1. **`MemberId` 與 `IdentityId` 分離**：領域有自己的識別，不拿外部系統的 ID 當主鍵。這讓「換 IAM」或「一個會員未來綁多個登入方式」都有轉圜空間。
2. **不變條件（Invariants）收在聚合內**：
   - 停權中的會員不能再被停權、也不能更新資料；
   - Email 格式在建構值物件時即驗證，聚合內不可能存在非法 Email。
3. **聚合不含密碼**：密碼從未進入領域模型，註冊時它只以參數形式流經應用層直達 Keycloak Port。

### 3.2.2 領域事件

| 事件 | 觸發時機 | 潛在訂閱者 |
|---|---|---|
| `MemberEnrolled` | 註冊完成 | 行銷上下文（發迎新券）、通知上下文（寄歡迎信） |
| `MemberSuspended` | 停權完成 | 訂單上下文（凍結進行中訂單） |

### 3.2.3 Ports（介面）設計

領域／應用層對外的依賴全部以 Port 表達（DIP 的落實，詳見第 4 章）：

| Port | 方向 | 職責 | 由誰實作 |
|---|---|---|---|
| `MemberRepository` | 出向（Driven） | 會員聚合的持久化 | JPA Adapter |
| `IdentityProvisioningPort` | 出向（Driven） | 建立身分帳號、停用帳號 | Keycloak Admin Client Adapter |
| `DomainEventPublisher` | 出向（Driven） | 發布領域事件 | Spring Events Adapter |
| `EnrollMemberUseCase` 等 | 入向（Driving） | 使用案例入口 | 應用層服務實作，由 REST Controller 呼叫 |

## 3.3 分層架構（六角形架構）

Maven 專案結構（package by layer within bounded context）：

```
com.shopmall.membership
├── domain                          ← 領域層：純 Java，零框架依賴
│   ├── model
│   │   ├── Member.java
│   │   ├── MemberId.java
│   │   ├── IdentityId.java
│   │   ├── Email.java
│   │   ├── MemberName.java
│   │   ├── MemberStatus.java
│   │   └── MembershipTier.java
│   ├── event
│   │   ├── MemberEnrolled.java
│   │   └── MemberSuspended.java
│   └── exception
│       ├── MemberNotFoundException.java
│       └── MemberAlreadySuspendedException.java
├── application                     ← 應用層：使用案例編排，依賴 domain 與 port
│   ├── port
│   │   ├── in
│   │   │   ├── EnrollMemberUseCase.java
│   │   │   ├── GetMemberProfileUseCase.java
│   │   │   ├── UpdateMemberProfileUseCase.java
│   │   │   └── SuspendMemberUseCase.java
│   │   └── out
│   │       ├── MemberRepository.java
│   │       ├── IdentityProvisioningPort.java
│   │       └── DomainEventPublisher.java
│   └── service
│       ├── EnrollMemberService.java
│       ├── MemberProfileService.java
│       └── SuspendMemberService.java
└── infrastructure                  ← 基礎設施層：所有框架與外部系統細節
    ├── identity
    │   ├── KeycloakIdentityProvisioningAdapter.java
    │   └── KeycloakAdminClientConfig.java
    ├── persistence
    │   ├── MemberJpaEntity.java
    │   ├── MemberJpaRepository.java
    │   └── MemberRepositoryAdapter.java
    ├── security
    │   ├── SecurityConfig.java
    │   ├── KeycloakRealmRoleConverter.java
    │   └── AuthenticatedIdentity.java
    └── web
        ├── MemberController.java
        ├── dto/…
        └── GlobalExceptionHandler.java
```

### 依賴方向（最重要的規則）

```
infrastructure ──> application ──> domain
      │                                ▲
      └────────────────────────────────┘
        （infrastructure 也可直接依賴 domain）
```

- `domain` **不 import 任何** Spring、JPA、Keycloak 類別；
- `application` 只依賴 `domain` 與自己宣告的 Port；
- 所有「髒東西」（JPA annotation、Keycloak client、HTTP）都關在 `infrastructure`。

> 你可以用 ArchUnit 把這條規則寫成測試（第 7 章示範），讓架構不會隨時間腐化。

---

下一章：[第 4 章：SOLID 原則的落實](04-solid-principles.md)
