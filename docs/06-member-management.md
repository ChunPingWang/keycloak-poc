# 第 6 章：會員管理實作

本章按照「領域層 → 應用層 → 基礎設施層」的順序，給出會員管理的完整實作。依賴方向決定了撰寫順序：先寫不依賴任何人的領域層。

## 6.1 領域層（`domain`）

領域層是純 Java：沒有任何 Spring、JPA、Keycloak 的 import。

### 6.1.1 值物件

```java
package com.shopmall.membership.domain.model;

import java.util.Objects;
import java.util.UUID;

/** 會員在本上下文中的識別（領域自己的 ID，與 Keycloak 無關）。 */
public record MemberId(UUID value) {
    public MemberId {
        Objects.requireNonNull(value, "MemberId 不可為 null");
    }
    public static MemberId newId() {
        return new MemberId(UUID.randomUUID());
    }
}
```

```java
package com.shopmall.membership.domain.model;

import java.util.Objects;

/** 會員對應的身分帳號識別（等於 IAM 系統中的使用者 ID / JWT 的 sub）。 */
public record IdentityId(String value) {
    public IdentityId {
        Objects.requireNonNull(value, "IdentityId 不可為 null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("IdentityId 不可為空白");
        }
    }
}
```

```java
package com.shopmall.membership.domain.model;

import java.util.regex.Pattern;

/** Email 值物件：自我驗證，聚合內不可能存在非法 Email。 */
public record Email(String value) {
    private static final Pattern FORMAT =
            Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    public Email {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Email 格式不正確: " + value);
        }
        value = value.toLowerCase();
    }
}
```

```java
package com.shopmall.membership.domain.model;

/** 會員顯示名稱：1~50 字。 */
public record MemberName(String value) {
    public MemberName {
        if (value == null || value.isBlank() || value.length() > 50) {
            throw new IllegalArgumentException("會員名稱必須為 1~50 字");
        }
        value = value.strip();
    }
}
```

```java
package com.shopmall.membership.domain.model;

public enum MemberStatus { ACTIVE, SUSPENDED }
```

```java
package com.shopmall.membership.domain.model;

public enum MembershipTier { STANDARD, GOLD, PLATINUM }
```

> 值物件用 `record` 實作：不可變、以值比較相等、在 compact constructor 中驗證不變條件——這讓「非法狀態無法被表示」。

### 6.1.2 聚合根：`Member`

```java
package com.shopmall.membership.domain.model;

import com.shopmall.membership.domain.exception.MemberAlreadySuspendedException;
import com.shopmall.membership.domain.exception.MemberSuspendedException;

import java.time.Instant;
import java.util.Objects;

/**
 * 會員聚合根。
 * 不變條件：
 * 1. 停權中的會員不可更新資料、不可再次停權。
 * 2. Email、名稱恆為合法值（由值物件保證）。
 */
public class Member {

    private final MemberId id;
    private final IdentityId identityId;
    private Email email;
    private MemberName name;
    private MemberStatus status;
    private MembershipTier tier;
    private final Instant enrolledAt;

    private Member(MemberId id, IdentityId identityId, Email email,
                   MemberName name, MemberStatus status,
                   MembershipTier tier, Instant enrolledAt) {
        this.id = Objects.requireNonNull(id);
        this.identityId = Objects.requireNonNull(identityId);
        this.email = Objects.requireNonNull(email);
        this.name = Objects.requireNonNull(name);
        this.status = Objects.requireNonNull(status);
        this.tier = Objects.requireNonNull(tier);
        this.enrolledAt = Objects.requireNonNull(enrolledAt);
    }

    /** 工廠方法：註冊新會員。新會員一律為 ACTIVE / STANDARD。 */
    public static Member enroll(IdentityId identityId, Email email,
                                MemberName name, Instant now) {
        return new Member(MemberId.newId(), identityId, email, name,
                MemberStatus.ACTIVE, MembershipTier.STANDARD, now);
    }

    /** 還原（reconstitute）：僅供持久化層由資料庫重建聚合使用。 */
    public static Member reconstitute(MemberId id, IdentityId identityId,
                                      Email email, MemberName name,
                                      MemberStatus status, MembershipTier tier,
                                      Instant enrolledAt) {
        return new Member(id, identityId, email, name, status, tier, enrolledAt);
    }

    /** 更新個人資料。停權中的會員不可更新。 */
    public void updateProfile(MemberName newName) {
        requireActive();
        this.name = newName;
    }

    /** 停權。 */
    public void suspend() {
        if (status == MemberStatus.SUSPENDED) {
            throw new MemberAlreadySuspendedException(id);
        }
        this.status = MemberStatus.SUSPENDED;
    }

    private void requireActive() {
        if (status != MemberStatus.ACTIVE) {
            throw new MemberSuspendedException(id);
        }
    }

    public MemberId id() { return id; }
    public IdentityId identityId() { return identityId; }
    public Email email() { return email; }
    public MemberName name() { return name; }
    public MemberStatus status() { return status; }
    public MembershipTier tier() { return tier; }
    public Instant enrolledAt() { return enrolledAt; }
}
```

