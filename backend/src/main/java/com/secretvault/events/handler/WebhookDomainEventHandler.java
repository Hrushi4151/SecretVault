package com.secretvault.events.handler;

import com.secretvault.events.dispatcher.DomainEventHandler;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.webhook.service.WebhookDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WebhookDomainEventHandler implements DomainEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WebhookDomainEventHandler.class);
    private static final String CONSUMER_NAME = "WebhookDomainEventHandler";

    private final WebhookDeliveryService webhookDeliveryService;

    public WebhookDomainEventHandler(WebhookDeliveryService webhookDeliveryService) {
        this.webhookDeliveryService = webhookDeliveryService;
    }

    @Override
    public String getConsumerName() {
        return CONSUMER_NAME;
    }

    @Override
    public void handle(DomainEvent event) {
        log.debug("Webhook handler enqueuing deliveries for event [{}]", event.getEventId());
        webhookDeliveryService.enqueueDeliveriesForEvent(event);
    }
}
