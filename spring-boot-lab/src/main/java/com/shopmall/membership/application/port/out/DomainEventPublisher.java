package com.shopmall.membership.application.port.out;

public interface DomainEventPublisher {

    void publish(Object domainEvent);
}
