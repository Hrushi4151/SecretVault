package com.secretvault.events.handler;

import com.secretvault.automation.engine.AutomationEngine;
import com.secretvault.events.dispatcher.DomainEventHandler;
import com.secretvault.events.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AutomationDomainEventHandler implements DomainEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AutomationDomainEventHandler.class);

    private final AutomationEngine automationEngine;

    public AutomationDomainEventHandler(AutomationEngine automationEngine) {
        this.automationEngine = automationEngine;
    }

    @Override
    public String getConsumerName() {
        return "AutomationDomainEventHandler";
    }

    @Override
    public boolean supports(DomainEvent event) {
        return event != null;
    }

    @Override
    public void handle(DomainEvent event) {
        try {
            automationEngine.processEvent(event);
        } catch (Exception e) {
            log.error("Error executing automation policies for event {}: {}", event.getEventId(), e.getMessage(), e);
        }
    }
}
