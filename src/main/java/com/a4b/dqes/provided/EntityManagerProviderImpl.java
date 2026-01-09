/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.provided;

import org.springframework.stereotype.Component;

import com.a4b.core.server.itf.EntityManagerProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
public class EntityManagerProviderImpl implements EntityManagerProvider {

	@PersistenceContext
	private EntityManager appEntityManager;

	@Override
	public EntityManager getSecurityEntityManager() {
		return null;
	}

	@Override
	public EntityManager getSecurityXAEntityManager() {
		return null;
	}

	@Override
	public EntityManager getAppEntityManager() {
		return appEntityManager;
	}

	@Override
	public EntityManager getAppXAEntityManager() {
		return null;
	}

}
