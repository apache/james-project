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

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.jpa.model.JPAUser;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

/**
 * JPA based UserRepository
 */
public class JPAUsersRepository extends AbstractUsersRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JPAUsersRepository.class);

    private EntityManagerFactory entityManagerFactory;

    private String algo;

    @Inject
    public JPAUsersRepository(DomainList domainList) {
        super(domainList);
    }

    /**
     * Sets entity manager.
     * 
     * @param entityManagerFactory
     *            the entityManager to set
     */
    @Inject
    @PersistenceUnit(unitName = "James")
    public final void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @PostConstruct
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
    public User getUserByName(Username name) throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            return (JPAUser) entityManager.createNamedQuery("findUserByName").setParameter("name", name.asString()).getSingleResult();
        } catch (NoResultException e) {
            return null;
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
        assertDomainPartValid(user.getUserName());

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
        assertDomainPartValid(name);

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
     * Test if user with name 'name' has password 'password'.
     * 
     * @param name
     *            the name of the user to be tested
     * @param password
     *            the password to be tested
     * 
     * @return true if the test is successful, false if the user doesn't exist
     *         or if the password is incorrect
     * 
     * @since James 1.2.2
     */
    @Override
    public boolean test(Username name, String password) throws UsersRepositoryException {
        final User user = getUserByName(name);
        final boolean result;
        result = user != null && user.verifyPassword(password);
        return result;
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
                .collect(Guavate.toImmutableList()).iterator();

        } catch (PersistenceException e) {
            LOGGER.debug("Failed to find user", e);
            throw new UsersRepositoryException("Failed to list users", e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public void doConfigure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        algo = config.getString("algorithm", "MD5");
        super.doConfigure(config);
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
    protected void doAddUser(Username username, String password) throws UsersRepositoryException {
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
