package com.shopmall.membership.infrastructure.identity;

import com.shopmall.membership.application.port.out.IdentityProvisioningPort;
import com.shopmall.membership.domain.exception.DuplicateIdentityException;
import com.shopmall.membership.domain.exception.IdentityNotFoundException;
import com.shopmall.membership.domain.exception.IdentityProvisioningException;
import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.MemberName;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IdentityProvisioningPort 的 Keycloak 實作（防腐層）。
 * Keycloak 的型別（UserRepresentation 等）只存在於這個 package，不外洩。
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
        UserResource userResource = keycloak.realm(realm).users().get(identityId.value());
        try {
            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(false);
            userResource.update(user);
            // 使既有 SSO session 失效（已簽發的 token 到期前仍有效，見附錄 A.2）
            userResource.logout();
        } catch (NotFoundException e) {
            throw new IdentityNotFoundException(identityId);
        }
    }
}
