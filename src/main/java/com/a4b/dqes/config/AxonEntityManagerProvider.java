/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.config;

import org.axonframework.common.jpa.EntityManagerProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;


public class AxonEntityManagerProvider implements EntityManagerProvider {

    private EntityManager entityManager;

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Sets the container managed entityManager to use. Is generally injected by the application container.
     *
     * @param entityManager the entityManager to return upon request.
     */
    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
}
