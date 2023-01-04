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

package org.apache.james.user.cassandra;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.util.FunctionalUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class CassandraDelegationStore implements DelegationStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraDelegationStore.class);
    private final CassandraUsersDAO cassandraUsersDAO;

    @Inject
    public CassandraDelegationStore(CassandraUsersDAO cassandraUsersDAO) {
        this.cassandraUsersDAO = cassandraUsersDAO;
    }

    @Override
    public Publisher<Username> authorizedUsers(Username baseUser) {
        return cassandraUsersDAO.getAuthorizedUsers(baseUser);
    }

    @Override
    public Publisher<Void> clear(Username baseUser) {
        return cassandraUsersDAO.getAuthorizedUsers(baseUser)
            .flatMap(authorizedUser -> cassandraUsersDAO.removeDelegatedToUser(authorizedUser, baseUser))
            .then(cassandraUsersDAO.removeAllAuthorizedUsers(baseUser));
    }

    @Override
    public Publisher<Void> addAuthorizedUser(Username baseUser, Username userWithAccess) {
        return cassandraUsersDAO.addAuthorizedUsers(baseUser, userWithAccess)
            .then(Mono.from(cassandraUsersDAO.containsReactive(userWithAccess))
                .filter(FunctionalUtils.identityPredicate())
                .flatMap(authorizedUser -> cassandraUsersDAO.addDelegatedToUsers(userWithAccess, baseUser))
                .onErrorResume(error -> {
                    LOGGER.warn("Can not add delegated user: {} to user: {}", userWithAccess, baseUser);
                    return Mono.empty();
                }));
    }

    @Override
    public Publisher<Void> removeAuthorizedUser(Username baseUser, Username userWithAccess) {
        return cassandraUsersDAO.removeAuthorizedUser(baseUser, userWithAccess)
            .then(cassandraUsersDAO.removeDelegatedToUser(userWithAccess, baseUser));
    }

    @Override
    public Publisher<Username> delegatedUsers(Username baseUser) {
        return cassandraUsersDAO.getDelegatedToUsers(baseUser);
    }
}
