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
package org.apache.james.smtpserver;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.AuthHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Auth hook can be used to authenticate against the james user repository
 */
public class UsersRepositoryAuthHook implements AuthHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(UsersRepositoryAuthHook.class);

    private UsersRepository users;

    /**
     * Gets the users repository.
     * 
     * @return the users
     */
    public final UsersRepository getUsers() {
        return users;
    }

    /**
     * Sets the users repository.
     * 
     * @param users
     *            the users to set
     */
    @Inject
    public final void setUsersRepository(UsersRepository users) {
        this.users = users;
    }

    @Override
    public HookResult doAuth(SMTPSession session, String username, String password) {
        try {
            if (users.test(username, password)) {
                session.setUser(username);
                session.setRelayingAllowed(true);
                return new HookResult(HookReturnCode.ok(), "Authentication Successful");
            }
        } catch (UsersRepositoryException e) {
            LOGGER.info("Unable to access UsersRepository", e);
        }
        return HookResult.DECLINED;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }
}
