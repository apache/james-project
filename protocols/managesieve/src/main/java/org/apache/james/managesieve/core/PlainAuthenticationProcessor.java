/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.managesieve.core;

import java.util.Iterator;

import org.apache.james.managesieve.api.AuthenticationException;
import org.apache.james.managesieve.api.AuthenticationProcessor;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;

import com.google.common.base.Splitter;

/**
 * See RFC-4616 : https://tools.ietf.org/html/rfc4616
 *
 * Only differences is that ManageSieve does not handle tags. See https://tools.ietf.org/html/rfc5804#section-2.1 for details
 */
public class PlainAuthenticationProcessor implements AuthenticationProcessor {

    private final UsersRepository usersRepository;

    public PlainAuthenticationProcessor(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public String initialServerResponse(Session session) {
        return "+ \"\"";
    }


    @Override
    public String isAuthenticationSuccesfull(Session session, String suppliedClientData) throws SyntaxException, AuthenticationException {
        if (suppliedClientData.contains("\u0000")) {
            return authenticateWithSeparator(session, suppliedClientData, '\u0000');
        } else {
            return authenticateWithSeparator(session, suppliedClientData, ' ');
        }
    }

    private String authenticateWithSeparator(Session session, String suppliedClientData, char c) throws SyntaxException, AuthenticationException {
        Iterator<String> it = Splitter.on(c).split(suppliedClientData).iterator();
        if (!it.hasNext()) {
            throw new SyntaxException("You must supply a username for the authentication mechanism. Formal syntax : <NULL>username<NULL>password");
        }
        String userName = it.next();
        if (!it.hasNext()) {
            throw new SyntaxException("You must supply a password for the authentication mechanism. Formal syntax : <NULL>username<NULL>password");
        }
        String password = it.next();
        session.setUser(userName);
        try {
            User user = usersRepository.getUserByName(userName);
            if (user != null && user.verifyPassword(password)) {
                return userName;
            } else {
                return null;
            }
        } catch (UsersRepositoryException e) {
            throw new AuthenticationException(e.getMessage());
        }
    }
}
