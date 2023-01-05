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
import org.reactivestreams.Publisher;

public class CassandraDelegationStore implements DelegationStore {
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
        return cassandraUsersDAO.removeAllAuthorizedUsers(baseUser);
    }

    @Override
    public Publisher<Void> addAuthorizedUser(Username baseUser, Username userWithAccess) {
        return cassandraUsersDAO.addAuthorizedUsers(baseUser, userWithAccess);
    }

    @Override
    public Publisher<Void> removeAuthorizedUser(Username baseUser, Username userWithAccess) {
        return cassandraUsersDAO.removeAuthorizedUser(baseUser, userWithAccess);
    }

    @Override
    public Publisher<Username> delegatedUsers(Username baseUser) {
        return cassandraUsersDAO.getDelegatedToUsers(baseUser);
    }

    @Override
    public Publisher<Void> removeDelegatedUser(Username baseUser, Username delegatedToUser) {
        return cassandraUsersDAO.removeDelegatedToUser(baseUser, delegatedToUser);
    }
}
