package com.shopmall.membership.infrastructure.web;

import com.shopmall.membership.application.port.in.EnrollMemberUseCase;
import com.shopmall.membership.application.port.in.EnrollMemberUseCase.EnrollMemberCommand;
import com.shopmall.membership.application.port.in.GetMemberProfileUseCase;
import com.shopmall.membership.application.port.in.GetMemberProfileUseCase.MemberProfile;
import com.shopmall.membership.application.port.in.UpdateMemberProfileUseCase;
import com.shopmall.membership.infrastructure.security.AuthenticatedIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
