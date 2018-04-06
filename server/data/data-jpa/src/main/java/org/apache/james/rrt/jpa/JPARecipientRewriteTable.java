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
package org.apache.james.rrt.jpa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;

import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.jpa.model.JPARecipientRewrite;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible to implement the Virtual User Table in database with JPA
 * access.
 */
public class JPARecipientRewriteTable extends AbstractRecipientRewriteTable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JPARecipientRewriteTable.class);

    /**
     * The entity manager to access the database.
     */
    private EntityManagerFactory entityManagerFactory;

    /**
     * Set the entity manager to use.
     * 
     * @param entityManagerFactory
     */
    @Inject
    @PersistenceUnit(unitName = "James")
    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    protected void addMappingInternal(String user, Domain domain, Mapping mapping) throws RecipientRewriteTableException {
        String fixedUser = getFixedUser(user);
        Domain fixedDomain = getFixedDomain(domain);
        Mappings map = getUserDomainMappings(fixedUser, fixedDomain);
        if (map != null && map.size() != 0) {
            Mappings updatedMappings = MappingsImpl.from(map).add(mapping).build();
            doUpdateMapping(fixedUser, fixedDomain, updatedMappings.serialize());
        } else {
            doAddMapping(fixedUser, fixedDomain, mapping.asString());
        }
    }

    @Override
    protected Mappings mapAddress(String user, Domain domain) throws RecipientRewriteTableException {
        Mappings mapping = getMapping(user, domain, "selectExactMappings");
        if (mapping != null) {
            return mapping;
        }
        return getMapping(user, domain, "selectMappings");
    }

    private Mappings getMapping(String user, Domain domain, String queryName) throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            @SuppressWarnings("unchecked")
            List<JPARecipientRewrite> virtualUsers = entityManager
                .createNamedQuery(queryName)
                .setParameter("user", user)
                .setParameter("domain", domain.asString())
                .getResultList();
            transaction.commit();
            if (virtualUsers.size() > 0) {
                return MappingsImpl.fromRawString(virtualUsers.get(0).getTargetAddress());
            }
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to find mapping for  user={} and domain={}", user, domain, e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RecipientRewriteTableException("Error while retrieve mappings", e);
        } finally {
            entityManager.close();
        }
        return MappingsImpl.empty();
    }

    @Override
    public Mappings getUserDomainMappings(String user, Domain domain) throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            @SuppressWarnings("unchecked")
            List<JPARecipientRewrite> virtualUsers = entityManager.createNamedQuery("selectUserDomainMapping")
                .setParameter("user", user)
                .setParameter("domain", domain.asString())
                .getResultList();
            transaction.commit();
            if (virtualUsers.size() > 0) {
                return MappingsImpl.fromRawString(virtualUsers.get(0).getTargetAddress());
            }
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to get user domain mappings", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RecipientRewriteTableException("Error while retrieve mappings", e);

        } finally {
            entityManager.close();
        }
        return null;
    }

    @Override
    protected Map<String, Mappings> getAllMappingsInternal() throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        Map<String, Mappings> mapping = new HashMap<>();
        try {
            transaction.begin();
            @SuppressWarnings("unchecked")
            List<JPARecipientRewrite> virtualUsers = entityManager.createNamedQuery("selectAllMappings").getResultList();
            transaction.commit();
            for (JPARecipientRewrite virtualUser : virtualUsers) {
                mapping.put(virtualUser.getUser() + "@" + virtualUser.getDomain(), MappingsImpl.fromRawString(virtualUser.getTargetAddress()));
            }
            if (mapping.size() > 0) {
                return mapping;
            }
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to get all mappings", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RecipientRewriteTableException("Error while retrieve mappings", e);

        } finally {
            entityManager.close();
        }
        return null;
    }

    @Override
    protected void removeMappingInternal(String user, Domain domain, Mapping mapping) throws RecipientRewriteTableException {
        String fixedUser = getFixedUser(user);
        Domain fixedDomain = getFixedDomain(domain);
        Mappings map = getUserDomainMappings(fixedUser, fixedDomain);
        if (map != null && map.size() > 1) {
            Mappings updatedMappings = map.remove(mapping);
            doUpdateMapping(fixedUser, fixedDomain, updatedMappings.serialize());
        } else {
            doRemoveMapping(fixedUser, fixedDomain, mapping.asString());
        }
    }

    /**
     * Update the mapping for the given user and domain
     * 
     * @param user the user
     * @param domain the domain
     * @param mapping the mapping
     * @return true if update was successfully
     * @throws RecipientRewriteTableException
     */
    private boolean doUpdateMapping(String user, Domain domain, String mapping) throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            int updated = entityManager
                .createNamedQuery("updateMapping")
                .setParameter("targetAddress", mapping)
                .setParameter("user", user)
                .setParameter("domain", domain.asString())
                .executeUpdate();
            transaction.commit();
            if (updated > 0) {
                return true;
            }
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to update mapping", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RecipientRewriteTableException("Unable to update mapping", e);
        } finally {
            entityManager.close();
        }
        return false;
    }

    /**
     * Remove a mapping for the given user and domain
     * 
     * @param user the user
     * @param domain the domain
     * @param mapping the mapping
     * @throws RecipientRewriteTableException
     */
    private void doRemoveMapping(String user, Domain domain, String mapping) throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            entityManager.createNamedQuery("deleteMapping")
                .setParameter("user", user)
                .setParameter("domain", domain.asString())
                .setParameter("targetAddress", mapping)
                .executeUpdate();
            transaction.commit();

        } catch (PersistenceException e) {
            LOGGER.debug("Failed to remove mapping", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RecipientRewriteTableException("Unable to remove mapping", e);

        } finally {
            entityManager.close();
        }
    }

    /**
     * Add mapping for given user and domain
     * 
     * @param user the user
     * @param domain the domain
     * @param mapping the mapping
     * @throws RecipientRewriteTableException
     */
    private void doAddMapping(String user, Domain domain, String mapping) throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            JPARecipientRewrite jpaRecipientRewrite = new JPARecipientRewrite(user, domain, mapping);
            entityManager.persist(jpaRecipientRewrite);
            transaction.commit();
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to save virtual user", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RecipientRewriteTableException("Unable to add mapping", e);
        } finally {
            entityManager.close();
        }
    }

}
