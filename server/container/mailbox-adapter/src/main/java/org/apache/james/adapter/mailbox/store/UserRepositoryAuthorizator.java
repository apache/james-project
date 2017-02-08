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

package org.apache.james.adapter.mailbox.store;

import javax.inject.Inject;

import org.apache.james.lifecycle.api.LogEnabled;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;

/**
 * Authorizator which use an UsersRepository to check if the delegation is allowed
 */
public class UserRepositoryAuthorizator implements Authorizator, LogEnabled {

    private final UsersRepository repos;
    private Logger log;

    @Inject
    public UserRepositoryAuthorizator(UsersRepository repos) {
        this.repos = repos;
    }

    @Override
    public boolean canLoginAsOtherUser(String userId, String otherUserId) {
        try {
            return repos.isAdministrator(userId) && repos.contains(otherUserId);
        } catch (UsersRepositoryException e) {
            log.warn("Unable to access UsersRepository", e);
        }
        return false;
    }

    @Override
    public void setLog(Logger log) {
        this.log = log;
    }

}
