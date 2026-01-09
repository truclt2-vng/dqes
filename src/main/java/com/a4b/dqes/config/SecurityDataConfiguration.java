/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.a4b.core.ezmt.client.dataconstraint.CheckDataConstraintInterceptor;
import com.a4b.core.ezmt.client.dataconstraint.DataConstraintProvider;
import com.a4b.core.ezmt.client.dataconstraint.DataConstraintProviderImpl;
import com.a4b.core.ezmt.client.dataconstraint.itf.DataConstraintsInitiator;
import com.a4b.core.ezmt.client.dataconstraint.itf.MethodCheckDataConstraintService;
import com.a4b.core.ezmt.client.dataconstraint.itf.MethodDataConstraintInfoProvider;
import com.a4b.core.ezmt.client.dataconstraint.prov.AccessAttributeDataQuery;
import com.a4b.core.ezmt.client.dataconstraint.prov.AccessAttributeDataQueryImpl;
import com.a4b.core.ezmt.client.dataconstraint.prov.AccessLevelSearcherImpl;
import com.a4b.core.ezmt.client.dataconstraint.prov.MethodCheckDataConstraintServiceImpl;
import com.a4b.core.ezmt.client.dataconstraint.prov.MethodDataConstraintInfoProviderImpl;
import com.a4b.core.ezmt.client.dataconstraint.sec.AccessLevelSearcher;
import com.a4b.core.server.itf.EntityManagerProvider;
import com.a4b.dqes.provided.DefaultDataConstraintsInitiatorImpl;



@Configuration
@EnableAspectJAutoProxy
public class SecurityDataConfiguration {
	
	@Bean
	public DataConstraintsInitiator dataConstraintsInitiator() {
		DataConstraintsInitiator dataConstraintsInitiator = new DefaultDataConstraintsInitiatorImpl();
		return dataConstraintsInitiator;
	}

	@Bean
	public AccessLevelSearcher accessLevelSearcher() {
		AccessLevelSearcherImpl accessLevelSearcher = new AccessLevelSearcherImpl();
		return accessLevelSearcher;
	}

	@Bean
	public AccessAttributeDataQuery accessAttributeDataQuery(EntityManagerProvider entityManagerProvider) {
		AccessAttributeDataQueryImpl accessAttributeDataQuery = new AccessAttributeDataQueryImpl();
		accessAttributeDataQuery.setEntityManagerProvider(entityManagerProvider);
		return accessAttributeDataQuery;
	}

	@Bean
	public DataConstraintProvider dataConstraintProvider(AccessAttributeDataQuery accessAttributeDataQuery,
			AccessLevelSearcher accessLevelSearcher) {
		DataConstraintProvider dataConstraintProvider = new DataConstraintProviderImpl(accessAttributeDataQuery,
				accessLevelSearcher);
		return dataConstraintProvider;
	}
	
	@Bean
	public CheckDataConstraintInterceptor checkDataConstraintInterceptor() {
		return new CheckDataConstraintInterceptor();
	}
	
	@Bean
	public MethodCheckDataConstraintService methodCheckDataConstraintService() {
		return new MethodCheckDataConstraintServiceImpl();
	}
	
	@Bean
	public MethodDataConstraintInfoProvider methodDataConstraintInfoProvider() {
		return new MethodDataConstraintInfoProviderImpl();
	}
}
