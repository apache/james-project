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
package org.apache.james.jmap.draft;

import static org.apache.james.metrics.api.TimeMetric.ExecutionResult.DEFAULT_100_MS_THRESHOLD;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

import com.google.common.annotations.VisibleForTesting;

public class UserProvisioningFilter implements Filter {

    private final UsersRepository usersRepository;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting UserProvisioningFilter(UsersRepository usersRepository, MetricFactory metricFactory) {
        this.usersRepository = usersRepository;
        this.metricFactory = metricFactory;
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!usersRepository.isReadOnly()) {
            Optional<MailboxSession> session = Optional.ofNullable((MailboxSession) request.getAttribute(AuthenticationFilter.MAILBOX_SESSION));
            session.ifPresent(this::createAccountIfNeeded);
        }
        chain.doFilter(request, response);
    }
    
    @VisibleForTesting
    void createAccountIfNeeded(MailboxSession session) {
        TimeMetric timeMetric = metricFactory.timer("JMAP-user-provisioning");
        try {
            Username username = session.getUser();
            if (needsAccountCreation(username)) {
                createAccount(username);
            }
        } catch (AlreadyExistInUsersRepositoryException e) {
            // Ignore
        } catch (UsersRepositoryException e) {
            throw new RuntimeException(e);
        } finally {
            timeMetric.stopAndPublish().logWhenExceedP99(DEFAULT_100_MS_THRESHOLD);
        }
    }

    private void createAccount(Username username) throws UsersRepositoryException {
        usersRepository.addUser(username, generatePassword());
    }

    private boolean needsAccountCreation(Username username) throws UsersRepositoryException {
        return !usersRepository.contains(username);
    }

    private String generatePassword() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void destroy() {
    }
}