設計說明：

- 建構子私有，只能經由 `enroll`（表達業務意圖）或 `reconstitute`（持久化重建）產生——命名即通用語言。
- 狀態轉移的規則（不可重複停權）**封裝在聚合內**，應用層只負責編排，不做業務判斷（SRP）。
- `now` 由外部傳入而非在聚合內呼叫 `Instant.now()`，讓時間可測試。

### 6.1.3 領域事件與例外

```java
package com.shopmall.membership.domain.event;

import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.MemberId;

import java.time.Instant;

public record MemberEnrolled(MemberId memberId, Email email, Instant occurredAt) {}
```

```java
package com.shopmall.membership.domain.event;

import com.shopmall.membership.domain.model.MemberId;

import java.time.Instant;

public record MemberSuspended(MemberId memberId, Instant occurredAt) {}
```

```java
package com.shopmall.membership.domain.exception;

import com.shopmall.membership.domain.model.MemberId;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(MemberId id) {
        super("找不到會員: " + id.value());
    }
}
```

（`MemberAlreadySuspendedException`、`MemberSuspendedException`、`DuplicateEmailException` 依同樣模式定義，從略。）

## 6.2 應用層（`application`）

### 6.2.1 入向 Port（使用案例介面，ISP）

```java
package com.shopmall.membership.application.port.in;

import com.shopmall.membership.domain.model.MemberId;

public interface EnrollMemberUseCase {

    MemberId enroll(EnrollMemberCommand command);

    /** rawPassword 只以參數形式流經應用層，直達身分供裝 Port，絕不落地。 */
    record EnrollMemberCommand(String email, String name, String rawPassword) {}
}
```

```java
package com.shopmall.membership.application.port.in;

import com.shopmall.membership.domain.model.IdentityId;

public interface GetMemberProfileUseCase {

    MemberProfile byIdentity(IdentityId identityId);

    record MemberProfile(String memberId, String email, String name,
                         String status, String tier) {}
}
```

```java
package com.shopmall.membership.application.port.in;

import com.shopmall.membership.domain.model.IdentityId;

public interface UpdateMemberProfileUseCase {
    void update(IdentityId identityId, String newName);
}
```

```java
package com.shopmall.membership.application.port.in;

import com.shopmall.membership.domain.model.MemberId;

public interface SuspendMemberUseCase {
    void suspend(MemberId memberId);
}
```

### 6.2.2 出向 Port（DIP：抽象由高層擁有）

```java
package com.shopmall.membership.application.port.out;

import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.Member;
import com.shopmall.membership.domain.model.MemberId;

import java.util.Optional;

public interface MemberRepository {
    void save(Member member);
    Optional<Member> findById(MemberId id);
    Optional<Member> findByIdentityId(IdentityId identityId);
    boolean existsByEmail(Email email);
}
```

```java
package com.shopmall.membership.application.port.out;

import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.MemberName;

/**
 * 身分供裝 Port：會員上下文對 IAM 系統的全部需求。
 * 介面形狀由使用案例決定，與 Keycloak API 無關（防腐層）。
 */
public interface IdentityProvisioningPort {

    /**
     * 建立可登入的身分帳號並賦予會員角色。
     * @return 新帳號的 IdentityId
     * @throws DuplicateIdentityException Email 已被註冊
     */
    IdentityId provision(Email email, MemberName name, RawPassword password);

    /** 停用帳號，使其無法再登入、既有 session 失效。 */
    void disable(IdentityId identityId);

    /** 密碼包裝型別：toString 遮罩，避免意外進入日誌。 */
    record RawPassword(String value) {
        public RawPassword {
            if (value == null || value.length() < 8) {
                throw new IllegalArgumentException("密碼至少 8 碼");
            }
        }
        @Override public String toString() { return "RawPassword[****]"; }
    }
}
```

