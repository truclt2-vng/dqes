/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.web.rest;

import java.io.Serializable;
import java.security.Principal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import com.a4b.dqes.broker.KafkaConsumer;
import com.a4b.dqes.security.SecurityUtils;
import com.a4b.xpservicelog.provided.SerLogTraceInfoProvider;
import com.a4b.xpservicelog.sentmessage.TracingStreamBridge;

import lombok.Getter;
import lombok.Setter;

@RestController
@RequestMapping("/api/dqes-kafka")
public class DqesKafkaResource {

    private static final String PRODUCER_BINDING_NAME = "binding-out-0";

    private final Logger log = LoggerFactory.getLogger(DqesKafkaResource.class);
    private final KafkaConsumer kafkaConsumer;
    private final StreamBridge streamBridge;
    private final TracingStreamBridge tracingStreamBridge;
    private final SerLogTraceInfoProvider traceInfoProvider;

    @Value("${spring.cloud.stream.bindings.demoMessageConsumer-in-0.destination}")
    private String demoInputTopic;

    public DqesKafkaResource(StreamBridge streamBridge, KafkaConsumer kafkaConsumer,
                                   TracingStreamBridge tracingStreamBridge,
                                   SerLogTraceInfoProvider traceInfoProvider) {
        this.streamBridge = streamBridge;
        this.kafkaConsumer = kafkaConsumer;
        this.tracingStreamBridge = tracingStreamBridge;
        this.traceInfoProvider = traceInfoProvider;
    }

    @PostMapping("/publish")
    public void publish(@RequestParam("message") String message) {
        log.debug("REST request the message : {} to send to Kafka topic ", message);
        streamBridge.send(PRODUCER_BINDING_NAME, message);
    }

    @GetMapping("/register")
    public ResponseBodyEmitter register(Principal principal) {
        return kafkaConsumer.register(principal.getName());
    }

    @GetMapping("/unregister")
    public void unregister(Principal principal) {
        kafkaConsumer.unregister(principal.getName());
    }



    @PostMapping("/tracing/send")
    public void sendMessage(@RequestBody Map<String, Object> message) {
        var tenantCode = SecurityUtils.getCurrentUserTenantCode();
        var appCode = SecurityUtils.getCurrentAppCode();
        var currentUserName = SecurityUtils.getCurrentUserName();
        tracingStreamBridge.send(currentUserName, tenantCode, appCode, demoInputTopic, message, Map.of());
    }


    @Setter
    @Getter
    public static class ConsumeDemoDto implements Serializable {
        private Map<String, Object> data;
    }
}
