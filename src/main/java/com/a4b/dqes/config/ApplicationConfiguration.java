/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.a4b.core.server.ApplicationContextProvider;

@Configuration
public class ApplicationConfiguration {
    
	@Bean
	public ApplicationContextProvider applicationContextProvider() {
		return new ApplicationContextProvider();
	}   
}
