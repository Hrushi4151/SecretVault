package com.secretvault.events.handler;

import com.secretvault.events.dispatcher.DomainEventHandler;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.incident.service.SecurityIncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IncidentDomainEventHandler implements DomainEventHandler {

    private static final Logger log = LoggerFactory.getLogger(IncidentDomainEventHandler.class);

    private final SecurityIncidentService incidentService;

    public IncidentDomainEventHandler(SecurityIncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @Override
    public String getConsumerName() {
        return "IncidentDomainEventHandler";
    }

    @Override
    public boolean supports(DomainEvent event) {
        return event != null;
    }

    @Override
    public void handle(DomainEvent event) {
        try {
            incidentService.processDomainEventCorrelation(event);
        } catch (Exception e) {
            log.error("Failed to process security incident correlation for event {}: {}", event.eventId(), e.getMessage(), e);
        }
    }
}