```java
package com.shopmall.membership.application.port.out;

public interface DomainEventPublisher {
    void publish(Object domainEvent);
}
```

### 6.2.3 應用服務：註冊

```java
package com.shopmall.membership.application.service;

import com.shopmall.membership.application.port.in.EnrollMemberUseCase;
import com.shopmall.membership.application.port.out.DomainEventPublisher;
import com.shopmall.membership.application.port.out.IdentityProvisioningPort;
import com.shopmall.membership.application.port.out.IdentityProvisioningPort.RawPassword;
import com.shopmall.membership.application.port.out.MemberRepository;
import com.shopmall.membership.domain.event.MemberEnrolled;
import com.shopmall.membership.domain.exception.DuplicateEmailException;
import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.Member;
import com.shopmall.membership.domain.model.MemberId;
import com.shopmall.membership.domain.model.MemberName;

import java.time.Clock;
import java.time.Instant;

/**
 * 註冊使用案例：
 * 1. 檢查 Email 未被使用
 * 2. 在 IAM 建立帳號（取得 IdentityId）
 * 3. 建立會員聚合並持久化
 * 4. 發布 MemberEnrolled 事件
 */
public class EnrollMemberService implements EnrollMemberUseCase {

    private final MemberRepository memberRepository;
    private final IdentityProvisioningPort identityProvisioning;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public EnrollMemberService(MemberRepository memberRepository,
                               IdentityProvisioningPort identityProvisioning,
                               DomainEventPublisher eventPublisher,
                               Clock clock) {
        this.memberRepository = memberRepository;
        this.identityProvisioning = identityProvisioning;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public MemberId enroll(EnrollMemberCommand command) {
        var email = new Email(command.email());
        var name = new MemberName(command.name());

        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        // 先建 IAM 帳號：失敗則整個註冊失敗，不會產生「有會員沒帳號」的狀態。
        var identityId = identityProvisioning.provision(
                email, name, new RawPassword(command.rawPassword()));

        Instant now = clock.instant();
        var member = Member.enroll(identityId, email, name, now);
        memberRepository.save(member);

        eventPublisher.publish(new MemberEnrolled(member.id(), email, now));
        return member.id();
    }
}
```

> **一致性策略**：跨 Keycloak 與本地資料庫沒有分散式交易。順序上先建帳號、後存會員：若存會員失敗，會留下「有帳號、無會員」的孤兒帳號，可由補償（在 catch 中呼叫 `identityProvisioning.disable`）或對帳批次清理。附錄 A 討論更完整的做法（Outbox pattern）。

### 6.2.4 應用服務：查詢與更新（含「只能改自己」的授權）

```java
package com.shopmall.membership.application.service;

import com.shopmall.membership.application.port.in.GetMemberProfileUseCase;
import com.shopmall.membership.application.port.in.UpdateMemberProfileUseCase;
import com.shopmall.membership.application.port.out.MemberRepository;
import com.shopmall.membership.domain.exception.MemberNotFoundException;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.MemberName;

public class MemberProfileService
        implements GetMemberProfileUseCase, UpdateMemberProfileUseCase {

    private final MemberRepository memberRepository;

    public MemberProfileService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public MemberProfile byIdentity(IdentityId identityId) {
        var member = memberRepository.findByIdentityId(identityId)
                .orElseThrow(() -> new MemberNotFoundException(identityId));
        return new MemberProfile(
                member.id().value().toString(),
                member.email().value(),
                member.name().value(),
                member.status().name(),
                member.tier().name());
    }

    @Override
    public void update(IdentityId identityId, String newName) {
        // 「只能改自己」的授權在此天然成立：
        // 查詢鍵就是呼叫者自己的 IdentityId（來自已驗證的 JWT sub），
        // 使用案例根本沒有提供「改別人」的入口。
        var member = memberRepository.findByIdentityId(identityId)
                .orElseThrow(() -> new MemberNotFoundException(identityId));
        member.updateProfile(new MemberName(newName));
        memberRepository.save(member);
    }
}
```

### 6.2.5 應用服務：停權

