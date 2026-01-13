/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.RequiredArgsConstructor;
import tech.jhipster.async.ExceptionHandlingAsyncTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
@Profile("!testdev & !testprod")
@RequiredArgsConstructor
public class AsyncConfiguration implements AsyncConfigurer {

    private final Logger log = LoggerFactory.getLogger(AsyncConfiguration.class);
    
	public static final String ASYNC_EXECUTOR_BEAN_NAME = "taskExecutor";
	public static final String WORKER_EXECUTOR_BEAN_NAME = "workerExecutor";
	public static final String ACTIVITY_STREAM_EXECUTOR_BEAN_NAME = "activityStreamExecutor";
    public static final String EVENT_PUBLISHER_BEAN_NAME = "eventPublisherExecutor";

    private final TaskExecutionProperties taskExecutionProperties;

    @Override
    @Bean(name = ASYNC_EXECUTOR_BEAN_NAME)
    public Executor getAsyncExecutor() {
        log.debug("Creating Async Task Executor");
        VirtualThreadTaskExecutor executor = new VirtualThreadTaskExecutor(taskExecutionProperties.getThreadNamePrefix());
		return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

	@Bean(name = WORKER_EXECUTOR_BEAN_NAME)
	public ExecutorService getWorkerExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	@Bean(name = ACTIVITY_STREAM_EXECUTOR_BEAN_NAME)
	public ExecutorService getActivityStreamExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

    @Bean(name = EVENT_PUBLISHER_BEAN_NAME)
	public ExecutorService eventPublisherExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	@Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("warmup-");
        ex.initialize();
        return ex;
    }
}
