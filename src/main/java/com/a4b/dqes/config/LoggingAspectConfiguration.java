/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.config;

import com.a4b.xpservicelog.provided.SerLogTraceInfoProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.Environment;

import com.a4b.dqes.aop.logging.LoggingAspect;
import reactor.core.publisher.Hooks;

@Slf4j
@Configuration
@EnableAspectJAutoProxy
public class LoggingAspectConfiguration {

    @Bean
    // @Profile(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)
    public LoggingAspect loggingAspect(Environment env, SerLogTraceInfoProvider traceInfoProvider) {
        return new LoggingAspect(env, traceInfoProvider);
    }

    @PostConstruct
    public void init() {
        Hooks.enableAutomaticContextPropagation();// Enables automatic context propagation between threads/reactive flows
        log.info(">>> Hooks.enableAutomaticContextPropagation <<<");
    }
}
