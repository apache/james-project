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

import java.util.Collections;
import java.util.Iterator;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.jpa.model.JPAUser;
import org.apache.james.user.lib.AbstractUsersRepository;

/**
 * JPA based UserRepository
 */
public class JPAUsersRepository extends AbstractUsersRepository {

    private EntityManagerFactory entityManagerFactory;

    private String algo;

    /**
     * Sets entity manager.
     * 
     * @param entityManagerFactory
     *            the entityManager to set
     */
    @PersistenceUnit(unitName="James")
    public final void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @PostConstruct
    public void init() {
        createEntityManager().close();
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
    public User getUserByName(String name) throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            return (JPAUser) entityManager.createNamedQuery("findUserByName").setParameter("name", name).getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (PersistenceException e) {
            getLogger().debug("Failed to find user", e);
            throw new UsersRepositoryException("Unable to search user", e);
        } finally {
            entityManager.close();
        }
    }

    /**
     * Returns the user name of the user matching name on an equalsIgnoreCase
     * basis. Returns null if no match.
     * 
     * @param name
     *            the name to case-correct
     * @return the case-correct name of the user, null if the user doesn't exist
     * @throws UsersRepositoryException
     */
    public String getRealName(String name) throws UsersRepositoryException {
        User u = getUserByName(name);
        if (u != null) {
            u.getUserName();
        }
        return null;
    }

    /**
     * Update the repository with the specified user object. A user object with
     * this username must already exist.
     * 
     * @throws UsersRepositoryException
     */
    public void updateUser(User user) throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            if (contains(user.getUserName())) {
                transaction.begin();
                entityManager.merge(user);
                transaction.commit();
            } else {
                getLogger().debug("User not found");
                throw new UsersRepositoryException("User " + user.getUserName() + " not found");
            }
        } catch (PersistenceException e) {
            getLogger().debug("Failed to update user", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new UsersRepositoryException("Failed to update user " + user.getUserName(), e);
        } finally {
            entityManager.close();
        }
    }

    /**
     * Removes a user from the repository
     * 
     * @param name
     *            the user to remove from the repository
     * @throws UsersRepositoryException
     */
    public void removeUser(String name) throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            if (entityManager.createNamedQuery("deleteUserByName").setParameter("name", name).executeUpdate() < 1) {
                transaction.commit();
                throw new UsersRepositoryException("User " + name + " does not exist");
            } else {
                transaction.commit();
            }
        } catch (PersistenceException e) {
            getLogger().debug("Failed to remove user", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new UsersRepositoryException("Failed to remove user " + name, e);
        } finally {
            entityManager.close();
        }
    }

    /**
     * Returns whether or not this user is in the repository
     * 
     * @param name
     *            the name to check in the repository
     * @return whether the user is in the repository
     * @throws UsersRepositoryException
     */
    public boolean contains(String name) throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            return (Long) entityManager.createNamedQuery("containsUser").setParameter("name", name.toLowerCase()).getSingleResult() > 0;
        } catch (PersistenceException e) {
            getLogger().debug("Failed to find user", e);
            throw new UsersRepositoryException("Failed to find user" + name, e);
        } finally {
            entityManager.close();
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
    public boolean test(String name, String password) throws UsersRepositoryException {
        final User user = getUserByName(name);
        final boolean result;
        result = user != null && user.verifyPassword(password);
        return result;
    }

    /**
     * Returns a count of the users in the repository.
     * 
     * @return the number of users in the repository
     * @throws UsersRepositoryException
     */
    public int countUsers() throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            return ((Long) entityManager.createNamedQuery("countUsers").getSingleResult()).intValue();
        } catch (PersistenceException e) {
            getLogger().debug("Failed to find user", e);
            throw new UsersRepositoryException("Failed to count users", e);
        } finally {
            entityManager.close();
        }
    }

    /**
     * List users in repository.
     * 
     * @return Iterator over a collection of Strings, each being one user in the
     *         repository.
     * @throws UsersRepositoryException
     */
    @SuppressWarnings("unchecked")
    public Iterator<String> list() throws UsersRepositoryException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            return Collections.unmodifiableList(entityManager.createNamedQuery("listUserNames").getResultList()).iterator();

        } catch (PersistenceException e) {
            getLogger().debug("Failed to find user", e);
            throw new UsersRepositoryException("Failed to list users", e);
        } finally {
            entityManager.close();
        }
    }

    /**
     * @see
     * org.apache.james.user.lib.AbstractUsersRepository#doConfigure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    public void doConfigure(HierarchicalConfiguration config) throws ConfigurationException {
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

    /**
     * @see
     * org.apache.james.user.lib.AbstractUsersRepository#doAddUser(java.lang.String, java.lang.String)
     */
    protected void doAddUser(String username, String password) throws UsersRepositoryException {
        String lowerCasedUsername = username.toLowerCase();
        if (contains(lowerCasedUsername)) {
            throw new UsersRepositoryException(lowerCasedUsername + " already exists.");
        }
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            JPAUser user = new JPAUser(lowerCasedUsername, password, algo);
            entityManager.persist(user);
            transaction.commit();
        } catch (PersistenceException e) {
            getLogger().debug("Failed to save user", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new UsersRepositoryException("Failed to add user" + username, e);
        } finally {
            entityManager.close();
        }
    }

}
