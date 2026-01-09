/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.config;

import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.a4b.xpactstreams.config.ActivityStreamAutoConfiguration;
import com.a4b.xpactstreams.config.DefaultExecutorServiceProvider;
import com.a4b.xpactstreams.config.ExecutorServiceProvider;
import com.a4b.xpactstreams.provided.DefaultTraceInfoProviderImpl;
import com.a4b.xpactstreams.provided.TraceInfoProvider;

@Configuration
@AutoConfigureAfter(value = AsyncConfiguration.class)
@AutoConfigureBefore(value = ActivityStreamAutoConfiguration.class)
public class ActivityStreamConfiguration {

	@Bean
	public ExecutorServiceProvider activityStreamExecutorServiceProvider(
			@Qualifier(AsyncConfiguration.ACTIVITY_STREAM_EXECUTOR_BEAN_NAME) ExecutorService activityStreamExecutor) {
		return new DefaultExecutorServiceProvider(activityStreamExecutor);
	}
	
	@Bean
	public TraceInfoProvider traceInfoProvider() {
		return new DefaultTraceInfoProviderImpl();
	}
}
