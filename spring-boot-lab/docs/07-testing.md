# 第 7 章：測試策略

分層架構的最大紅利在測試：每一層都能以最便宜的方式測到重點。本章由內而外介紹四種測試。

## 7.1 測試金字塔對應

| 層 | 測試類型 | 需要的環境 | 速度 |
|---|---|---|---|
| 領域層 | 純單元測試 | 無（Plain JUnit） | 毫秒 |
| 應用層 | 單元測試 + 記憶體 Adapter | 無 | 毫秒 |
| 基礎設施層（Keycloak Adapter） | 整合測試 | Testcontainers（Keycloak） | 秒~分 |
| 全鏈路（HTTP → DB） | 整合測試 | Testcontainers（Keycloak + PostgreSQL） | 秒~分 |
| 架構規則 | ArchUnit | 無 | 毫秒 |

測試相依：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.github.dasniko</groupId>
    <artifactId>testcontainers-keycloak</artifactId>
    <version>3.5.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

## 7.2 領域層單元測試

聚合的不變條件是最重要、也最便宜的測試：

```java
class MemberTest {

    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");

    private Member newMember() {
        return Member.enroll(
                new IdentityId("kc-user-1"),
                new Email("alice@example.com"),
                new MemberName("Alice"),
                NOW);
    }

    @Test
    void 新註冊會員為_ACTIVE_且_STANDARD() {
        var member = newMember();
        assertEquals(MemberStatus.ACTIVE, member.status());
        assertEquals(MembershipTier.STANDARD, member.tier());
    }

    @Test
    void 停權後不可更新資料() {
        var member = newMember();
        member.suspend();
        assertThrows(MemberSuspendedException.class,
                () -> member.updateProfile(new MemberName("New Name")));
    }

    @Test
    void 不可重複停權() {
        var member = newMember();
        member.suspend();
        assertThrows(MemberAlreadySuspendedException.class, member::suspend);
    }

    @Test
    void email_格式錯誤時無法建立值物件() {
        assertThrows(IllegalArgumentException.class, () -> new Email("not-an-email"));
    }
}
```

## 7.3 應用層測試：記憶體 Adapter（LSP 的回報）

不用 mocking framework 也可以：為 Port 寫簡單的記憶體實作（同時作為 LSP 契約的第二個實作）：

```java
class InMemoryIdentityProvisioning implements IdentityProvisioningPort {
    final Map<String, Email> provisioned = new HashMap<>();
    final Set<String> disabled = new HashSet<>();
    private int seq = 0;

    @Override
    public IdentityId provision(Email email, MemberName name, RawPassword password) {
        if (provisioned.containsValue(email)) {
            throw new DuplicateIdentityException(email);   // 遵守與 Keycloak 實作相同的契約
        }
        var id = "kc-" + (++seq);
        provisioned.put(id, email);
        return new IdentityId(id);
    }

    @Override
    public void disable(IdentityId identityId) {
        if (!provisioned.containsKey(identityId.value())) {
            throw new IdentityNotFoundException(identityId);
        }
        disabled.add(identityId.value());
    }
}
```

```java
class EnrollMemberServiceTest {

    InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
    InMemoryIdentityProvisioning identityProvisioning = new InMemoryIdentityProvisioning();
    RecordingEventPublisher events = new RecordingEventPublisher();
    Clock clock = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC);

    EnrollMemberService service = new EnrollMemberService(
            memberRepository, identityProvisioning, events, clock);

    @Test
    void 註冊成功_會建立IAM帳號_存會員_發事件() {
        var memberId = service.enroll(new EnrollMemberCommand(
                "alice@example.com", "Alice", "alice-secret-123"));

        var member = memberRepository.findById(memberId).orElseThrow();
        assertEquals("kc-1", member.identityId().value());
        assertEquals(1, identityProvisioning.provisioned.size());
        assertInstanceOf(MemberEnrolled.class, events.published().getFirst());
    }

    @Test
    void email_重複時擲出_DuplicateEmailException_且不建立IAM帳號() {
        service.enroll(new EnrollMemberCommand("alice@example.com", "Alice", "alice-secret-123"));

        assertThrows(DuplicateEmailException.class, () ->
                service.enroll(new EnrollMemberCommand("alice@example.com", "Bob", "bob-secret-123")));
        assertEquals(1, identityProvisioning.provisioned.size());
    }
}
```

這組測試在毫秒內跑完、不需 Docker——這就是第 4 章 DIP 的具體回報。

## 7.4 Keycloak Adapter 整合測試（Testcontainers）

Adapter 本身必須用真的 Keycloak 驗證（契約測試的第一個實作）：

```java
@Testcontainers
class KeycloakIdentityProvisioningAdapterIT {

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
            .withRealmImportFile("shopmall-realm-test.json");   // 內含 realm、member 角色、backend client

    static KeycloakIdentityProvisioningAdapter adapter;

    @BeforeAll
    static void setUp() {
        var admin = KeycloakBuilder.builder()
                .serverUrl(keycloak.getAuthServerUrl())
                .realm("shopmall")
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId("shopmall-backend")
                .clientSecret("test-secret")
                .build();
        adapter = new KeycloakIdentityProvisioningAdapter(admin, "shopmall", "member");
    }

    @Test
    void 建立帳號後可在Keycloak查到_且具有member角色() {
        var id = adapter.provision(new Email("it-user@example.com"),
                new MemberName("IT User"),
                new RawPassword("it-secret-123"));
        assertNotNull(id.value());
        // 進一步以 admin client 驗證角色已指派（從略）
    }

    @Test
    void 重複Email擲出DuplicateIdentityException() {
        adapter.provision(new Email("dup@example.com"), new MemberName("A"),
                new RawPassword("secret-123"));
        assertThrows(DuplicateIdentityException.class, () ->
                adapter.provision(new Email("dup@example.com"), new MemberName("B"),
                        new RawPassword("secret-456")));
    }
}
```

> **LSP 契約測試技巧**：把 7.3 與 7.4 中重複的情境抽成抽象測試類別 `IdentityProvisioningContractTest`，由 `InMemory...Test` 與 `Keycloak...IT` 分別繼承並提供受測實作——同一組行為斷言跑在兩個實作上，「可替換性」就有了測試背書。

## 7.5 Web 層授權測試

用 `spring-security-test` 的 `jwt()` post-processor，不需要真的 Keycloak：

```java
@WebMvcTest(AdminMemberController.class)
@Import(SecurityConfig.class)
class AdminMemberControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SuspendMemberUseCase suspendMember;

    @Test
    void 具customer_service角色可停權() throws Exception {
        mockMvc.perform(post("/api/admin/members/{id}/suspension", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_customer-service"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void 一般會員停權他人_得到403() throws Exception {
        mockMvc.perform(post("/api/admin/members/{id}/suspension", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_member"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 未登入_得到401() throws Exception {
        mockMvc.perform(post("/api/admin/members/{id}/suspension", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
```

## 7.6 架構測試（ArchUnit）：讓分層規則不腐化

```java
@AnalyzeClasses(packages = "com.shopmall.membership")
class ArchitectureTest {

    @ArchTest
    static final ArchRule 領域層不依賴任何框架 =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                            "org.keycloak..");

    @ArchTest
    static final ArchRule 應用層不依賴基礎設施層 =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule Keycloak型別只能出現在identity套件 =
            noClasses().that().resideOutsideOfPackage("..infrastructure.identity..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.keycloak..");
}
```

這三條規則把第 3、4 章的架構決策固化成 CI 會擋下的測試——防腐層不再只靠自律。

---

下一章：[附錄 A：常見問題與最佳實踐](appendix-a-faq.md)
