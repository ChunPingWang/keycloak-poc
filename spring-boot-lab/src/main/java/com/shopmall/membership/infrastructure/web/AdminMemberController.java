package com.shopmall.membership.infrastructure.web;

import com.shopmall.membership.application.port.in.SuspendMemberUseCase;
import com.shopmall.membership.domain.model.MemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