```java
package com.shopmall.membership.application.service;

import com.shopmall.membership.application.port.in.SuspendMemberUseCase;
import com.shopmall.membership.application.port.out.DomainEventPublisher;
import com.shopmall.membership.application.port.out.IdentityProvisioningPort;
import com.shopmall.membership.application.port.out.MemberRepository;
import com.shopmall.membership.domain.event.MemberSuspended;
import com.shopmall.membership.domain.exception.MemberNotFoundException;
import com.shopmall.membership.domain.model.MemberId;

import java.time.Clock;

public class SuspendMemberService implements SuspendMemberUseCase {

    private final MemberRepository memberRepository;
    private final IdentityProvisioningPort identityProvisioning;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public SuspendMemberService(MemberRepository memberRepository,
                                IdentityProvisioningPort identityProvisioning,
                                DomainEventPublisher eventPublisher,
                                Clock clock) {
        this.memberRepository = memberRepository;
        this.identityProvisioning = identityProvisioning;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public void suspend(MemberId memberId) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));

        member.suspend();                                  // 領域規則（不可重複停權）在聚合內
        memberRepository.save(member);
        identityProvisioning.disable(member.identityId()); // 同步停用登入能力

        eventPublisher.publish(new MemberSuspended(memberId, clock.instant()));
    }
}
```

## 6.3 基礎設施層（`infrastructure`）

### 6.3.1 Keycloak Admin Client 設定

```java
package com.shopmall.membership.infrastructure.identity;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminClientConfig {

    /** 以 shopmall-backend 的服務帳號（client_credentials）連線 Keycloak Admin API。 */
    @Bean
    Keycloak keycloakAdminClient(
            @Value("${shopmall.keycloak.server-url}") String serverUrl,
            @Value("${shopmall.keycloak.realm}") String realm,
            @Value("${shopmall.keycloak.admin-client-id}") String clientId,
            @Value("${shopmall.keycloak.admin-client-secret}") String clientSecret) {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
    }
}
```

### 6.3.2 Keycloak Adapter（防腐層核心）

```java
package com.shopmall.membership.infrastructure.identity;

import com.shopmall.membership.application.port.out.IdentityProvisioningPort;
import com.shopmall.membership.domain.exception.DuplicateIdentityException;
import com.shopmall.membership.domain.exception.IdentityProvisioningException;
import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.MemberName;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IdentityProvisioningPort 的 Keycloak 實作（防腐層）。
 * Keycloak 的型別（UserRepresentation 等）只存在於這個類別，不外洩。
 */
@Component
public class KeycloakIdentityProvisioningAdapter implements IdentityProvisioningPort {

    private final Keycloak keycloak;
    private final String realm;
    private final String memberRole;

    public KeycloakIdentityProvisioningAdapter(
            Keycloak keycloak,
            @Value("${shopmall.keycloak.realm}") String realm,
            @Value("${shopmall.keycloak.member-role}") String memberRole) {
        this.keycloak = keycloak;
        this.realm = realm;
        this.memberRole = memberRole;
    }

    @Override
    public IdentityId provision(Email email, MemberName name, RawPassword password) {
        var user = new UserRepresentation();
        user.setUsername(email.value());          // 以 Email 作為登入帳號
        user.setEmail(email.value());
        user.setFirstName(name.value());
        user.setEnabled(true);
        user.setEmailVerified(false);             // 交給 Keycloak 的驗證信流程

        var credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password.value());
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        RealmResource realmResource = keycloak.realm(realm);
        try (Response response = realmResource.users().create(user)) {
            if (response.getStatus() == 409) {
                throw new DuplicateIdentityException(email);
            }
            if (response.getStatus() != 201) {
                throw new IdentityProvisioningException(
                        "建立帳號失敗，HTTP " + response.getStatus());
            }
            String userId = CreatedResponseUtil.getCreatedId(response);
            assignMemberRole(realmResource, userId);
            return new IdentityId(userId);
        }
    }

    private void assignMemberRole(RealmResource realmResource, String userId) {
        RoleRepresentation role = realmResource.roles().get(memberRole).toRepresentation();
        realmResource.users().get(userId).roles().realmLevel().add(List.of(role));
    }

    @Override
    public void disable(IdentityId identityId) {
        var userResource = realmResource().users().get(identityId.value());
        UserRepresentation user = userResource.toRepresentation();   // 不存在會擲 404
        user.setEnabled(false);
        userResource.update(user);
        userResource.logout();   // 使既有 SSO session 失效（已簽發的 token 到期前仍有效，見附錄 A）
    }

    private RealmResource realmResource() {
        return keycloak.realm(realm);
    }
}
```

