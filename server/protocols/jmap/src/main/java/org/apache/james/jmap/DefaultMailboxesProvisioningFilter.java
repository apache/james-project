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

import org.apache.james.core.User;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class DefaultMailboxesProvisioningFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMailboxesProvisioningFilter.class);
    private final MailboxManager mailboxManager;
    private final SubscriptionManager subscriptionManager;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    DefaultMailboxesProvisioningFilter(MailboxManager mailboxManager,
                                       SubscriptionManager subscriptionManager,
                                       MetricFactory metricFactory) {
        this.mailboxManager = mailboxManager;
        this.subscriptionManager = subscriptionManager;
        this.metricFactory = metricFactory;
    }
    
    @Override
    public void init(FilterConfig filterConfig) {
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
            throw new RuntimeException(e);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private void createDefaultMailboxes(User user) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(user.asString());
        DefaultMailboxes.DEFAULT_MAILBOXES.stream()
            .map(toMailboxPath(session))
            .filter(mailboxPath -> mailboxDoesntExist(mailboxPath, session))
            .forEach(mailboxPath -> createMailbox(mailboxPath, session));
    }

    private boolean mailboxDoesntExist(MailboxPath mailboxPath, MailboxSession session) {
        try {
            return !mailboxManager.mailboxExists(mailboxPath, session);
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private Function<String, MailboxPath> toMailboxPath(MailboxSession session) {
        return mailbox -> MailboxPath.forUser(session.getUser().asString(), mailbox);
    }
    
    private void createMailbox(MailboxPath mailboxPath, MailboxSession session) {
        try {
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);
            if (mailboxId.isPresent()) {
                subscriptionManager.subscribe(session, mailboxPath.getName());
            }
            LOGGER.info("Provisioning {}. {} created.", mailboxPath, mailboxId);
        } catch (MailboxExistsException e) {
            LOGGER.info("Mailbox {} have been created concurrently", mailboxPath);
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() {
    }
}
