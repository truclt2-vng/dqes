
/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.aop.event;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
public class AxonEventPublishingHelper {

	@Autowired
	private EventBus eventBus;

	@Transactional
	public void publish(EventMessage<?>... events) {
		eventBus.publish(events);
	}
}
