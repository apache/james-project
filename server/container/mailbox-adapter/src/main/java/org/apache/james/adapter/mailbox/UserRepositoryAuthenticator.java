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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

/**
 * Authenticator which use an UsersRepository to check if the user and password
 * match
 */
public class UserRepositoryAuthenticator implements Authenticator {

    private final UsersRepository repos;

    @Inject
    public UserRepositoryAuthenticator(UsersRepository repos) {
        this.repos = repos;
    }

    @Override
    public Optional<Username> isAuthentic(Username userid, CharSequence passwd) throws MailboxException {
        try {
            return repos.test(userid, passwd.toString());
        } catch (UsersRepositoryException e) {
            throw new MailboxException("Unable to access UsersRepository", e);
        }
    }

}
