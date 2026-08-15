package com.shopmall.membership.application.service.support;

import com.shopmall.membership.application.port.out.DomainEventPublisher;

import java.util.ArrayList;
import java.util.List;

public class RecordingEventPublisher implements DomainEventPublisher {

    private final List<Object> published = new ArrayList<>();

    @Override
    public void publish(Object domainEvent) {
        published.add(domainEvent);
    }

    public List<Object> published() {
        return published;
    }
}
