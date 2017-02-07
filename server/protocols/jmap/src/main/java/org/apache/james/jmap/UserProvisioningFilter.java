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
package org.apache.james.jmap;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

public class UserProvisioningFilter implements Filter {

    private static final ImmutableList<String> DEFAULT_MAILBOXES = ImmutableList.of("INBOX", "Outbox", "Sent", "Trash");
    private static final Logger LOGGER = LoggerFactory.getLogger(UserProvisioningFilter.class);
    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;

    @Inject
    @VisibleForTesting UserProvisioningFilter(UsersRepository usersRepository, MailboxManager mailboxManager) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        Optional<MailboxSession> session = Optional.ofNullable((MailboxSession)request.getAttribute(AuthenticationFilter.MAILBOX_SESSION));
        session.ifPresent(this::createAccountIfNeeded);
        chain.doFilter(request, response);
    }
    
    @VisibleForTesting
    void createAccountIfNeeded(MailboxSession session) {
        try {
            User user = session.getUser();
            if (needsAccountCreation(user)) {
                createAccount(user);
            }
        } catch (AlreadyExistInUsersRepositoryException e) {
            // Ignore
        } catch (UsersRepositoryException|MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private boolean needsAccountCreation(User user) throws UsersRepositoryException {
        return !usersRepository.contains(user.getUserName());
    }

    private void createAccount(User user) throws UsersRepositoryException, BadCredentialsException, MailboxException {
        createUser(user);
        createDefaultMailboxes(user);
    }

    private void createUser(User user) throws UsersRepositoryException {
        usersRepository.addUser(user.getUserName(), generatePassword());
    }
    
    private String generatePassword() {
        return UUID.randomUUID().toString();
    }
    
    private void createDefaultMailboxes(User user) throws BadCredentialsException, MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(user.getUserName(), LOGGER);
        DEFAULT_MAILBOXES.stream()
            .map(toMailboxPath(session))
            .forEach(mailboxPath -> createMailbox(mailboxPath, session));
    }

    private Function<String, MailboxPath> toMailboxPath(MailboxSession session) {
        return mailbox -> new MailboxPath(session.getPersonalSpace(), session.getUser().getUserName(), mailbox);
    }
    
    private void createMailbox(MailboxPath mailboxPath, MailboxSession session) {
        try {
            mailboxManager.createMailbox(mailboxPath, session);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void destroy() {
    }
}
