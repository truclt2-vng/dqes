/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.event.handler;

import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.a4b.core.server.event.ExportableEvent;
import com.a4b.dqes.broker.CloudEventPublisher;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ExportedEventHandler {

    @Autowired
    private CloudEventPublisher cloudEventPublisher;

    @EventHandler
    public void handle(ExportableEvent event) {
        cloudEventPublisher.publish(event);
    }
}
