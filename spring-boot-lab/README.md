# Keycloak 與 Java Spring 整合教學指南

> 以電子商務系統的「會員管理」為例，並以 **DDD（領域驅動設計）** 與 **SOLID 原則** 作為設計準則。

> 📍 **本目錄在整個儲存庫中的位置**（原 `ChunPingWang/keycloak-tutorial` 儲存庫，已整併至此）：
>
> | 內容 | 定位 |
> |------|------|
> | [`../README.md`](../README.md) | 初學者動手教材：用 `curl` 純手工走完 OAuth/OIDC 每個流程 |
> | [`../CURRICULUM.md`](../CURRICULUM.md) | 進階課綱：13 個 Module，協定原理到生產部署 |
> | **本目錄** | **可建置執行的整合實作** — 對應進階課綱的 [Module 7：應用程式整合實戰](../CURRICULUM.md#module-7應用程式整合實戰) |
>
> 建議順序：先用 `../README.md` 建立體感 → 讀本目錄看真實專案怎麼落地 → 回到 `../CURRICULUM.md` 補齊生產議題。

## 這份指南是什麼？

本指南示範如何在一個電子商務系統中，將 **Keycloak** 作為身分與存取管理（IAM）伺服器，與 **Spring Boot 3.x** 應用程式整合，實作「會員管理」這個限界上下文（Bounded Context）。

重點不只是「把 Keycloak 接起來」，而是示範：

- 如何用 **DDD 戰略設計** 界定「會員」與「帳號（身分認證）」的邊界；
- 如何用 **六角形架構（Hexagonal / Ports & Adapters）** 讓領域層不依賴 Keycloak；
- 如何在每一層落實 **SOLID 原則**，讓未來即使更換 IAM 供應商（如 Auth0、Cognito），領域程式碼也完全不用修改。

## 技術棧

| 元件 | 版本 | 用途 |
|---|---|---|
| Java | 21 | 語言 |
| Spring Boot | 3.3.x | 應用程式框架 |
| Spring Security（OAuth2 Resource Server） | 6.x | JWT 驗證與授權 |
| Keycloak | 26.x | 身分與存取管理伺服器 |
| Keycloak Admin Client | 26.x | 由後端程式化管理使用者 |
| Spring Data JPA + PostgreSQL | — | 會員領域資料持久化 |
| Docker Compose | — | 本機環境建置 |

## 章節目錄

| 章節 | 內容 |
|---|---|
| [第 1 章：核心概念](docs/01-concepts.md) | OAuth 2.0、OIDC、JWT、Keycloak 核心名詞（Realm、Client、Role） |
| [第 2 章：環境建置](docs/02-environment-setup.md) | 用 Docker Compose 啟動 Keycloak 與 PostgreSQL，建立 Realm、Client、角色與測試使用者 |
| [第 3 章：DDD 戰略與戰術設計](docs/03-ddd-design.md) | 限界上下文、通用語言、聚合／實體／值物件設計、六角形架構分層 |
| [第 4 章：SOLID 原則的落實](docs/04-solid-principles.md) | 逐一檢視 SRP、OCP、LSP、ISP、DIP 如何體現在本專案的設計中 |
| [第 5 章：Spring Boot 與 Keycloak 整合](docs/05-spring-keycloak-integration.md) | Resource Server 設定、JWT 角色轉換、方法級授權 |
| [第 6 章：會員管理實作](docs/06-member-management.md) | 完整程式碼：會員註冊、查詢、更新、停權（含 Keycloak Admin API 整合） |
| [第 7 章：測試策略](docs/07-testing.md) | 領域單元測試、應用層測試（Mock Port）、整合測試（Testcontainers） |
| [附錄 A：常見問題與最佳實踐](docs/appendix-a-faq.md) | Token 驗證失敗排查、角色對應、正式環境注意事項 |

## 系統情境：電子商務會員管理

我們的電商系統名為 **ShopMall**，本指南聚焦其中的「會員管理」限界上下文，涵蓋以下使用案例：

1. **會員註冊**：訪客提供 Email、姓名、密碼註冊成為會員。系統需同時在 Keycloak 建立帳號（負責認證），並在會員資料庫建立會員資料（負責領域資訊，如會員等級、紅利點數）。
2. **會員登入**：由 Keycloak 負責（OIDC Authorization Code Flow），應用程式只驗證 Access Token。
3. **查詢個人資料**：登入會員可查詢自己的資料。
4. **更新個人資料**：登入會員可更新自己的姓名等資料。
5. **會員停權**：客服人員（`customer-service` 角色）可停權違規會員，停權需同步停用 Keycloak 帳號。

### 職責邊界（先講結論）

| 關注點 | 負責方 |
|---|---|
| 帳號、密碼、登入、Token 簽發、MFA | **Keycloak** |
| 會員等級、紅利點數、會員狀態等領域邏輯 | **ShopMall 會員上下文** |
| 兩者的關聯 | 以 Keycloak 的 `sub`（User ID）作為對應鍵 |

> **核心設計思想**：認證（Authentication）是通用子領域（Generic Subdomain），交給現成產品 Keycloak；會員管理是支撐核心業務的子領域，由我們自己建模。領域層透過 **Port（介面）** 與 Keycloak 互動，永遠不直接依賴 Keycloak 的 API。

## 快速開始

本目錄除了教學文件，也包含**可建置執行的完整實作**（`src/`，即第 5、6 章的程式碼）：

```bash
# 1. 啟動 Keycloak 與 PostgreSQL
docker compose up -d

# 2. 依第 2 章設定 Realm、Client、角色與測試使用者

# 3. 啟動 Spring Boot 應用程式（需先設定後端 client secret）
export KEYCLOAK_ADMIN_CLIENT_SECRET=<shopmall-backend 的 client secret>
./mvnw spring-boot:run

# 4. 執行測試（不需 Docker，21 個測試涵蓋領域規則、授權與架構規則）
./mvnw test
```

### 實作完成度

| 關注點 | 實作位置 |
|---|---|
| **Authentication**（JWT 驗證、`realm_access.roles` → `ROLE_*` 轉換） | `infrastructure/security/`（`SecurityConfig`、`KeycloakRealmRoleConverter`、`AuthenticatedIdentity`） |
| **Authorization**（URL 規則、`@PreAuthorize` 角色檢查、「只能改自己」領域授權） | `SecurityConfig` + `AdminMemberController` + `MemberProfileService` |
| 會員領域模型（聚合、值物件、領域事件） | `domain/` |
| 使用案例（註冊、查詢、更新、停權） | `application/` |
| Keycloak Admin API 防腐層、JPA 持久化、REST API | `infrastructure/` |
| 測試（領域單元、應用層記憶體 Adapter、Security 切片、ArchUnit） | `src/test/` |

建議按章節順序閱讀；若你已熟悉 OAuth2/OIDC，可直接從第 3 章開始。
