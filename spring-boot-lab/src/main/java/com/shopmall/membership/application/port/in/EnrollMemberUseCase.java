package com.shopmall.membership.application.port.in;

import com.shopmall.membership.domain.model.MemberId;

public interface EnrollMemberUseCase {

    MemberId enroll(EnrollMemberCommand command);

    /** rawPassword 只以參數形式流經應用層，直達身分供裝 Port，絕不落地。 */
    record EnrollMemberCommand(String email, String name, String rawPassword) {}
}
