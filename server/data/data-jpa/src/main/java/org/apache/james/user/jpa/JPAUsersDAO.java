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

package org.apache.james.user.jpa;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.core.Username;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.jpa.model.JPAUser;
import org.apache.james.user.lib.UsersDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * JPA based UserRepository
 */
public class JPAUsersDAO implements UsersDAO, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JPAUsersDAO.class);

    private EntityManagerFactory entityManagerFactory;
    private String algo;

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) {
        algo = config.getString("algorithm", "PBKDF2");
    }

    /**
     * Sets entity manager.
     * 
     * @param entityManagerFactory
     *            the entityManager to set
     */
    public final void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public void init() {
        EntityManagerUtils.safelyClose(createEntityManager());
    }

    /**
     * Get the user object with the specified user name. Return null if no such
     * user.
     * 
     * @param name
     *            the name of the user to retrieve
     * @return the user being retrieved, null if the user doesn't exist
     * 
     * @since James 1.2.2
     */
    @Override
    public Optional<User> getUserByName(Username name) throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            JPAUser singleResult = (JPAUser) entityManager
                .createNamedQuery("findUserByName")
                .setParameter("name", name.asString())
                .getSingleResult();
            return Optional.of(singleResult);
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to find user", e);
            throw new UsersRepositoryException("Unable to search user", e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    /**
     * Update the repository with the specified user object. A user object with
     * this username must already exist.
     */
    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        Preconditions.checkNotNull(user);

        EntityManager entityManager = entityManagerFactory.createEntityManager();

        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            if (contains(user.getUserName())) {
                transaction.begin();
                entityManager.merge(user);
                transaction.commit();
            } else {
                LOGGER.debug("User not found");
                throw new UsersRepositoryException("User " + user.getUserName() + " not found");
            }
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to update user", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new UsersRepositoryException("Failed to update user " + user.getUserName().asString(), e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    /**
     * Removes a user from the repository
     * 
     * @param name
     *            the user to remove from the repository
     */
    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            if (entityManager.createNamedQuery("deleteUserByName").setParameter("name", name.asString()).executeUpdate() < 1) {
                transaction.commit();
                throw new UsersRepositoryException("User " + name.asString() + " does not exist");
            } else {
                transaction.commit();
            }
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to remove user", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new UsersRepositoryException("Failed to remove user " + name.asString(), e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    /**
     * Returns whether or not this user is in the repository
     * 
     * @param name
     *            the name to check in the repository
     * @return whether the user is in the repository
     */
    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            return (Long) entityManager.createNamedQuery("containsUser")
                .setParameter("name", name.asString().toLowerCase(Locale.US))
                .getSingleResult() > 0;
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to find user", e);
            throw new UsersRepositoryException("Failed to find user" + name.asString(), e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    /**
     * Returns a count of the users in the repository.
     * 
     * @return the number of users in the repository
     */
    @Override
    public int countUsers() throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            return ((Long) entityManager.createNamedQuery("countUsers").getSingleResult()).intValue();
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to find user", e);
            throw new UsersRepositoryException("Failed to count users", e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    /**
     * List users in repository.
     * 
     * @return Iterator over a collection of Strings, each being one user in the
     *         repository.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Username> list() throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            return ((List<String>) entityManager.createNamedQuery("listUserNames").getResultList())
                .stream()
                .map(Username::of)
                .collect(ImmutableList.toImmutableList()).iterator();

        } catch (PersistenceException e) {
            LOGGER.debug("Failed to find user", e);
            throw new UsersRepositoryException("Failed to list users", e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
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

    @Override
    public void addUser(Username username, String password) throws UsersRepositoryException {
        Username lowerCasedUsername = Username.of(username.asString().toLowerCase(Locale.US));
        if (contains(lowerCasedUsername)) {
            throw new UsersRepositoryException(lowerCasedUsername.asString() + " already exists.");
        }
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            JPAUser user = new JPAUser(lowerCasedUsername.asString(), password, algo);
            entityManager.persist(user);
            transaction.commit();
        } catch (PersistenceException e) {
            LOGGER.debug("Failed to save user", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new UsersRepositoryException("Failed to add user" + username.asString(), e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

}
