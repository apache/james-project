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

package org.apache.james.xodus;

import static org.apache.james.user.lib.model.Algorithm.HashingMode.PLAIN;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.UsersDAO;
import org.apache.james.user.lib.model.Algorithm;
import org.apache.james.user.lib.model.DefaultUser;

import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityIterable;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.StoreTransaction;

public class XodusUsersDAO implements UsersDAO, Configurable {
    static final String ENTITY_TYPE = "JamesUser";
    private static final String PROP_USERNAME = "username";
    private static final String PROP_PASSWORD = "password";
    private static final String PROP_ALGO = "algorithm";

    private final PersistentEntityStore entityStore;
    private Algorithm algo;

    @Inject
    public XodusUsersDAO(PersistentEntityStore entityStore) {
        this.entityStore = entityStore;
        this.algo = Algorithm.of("PBKDF2");
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) {
        algo = Algorithm.of(config.getString("algorithm", "PBKDF2"), config.getString("hashingMode", PLAIN.name()));
    }

    @Override
    public void addUser(Username username, String password) throws UsersRepositoryException {
        DefaultUser user = new DefaultUser(username, algo, algo);
        user.setPassword(password);

        try {
            entityStore.executeInTransaction(txn -> {
                Entity existing = findEntity(txn, username);
                if (existing != null) {
                    throw new RuntimeException(new AlreadyExistInUsersRepositoryException("User " + username.asString() + " already exists"));
                }
                Entity entity = txn.newEntity(ENTITY_TYPE);
                entity.setProperty(PROP_USERNAME, username.asString());
                entity.setProperty(PROP_PASSWORD, user.getHashedPassword());
                entity.setProperty(PROP_ALGO, user.getHashAlgorithm().asString());
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof UsersRepositoryException) {
                throw (UsersRepositoryException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    public Optional<User> getUserByName(Username name) throws UsersRepositoryException {
        return entityStore.computeInReadonlyTransaction(txn -> {
            Entity entity = findEntity(txn, name);
            if (entity == null) {
                return Optional.empty();
            }
            String password = (String) entity.getProperty(PROP_PASSWORD);
            String algoStr = (String) entity.getProperty(PROP_ALGO);
            Algorithm userAlgo = (algoStr != null) ? Algorithm.of(algoStr) : algo;
            return Optional.of(new DefaultUser(name, password, userAlgo, algo));
        });
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        if (!(user instanceof DefaultUser)) {
            throw new UsersRepositoryException("Unsupported user type: " + user.getClass());
        }
        DefaultUser defaultUser = (DefaultUser) user;
        Username username = user.getUserName();

        try {
            entityStore.executeInTransaction(txn -> {
                Entity entity = findEntity(txn, username);
                if (entity == null) {
                    throw new RuntimeException(new UsersRepositoryException("User " + username.asString() + " not found to update"));
                }
                entity.setProperty(PROP_PASSWORD, defaultUser.getHashedPassword());
                entity.setProperty(PROP_ALGO, defaultUser.getHashAlgorithm().asString());
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof UsersRepositoryException) {
                throw (UsersRepositoryException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        boolean removed = entityStore.computeInTransaction(txn -> {
            Entity entity = findEntity(txn, name);
            if (entity != null) {
                return entity.delete();
            }
            return false;
        });
        if (!removed) {
            throw new UsersRepositoryException("Unable to remove unknown user " + name.asString());
        }
    }

    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        return entityStore.computeInReadonlyTransaction(txn -> !txn.find(ENTITY_TYPE, PROP_USERNAME, name.asString()).isEmpty());
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        Long count = entityStore.computeInReadonlyTransaction(txn -> txn.getAll(ENTITY_TYPE).size());
        return count != null ? count.intValue() : 0;
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        List<Username> result = entityStore.computeInReadonlyTransaction(txn -> {
            List<Username> list = new ArrayList<>();
            for (Entity entity : txn.getAll(ENTITY_TYPE)) {
                String u = (String) entity.getProperty(PROP_USERNAME);
                if (u != null) {
                    list.add(Username.of(u));
                }
            }
            return list;
        });
        return result.iterator();
    }

    private Entity findEntity(StoreTransaction txn, Username username) {
        EntityIterable iter = txn.find(ENTITY_TYPE, PROP_USERNAME, username.asString());
        return iter.getFirst();
    }
}
