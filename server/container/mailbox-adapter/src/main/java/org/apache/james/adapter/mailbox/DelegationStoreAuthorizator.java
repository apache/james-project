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

package org.apache.james.adapter.mailbox;

import java.util.Collection;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

import reactor.core.publisher.Flux;

/**
 * Authorizator which use an DelegationStore to check if the delegation is allowed
 */
public class DelegationStoreAuthorizator implements Authorizator {
    private final DelegationStore delegationStore;
    private final UsersRepository usersRepository;

    @Inject
    public DelegationStoreAuthorizator(DelegationStore delegationStore, UsersRepository usersRepository) {
        this.delegationStore = delegationStore;
        this.usersRepository = usersRepository;
    }

    @Override
    public AuthorizationState canLoginAsOtherUser(Username userId, Username otherUserId) throws MailboxException {
        boolean isAuthorized = Flux.from(delegationStore.authorizedUsers(otherUserId)).hasElement(userId).block();
        try {
            if (isAuthorized || isAdministrator(userId)) {
                return AuthorizationState.ALLOWED;
            }
            if (!usersRepository.contains(otherUserId)) {
                return AuthorizationState.UNKNOWN_USER;
            }
            return AuthorizationState.FORBIDDEN;
        } catch (UsersRepositoryException e) {
            throw new MailboxException("Unable to access usersRepository", e);
        }
    }

    private boolean isAdministrator(Username userId) throws UsersRepositoryException {
        if (userId.hasDomainPart() ^ usersRepository.supportVirtualHosting()) {
            return false;
        }
        return usersRepository.isAdministrator(userId);
    }

    @Override
    public Collection<Username> delegatedUsers(Username username) {
        return Flux.from(delegationStore.delegatedUsers(username)).collectList()
            .block();
    }
}
