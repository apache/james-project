/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.domainlist.jpa;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;

import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.jpa.model.JPADomain;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * JPA implementation of the DomainList.<br>
 * This implementation is compatible with the JDBCDomainList, meaning same
 * database schema can be reused.
 */
public class JPADomainList extends AbstractDomainList {
    private static final Logger LOGGER = LoggerFactory.getLogger(JPADomainList.class);

    /**
     * The entity manager to access the database.
     */
    private EntityManagerFactory entityManagerFactory;

    @Inject
    public JPADomainList(DNSService dns, EntityManagerFactory entityManagerFactory) {
        super(dns);
        this.entityManagerFactory = entityManagerFactory;
    }

    /**
     * Set the entity manager to use.
     */
    @Inject
    @PersistenceUnit(unitName = "James")
    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @PostConstruct
    public void init() {
        EntityManagerUtils.safelyClose(createEntityManager());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected List<Domain> getDomainListInternal() throws DomainListException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            List<String> resultList = entityManager
                    .createNamedQuery("listDomainNames")
                    .getResultList();
            return resultList
                    .stream()
                    .map(Domain::of)
                    .collect(ImmutableList.toImmutableList());
        } catch (PersistenceException e) {
            LOGGER.error("Failed to list domains", e);
            throw new DomainListException("Unable to retrieve domains", e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) throws DomainListException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            return containsDomainInternal(domain, entityManager);
        } catch (PersistenceException e) {
            LOGGER.error("Failed to find domain", e);
            throw new DomainListException("Unable to retrieve domains", e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            if (containsDomainInternal(domain, entityManager)) {
                transaction.commit();
                throw new DomainListException(domain.name() + " already exists.");
            }
            JPADomain jpaDomain = new JPADomain(domain);
            entityManager.persist(jpaDomain);
            transaction.commit();
        } catch (PersistenceException e) {
            LOGGER.error("Failed to save domain", e);
            rollback(transaction);
            throw new DomainListException("Unable to add domain " + domain.name(), e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public void doRemoveDomain(Domain domain) throws DomainListException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            if (!containsDomainInternal(domain, entityManager)) {
                transaction.commit();
                throw new DomainListException(domain.name() + " was not found.");
            }
            entityManager.createNamedQuery("deleteDomainByName").setParameter("name", domain.asString()).executeUpdate();
            transaction.commit();
        } catch (PersistenceException e) {
            LOGGER.error("Failed to remove domain", e);
            rollback(transaction);
            throw new DomainListException("Unable to remove domain " + domain.name(), e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    private void rollback(EntityTransaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    private boolean containsDomainInternal(Domain domain, EntityManager entityManager) {
        try {
            return entityManager.createNamedQuery("findDomainByName")
                .setParameter("name", domain.asString())
                .getSingleResult() != null;
        } catch (NoResultException e) {
            LOGGER.debug("No domain found", e);
            return false;
        }
    }

    /**
     * Return a new {@link EntityManager} instance
     *
     * @return manager
     */
    private EntityManager createEntityManager() {
        return entityManagerFactory.createEntityManager();
    }

}
