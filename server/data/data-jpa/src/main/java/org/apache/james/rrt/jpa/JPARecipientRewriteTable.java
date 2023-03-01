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

import static org.apache.james.rrt.jpa.model.JPARecipientRewrite.DELETE_MAPPING_QUERY;
import static org.apache.james.rrt.jpa.model.JPARecipientRewrite.SELECT_ALL_MAPPINGS_QUERY;
import static org.apache.james.rrt.jpa.model.JPARecipientRewrite.SELECT_SOURCES_BY_MAPPING_QUERY;
import static org.apache.james.rrt.jpa.model.JPARecipientRewrite.SELECT_USER_DOMAIN_MAPPING_QUERY;
import static org.apache.james.rrt.jpa.model.JPARecipientRewrite.UPDATE_MAPPING_QUERY;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;

import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.jpa.model.JPARecipientRewrite;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

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
     */
    @Inject
    @PersistenceUnit(unitName = "James")
    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void addMapping(MappingSource source, Mapping mapping) throws RecipientRewriteTableException {
        Mappings map = getStoredMappings(source);
        if (!map.isEmpty()) {
            Mappings updatedMappings = MappingsImpl.from(map).add(mapping).build();
            doUpdateMapping(source, updatedMappings.serialize());
        } else {
            doAddMapping(source, mapping.asString());
        }
    }

    @Override
    protected Mappings mapAddress(String user, Domain domain) throws RecipientRewriteTableException {
        Mappings userDomainMapping = getStoredMappings(MappingSource.fromUser(user, domain));
        if (userDomainMapping != null && !userDomainMapping.isEmpty()) {
            return userDomainMapping;
        }
        Mappings domainMapping = getStoredMappings(MappingSource.fromDomain(domain));
        if (domainMapping != null && !domainMapping.isEmpty()) {
            return domainMapping;
        }
        return MappingsImpl.empty();
    }

    @Override
    public Mappings getStoredMappings(MappingSource source) throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            @SuppressWarnings("unchecked")
            List<JPARecipientRewrite> virtualUsers = entityManager.createNamedQuery(SELECT_USER_DOMAIN_MAPPING_QUERY)
                .setParameter("user", source.getFixedUser())
                .setParameter("domain", source.getFixedDomain())
                .getResultList();
            if (virtualUsers.size() > 0) {
                return MappingsImpl.fromRawString(virtualUsers.get(0).getTargetAddress());
            }
            return MappingsImpl.empty();
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to get user domain mappings", e);
            throw new RecipientRewriteTableException("Error while retrieve mappings", e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public Map<MappingSource, Mappings> getAllMappings() throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        Map<MappingSource, Mappings> mapping = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            List<JPARecipientRewrite> virtualUsers = entityManager.createNamedQuery(SELECT_ALL_MAPPINGS_QUERY).getResultList();
            for (JPARecipientRewrite virtualUser : virtualUsers) {
                mapping.put(MappingSource.fromUser(virtualUser.getUser(), virtualUser.getDomain()), MappingsImpl.fromRawString(virtualUser.getTargetAddress()));
            }
            return mapping;
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to get all mappings", e);
            throw new RecipientRewriteTableException("Error while retrieve mappings", e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public Stream<MappingSource> listSources(Mapping mapping) throws RecipientRewriteTableException {
        Preconditions.checkArgument(listSourcesSupportedType.contains(mapping.getType()),
                "Not supported mapping of type %s", mapping.getType());

        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            return entityManager.createNamedQuery(SELECT_SOURCES_BY_MAPPING_QUERY, JPARecipientRewrite.class)
                .setParameter("targetAddress", mapping.asString())
                .getResultList()
                .stream()
                .map(user -> MappingSource.fromUser(user.getUser(), user.getDomain()));
        } catch (PersistenceException e) {
             String error = "Unable to list sources by mapping";
             LOGGER.debug(error, e);
            throw new RecipientRewriteTableException(error, e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public void removeMapping(MappingSource source, Mapping mapping) throws RecipientRewriteTableException {
        Mappings map = getStoredMappings(source);
        if (map.size() > 1) {
            Mappings updatedMappings = map.remove(mapping);
            doUpdateMapping(source, updatedMappings.serialize());
        } else {
            doRemoveMapping(source, mapping.asString());
        }
    }

    /**
     * Update the mapping for the given user and domain
     *
     * @return true if update was successfully
     */
    private boolean doUpdateMapping(MappingSource source, String mapping) throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            int updated = entityManager
                .createNamedQuery(UPDATE_MAPPING_QUERY)
                .setParameter("targetAddress", mapping)
                .setParameter("user", source.getFixedUser())
                .setParameter("domain", source.getFixedDomain())
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
            EntityManagerUtils.safelyClose(entityManager);
        }
        return false;
    }

    /**
     * Remove a mapping for the given user and domain
     */
    private void doRemoveMapping(MappingSource source, String mapping) throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            entityManager.createNamedQuery(DELETE_MAPPING_QUERY)
                .setParameter("user", source.getFixedUser())
                .setParameter("domain", source.getFixedDomain())
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
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    /**
     * Add mapping for given user and domain
     */
    private void doAddMapping(MappingSource source, String mapping) throws RecipientRewriteTableException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            JPARecipientRewrite jpaRecipientRewrite = new JPARecipientRewrite(source.getFixedUser(), Domain.of(source.getFixedDomain()), mapping);
            entityManager.persist(jpaRecipientRewrite);
            transaction.commit();
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to save virtual user", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RecipientRewriteTableException("Unable to add mapping", e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

}
