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
