package com.shopmall.membership.infrastructure.web;

import com.shopmall.membership.application.port.in.EnrollMemberUseCase;
import com.shopmall.membership.application.port.in.GetMemberProfileUseCase;
import com.shopmall.membership.application.port.in.GetMemberProfileUseCase.MemberProfile;
import com.shopmall.membership.application.port.in.UpdateMemberProfileUseCase;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.MemberId;
import com.shopmall.membership.infrastructure.security.AuthenticatedIdentity;
import com.shopmall.membership.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, AuthenticatedIdentity.class})
class MemberControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean EnrollMemberUseCase enrollMember;
    @MockBean GetMemberProfileUseCase getProfile;
    @MockBean UpdateMemberProfileUseCase updateProfile;
    @MockBean JwtDecoder jwtDecoder;

    @Test
    void 註冊是匿名端點_不需Token() throws Exception {
        when(enrollMember.enroll(any())).thenReturn(MemberId.newId());

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","name":"Alice",
                                 "password":"alice-secret-123"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void 查詢自己的資料_需要認證_未帶Token得401() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 查詢自己的資料_以JWT的sub為查詢鍵() throws Exception {
        when(getProfile.byIdentity(new IdentityId("kc-alice")))
                .thenReturn(new MemberProfile("m-1", "alice@example.com",
                        "Alice", "ACTIVE", "STANDARD"));

        mockMvc.perform(get("/api/members/me")
                        .with(jwt().jwt(j -> j.subject("kc-alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