### 6.3.3 JPA 持久化 Adapter

```java
package com.shopmall.membership.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** JPA 實體：純資料載體，與領域聚合分離（領域模型不被 JPA 註解污染）。 */
@Entity
@Table(name = "members")
public class MemberJpaEntity {

    @Id
    private UUID id;

    @Column(name = "identity_id", nullable = false, unique = true)
    private String identityId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusColumn status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TierColumn tier;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    public enum StatusColumn { ACTIVE, SUSPENDED }
    public enum TierColumn { STANDARD, GOLD, PLATINUM }

    protected MemberJpaEntity() {}   // for JPA

    // getters / setters 從略
}
```

```java
package com.shopmall.membership.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, UUID> {
    Optional<MemberJpaEntity> findByIdentityId(String identityId);
    boolean existsByEmail(String email);
}
```

```java
package com.shopmall.membership.infrastructure.persistence;

import com.shopmall.membership.application.port.out.MemberRepository;
import com.shopmall.membership.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** MemberRepository 的 JPA 實作：負責聚合 ↔ JPA 實體的雙向轉換。 */
@Component
public class MemberRepositoryAdapter implements MemberRepository {

    private final MemberJpaRepository jpa;

    public MemberRepositoryAdapter(MemberJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Member member) {
        var entity = new MemberJpaEntity();
        entity.setId(member.id().value());
        entity.setIdentityId(member.identityId().value());
        entity.setEmail(member.email().value());
        entity.setName(member.name().value());
        entity.setStatus(MemberJpaEntity.StatusColumn.valueOf(member.status().name()));
        entity.setTier(MemberJpaEntity.TierColumn.valueOf(member.tier().name()));
        entity.setEnrolledAt(member.enrolledAt());
        jpa.save(entity);
    }

    @Override
    public Optional<Member> findById(MemberId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Member> findByIdentityId(IdentityId identityId) {
        return jpa.findByIdentityId(identityId.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email.value());
    }

    private Member toDomain(MemberJpaEntity e) {
        return Member.reconstitute(
                new MemberId(e.getId()),
                new IdentityId(e.getIdentityId()),
                new Email(e.getEmail()),
                new MemberName(e.getName()),
                MemberStatus.valueOf(e.getStatus().name()),
                MembershipTier.valueOf(e.getTier().name()),
                e.getEnrolledAt());
    }
}
```

資料表（Flyway migration `V1__create_members.sql`）：

```sql
CREATE TABLE members (
    id           UUID PRIMARY KEY,
    identity_id  VARCHAR(64)  NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    name         VARCHAR(50)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    tier         VARCHAR(20)  NOT NULL,
    enrolled_at  TIMESTAMPTZ  NOT NULL
);
```

### 6.3.4 使用案例的 Bean 組裝

應用服務是純 Java 類別（沒有 `@Service` 註解——保持應用層無框架），在基礎設施層組裝：

```java
package com.shopmall.membership.infrastructure.config;

import com.shopmall.membership.application.port.out.*;
import com.shopmall.membership.application.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class MembershipUseCaseConfig {

    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean
    EnrollMemberService enrollMemberService(MemberRepository repo,
                                            IdentityProvisioningPort identity,
                                            DomainEventPublisher events,
                                            Clock clock) {
        return new EnrollMemberService(repo, identity, events, clock);
    }

    @Bean
    MemberProfileService memberProfileService(MemberRepository repo) {
        return new MemberProfileService(repo);
    }

    @Bean
    SuspendMemberService suspendMemberService(MemberRepository repo,
                                              IdentityProvisioningPort identity,
                                              DomainEventPublisher events,
                                              Clock clock) {
        return new SuspendMemberService(repo, identity, events, clock);
    }
}
```

（`DomainEventPublisher` 的 Spring 實作只是把事件轉發給 `ApplicationEventPublisher`，從略。）

### 6.3.5 REST Controllers

