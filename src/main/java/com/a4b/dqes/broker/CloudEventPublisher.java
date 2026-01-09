/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.broker;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import com.a4b.core.server.json.JSON;
import com.a4b.dqes.config.AsyncConfiguration;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CloudEventPublisher {

	@Autowired
	private StreamBridge streamBridge;

	@Value("${spring.application.name}")
	private String applicationName;

	@Autowired
	@Qualifier(AsyncConfiguration.EVENT_PUBLISHER_BEAN_NAME)
	private ExecutorService eventPublisherExecutor;

	public void publish(Object event) {
		if (event == null) {
			return;
		}
		eventPublisherExecutor.execute(new Runnable() {

			@Override
			public void run() {
				CloudEventBuilder builder = CloudEventBuilder.v1().withId(UUID.randomUUID().toString())
						.withSource(URI.create("https://cloudevents.a4b.com/xplatform/" + applicationName))
						.withType(event.getClass().getName())
						.withData(JsonCloudEventData.wrap(JSON.getObjectMapper().valueToTree(event)));
				CloudEvent cloudEvent = builder.build();

				streamBridge.send(getBindingName(), JSON.writeValueAsStringNoException(cloudEvent));
			}
		});
	}

	private String getBindingName() {
		return applicationName + "-cloud-event-out-0";
	}
}
