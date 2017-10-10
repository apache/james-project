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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;

public class DefaultMailboxesProvisioningFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMailboxesProvisioningFilter.class);
    private final MailboxManager mailboxManager;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting DefaultMailboxesProvisioningFilter(MailboxManager mailboxManager, MetricFactory metricFactory) {
        this.mailboxManager = mailboxManager;
        this.metricFactory = metricFactory;
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        Optional<MailboxSession> session = Optional.ofNullable((MailboxSession)request.getAttribute(AuthenticationFilter.MAILBOX_SESSION));
        session.ifPresent(this::createMailboxesIfNeeded);
        chain.doFilter(request, response);
    }
    
    @VisibleForTesting
    void createMailboxesIfNeeded(MailboxSession session) {
        TimeMetric timeMetric = metricFactory.timer("JMAP-mailboxes-provisioning");
        try {
            User user = session.getUser();
            createDefaultMailboxes(user);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private void createDefaultMailboxes(User user) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(user.getUserName());
        DefaultMailboxes.DEFAULT_MAILBOXES.stream()
            .map(toMailboxPath(session))
            .filter(mailboxPath -> mailboxDoesntExist(mailboxPath, session))
            .forEach(mailboxPath -> createMailbox(mailboxPath, session));
    }

    private boolean mailboxDoesntExist(MailboxPath mailboxPath, MailboxSession session) {
        try {
            return !mailboxManager.mailboxExists(mailboxPath, session);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private Function<String, MailboxPath> toMailboxPath(MailboxSession session) {
        return mailbox -> new MailboxPath(session.getPersonalSpace(), session.getUser().getUserName(), mailbox);
    }
    
    private void createMailbox(MailboxPath mailboxPath, MailboxSession session) {
        try {
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);
            LOGGER.info("Provisioning " + mailboxPath + ". " + mailboxId + " created.");
        } catch (MailboxExistsException e) {
            LOGGER.info("Mailbox {} have been created concurrently", mailboxPath);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void destroy() {
    }
}