```java
package com.shopmall.membership.infrastructure.web;

import com.shopmall.membership.application.port.in.*;
import com.shopmall.membership.application.port.in.EnrollMemberUseCase.EnrollMemberCommand;
import com.shopmall.membership.application.port.in.GetMemberProfileUseCase.MemberProfile;
import com.shopmall.membership.infrastructure.security.AuthenticatedIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final EnrollMemberUseCase enrollMember;
    private final GetMemberProfileUseCase getProfile;
    private final UpdateMemberProfileUseCase updateProfile;
    private final AuthenticatedIdentity authenticatedIdentity;

    public MemberController(EnrollMemberUseCase enrollMember,
                            GetMemberProfileUseCase getProfile,
                            UpdateMemberProfileUseCase updateProfile,
                            AuthenticatedIdentity authenticatedIdentity) {
        this.enrollMember = enrollMember;
        this.getProfile = getProfile;
        this.updateProfile = updateProfile;
        this.authenticatedIdentity = authenticatedIdentity;
    }

    /** 會員註冊（匿名端點）。 */
    @PostMapping
    public ResponseEntity<Void> enroll(@Valid @RequestBody EnrollRequest request) {
        var memberId = enrollMember.enroll(new EnrollMemberCommand(
                request.email(), request.name(), request.password()));
        return ResponseEntity.created(URI.create("/api/members/" + memberId.value())).build();
    }

    /** 查詢自己的個人資料。 */
    @GetMapping("/me")
    public MemberProfile me() {
        return getProfile.byIdentity(authenticatedIdentity.currentIdentityId());
    }

    /** 更新自己的個人資料。 */
    @PutMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        updateProfile.update(authenticatedIdentity.currentIdentityId(), request.name());
    }

    record EnrollRequest(
            @NotBlank @jakarta.validation.constraints.Email String email,
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Size(min = 8, max = 128) String password) {}

    record UpdateProfileRequest(@NotBlank @Size(max = 50) String name) {}
}
```

```java
package com.shopmall.membership.infrastructure.web;

import com.shopmall.membership.application.port.in.SuspendMemberUseCase;
import com.shopmall.membership.domain.model.MemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 後台端點：僅客服角色可用（粗粒度授權在此，領域規則在聚合內）。 */
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final SuspendMemberUseCase suspendMember;

    public AdminMemberController(SuspendMemberUseCase suspendMember) {
        this.suspendMember = suspendMember;
    }

    @PreAuthorize("hasRole('customer-service')")
    @PostMapping("/{memberId}/suspension")
    public ResponseEntity<Void> suspend(@PathVariable UUID memberId) {
        suspendMember.suspend(new MemberId(memberId));
        return ResponseEntity.noContent().build();
    }
}
```

### 6.3.6 例外對應 HTTP 狀態

```java
package com.shopmall.membership.infrastructure.web;

import com.shopmall.membership.domain.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({DuplicateEmailException.class, DuplicateIdentityException.class})
    ProblemDetail duplicate(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email 已被註冊");
    }

    @ExceptionHandler(MemberNotFoundException.class)
    ProblemDetail notFound(MemberNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MemberAlreadySuspendedException.class)
    ProblemDetail alreadySuspended(MemberAlreadySuspendedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
```

## 6.4 端到端驗證

```bash
# 1. 註冊新會員（匿名）
curl -i -X POST http://localhost:9090/api/members \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","name":"Alice Chen","password":"alice-secret-123"}'
# → HTTP/1.1 201 Created, Location: /api/members/<memberId>

# 2. 以新會員身分登入取得 Token
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/shopmall/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=shopmall-web" \
  -d "username=alice@example.com" -d "password=alice-secret-123" | jq -r .access_token)

# 3. 查詢自己的資料
curl -s http://localhost:9090/api/members/me -H "Authorization: Bearer $TOKEN" | jq
# → {"memberId":"...","email":"alice@example.com","name":"Alice Chen",
#    "status":"ACTIVE","tier":"STANDARD"}

# 4. 客服停權該會員（用 cs-bob 的 Token）
CS_TOKEN=$(curl -s -X POST "http://localhost:8080/realms/shopmall/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=shopmall-web" \
  -d "username=cs-bob" -d "password=bob-secret" | jq -r .access_token)

curl -i -X POST "http://localhost:9090/api/admin/members/<memberId>/suspension" \
  -H "Authorization: Bearer $CS_TOKEN"
# → 204 No Content；Alice 之後將無法再登入（Keycloak 帳號已停用）
```

---

下一章：[第 7 章：測試策略](07-testing.md)
