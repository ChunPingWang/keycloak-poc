package com.shopmall.membership.infrastructure.event;

import com.shopmall.membership.application.port.out.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** DomainEventPublisher 的 Spring 實作：轉發給 ApplicationEventPublisher。 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher springPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(Object domainEvent) {
        springPublisher.publishEvent(domainEvent);
    }
}
