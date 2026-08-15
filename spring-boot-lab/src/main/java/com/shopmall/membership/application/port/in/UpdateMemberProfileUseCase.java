package com.shopmall.membership.application.port.in;

import com.shopmall.membership.domain.model.IdentityId;

public interface UpdateMemberProfileUseCase {

    void update(IdentityId identityId, String newName);
}
