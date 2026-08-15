package com.shopmall.membership.application.service.support;

import com.shopmall.membership.application.port.out.IdentityProvisioningPort;
import com.shopmall.membership.domain.exception.DuplicateIdentityException;
import com.shopmall.membership.domain.exception.IdentityNotFoundException;
import com.shopmall.membership.domain.model.Email;
import com.shopmall.membership.domain.model.IdentityId;
import com.shopmall.membership.domain.model.MemberName;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** IdentityProvisioningPort 的記憶體實作：遵守與 Keycloak Adapter 相同的行為契約（LSP）。 */
public class InMemoryIdentityProvisioning implements IdentityProvisioningPort {

    public final Map<String, Email> provisioned = new HashMap<>();
    public final Set<String> disabled = new HashSet<>();
    private int seq = 0;

    @Override
    public IdentityId provision(Email email, MemberName name, RawPassword password) {
        if (provisioned.containsValue(email)) {
            throw new DuplicateIdentityException(email);
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
