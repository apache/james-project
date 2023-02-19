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
package org.apache.james.jmap.http;

import static org.apache.james.util.FunctionalUtils.negate;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DefaultMailboxesProvisioner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMailboxesProvisioner.class);
    private final MailboxManager mailboxManager;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    public DefaultMailboxesProvisioner(MailboxManager mailboxManager,
                                MetricFactory metricFactory) {
        this.mailboxManager = mailboxManager;
        this.metricFactory = metricFactory;
    }

    public Mono<Void> createMailboxesIfNeeded(MailboxSession session) {
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("JMAP-mailboxes-provisioning",
            createDefaultMailboxes(session)));
    }

    private Mono<Void> createDefaultMailboxes(MailboxSession session) {
        return Flux.fromIterable(DefaultMailboxes.DEFAULT_MAILBOXES)
            .map(toMailboxPath(session))
            .filterWhen(mailboxPath -> mailboxDoesntExist(mailboxPath, session), DEFAULT_CONCURRENCY)
            .concatMap(mailboxPath -> createMailbox(mailboxPath, session))
            .then();
    }

    private Mono<Boolean> mailboxDoesntExist(MailboxPath mailboxPath, MailboxSession session) {
        return Mono.from(mailboxManager.mailboxExists(mailboxPath, session))
            .map(negate());
    }

    private Function<String, MailboxPath> toMailboxPath(MailboxSession session) {
        return mailbox -> MailboxPath.forUser(session.getUser(), mailbox);
    }
    
    private Mono<Void> createMailbox(MailboxPath mailboxPath, MailboxSession session) {
        return Mono.from(mailboxManager.createMailboxReactive(mailboxPath, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, session))
            .onErrorResume(MailboxExistsException.class, e -> {
                LOGGER.info("Mailbox {} have been created concurrently", mailboxPath);
                return Mono.empty();
            })
            .then();
    }
}
