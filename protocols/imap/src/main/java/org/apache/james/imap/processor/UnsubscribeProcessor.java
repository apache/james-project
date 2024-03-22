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

package org.apache.james.imap.processor;

import static org.apache.james.util.ReactorUtils.logOnError;

import jakarta.inject.Inject;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.UnsubscribeRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class UnsubscribeProcessor extends AbstractSubscriptionProcessor<UnsubscribeRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsubscribeProcessor.class);

    @Inject
    public UnsubscribeProcessor(MailboxManager mailboxManager, SubscriptionManager subscriptionManager, StatusResponseFactory factory,
                                MetricFactory metricFactory) {
        super(UnsubscribeRequest.class, mailboxManager, subscriptionManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> doProcessRequest(UnsubscribeRequest request, ImapSession session, Responder responder) {
        MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(request.getMailboxName());
        MailboxSession mailboxSession = session.getMailboxSession();

        return Mono.from(getSubscriptionManager().unsubscribeReactive(mailboxPath, mailboxSession))
            .then(unsolicitedResponses(session, responder, false))
            .then(Mono.fromRunnable(() -> okComplete(request, responder)))
            .doOnEach(logOnError(SubscriptionException.class, e -> LOGGER.info("Unsubscribe failed for mailbox {}", request.getMailboxName(), e)))
            .onErrorResume(SubscriptionException.class, e ->
                unsolicitedResponses(session, responder, false)
                .then(Mono.fromRunnable(() -> no(request, responder, HumanReadableText.GENERIC_SUBSCRIPTION_FAILURE)))).then();
    }

    @Override
    protected MDCBuilder mdc(UnsubscribeRequest message) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "UNSUBSCRIBE")
            .addToContext("mailbox", message.getMailboxName());
    }
}
