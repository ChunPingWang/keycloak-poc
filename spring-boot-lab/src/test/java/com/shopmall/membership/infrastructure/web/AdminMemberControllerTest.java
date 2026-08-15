package com.shopmall.membership.infrastructure.web;

import com.shopmall.membership.application.port.in.SuspendMemberUseCase;
import com.shopmall.membership.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization 測試：驗證「僅 customer-service 角色可停權」的粗粒度授權。
 * 用 spring-security-test 的 jwt() 模擬已通過 Authentication 的請求，不需真的 Keycloak。
 */
@WebMvcTest(AdminMemberController.class)
@Import(SecurityConfig.class)
class AdminMemberControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SuspendMemberUseCase suspendMember;
    @MockBean JwtDecoder jwtDecoder;   // SecurityConfig 需要，但測試中不會真的解碼

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
