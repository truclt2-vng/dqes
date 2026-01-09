
/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.aop.event;

import java.util.Collection;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.axonframework.eventhandling.GenericEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;


@Aspect
public class EventPublishingAspect {
	private static final Logger LOGGER = LoggerFactory.getLogger(EventPublishingAspect.class);

	@Autowired
	private AxonEventPublishingHelper axonEventPublishingHelper;

	@AfterReturning("@annotation(com.a4b.core.server.annotation.EventPublisher)")
	public void handle(JoinPoint joinPoint) {
		try {
			Object event = EventPublishingStore.getEvent();
			if (event != null) {
				doPushEvent(event);
			}
		} catch (Throwable ex) {
			LOGGER.error("Failed to publish business event", ex);
		}
		finally {
			EventPublishingStore.clear();
		}
	}

	@SuppressWarnings("rawtypes")
	private void doPushEvent(Object event) {
		if(event instanceof Collection) {
			for(Object item : (Collection)event) {
				LOGGER.debug("=== EventPublishingAspect === " + item.getClass().getName());
				axonEventPublishingHelper.publish(GenericEventMessage.asEventMessage(item));
			}
		}
		else {
			LOGGER.debug("=== EventPublishingAspect === " + event.getClass().getName());
			axonEventPublishingHelper.publish(GenericEventMessage.asEventMessage(event));
		}
	}
}
