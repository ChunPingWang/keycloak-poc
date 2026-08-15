package com.shopmall.membership.infrastructure.config;

import com.shopmall.membership.application.port.out.DomainEventPublisher;
import com.shopmall.membership.application.port.out.IdentityProvisioningPort;
import com.shopmall.membership.application.port.out.MemberRepository;
import com.shopmall.membership.application.service.EnrollMemberService;
import com.shopmall.membership.application.service.MemberProfileService;
import com.shopmall.membership.application.service.SuspendMemberService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 應用服務是純 Java 類別（無 @Service 註解），在基礎設施層組裝為 Bean。 */
@Configuration
public class MembershipUseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

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
