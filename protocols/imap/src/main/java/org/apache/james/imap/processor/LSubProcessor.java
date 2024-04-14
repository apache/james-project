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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.display.ModifiedUtf7;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.LsubRequest;
import org.apache.james.imap.message.response.LSubResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxNameExpression;
import org.apache.james.mailbox.model.search.PrefixedRegex;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LSubProcessor extends AbstractMailboxProcessor<LsubRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LSubProcessor.class);

    private final SubscriptionManager subscriptionManager;

    @Inject
    public LSubProcessor(MailboxManager mailboxManager, SubscriptionManager subscriptionManager, StatusResponseFactory factory,
                         MetricFactory metricFactory) {
        super(LsubRequest.class, mailboxManager, factory, metricFactory);
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    protected Mono<Void> processRequestReactive(LsubRequest request, ImapSession session, Responder responder) {
        String referenceName = request.getBaseReferenceName();
        String mailboxPattern = request.getMailboxPattern();

        return listSubscriptions(session, responder, referenceName, mailboxPattern)
            .then(Mono.fromRunnable(() -> okComplete(request, responder)))
            .onErrorResume(MailboxException.class, e -> {
                no(request, responder, HumanReadableText.GENERIC_LSUB_FAILURE);
                return ReactorUtils.logAsMono(() -> LOGGER.error("LSub failed for reference {} and pattern {}", referenceName, mailboxPattern, e));
            }).then();
    }

    private Mono<Void> listSubscriptions(ImapSession session, Responder responder, String referenceName, String mailboxName) {
        MailboxSession mailboxSession = session.getMailboxSession();
        try {
            Mono<List<String>> mailboxesMono = Flux.from(subscriptionManager.subscriptionsReactive(mailboxSession))
                .map(MailboxPath::getName)
                .collectList();

            String decodedMailName = ModifiedUtf7.decodeModifiedUTF7(referenceName);

            MailboxNameExpression expression = new PrefixedRegex(
                decodedMailName,
                ModifiedUtf7.decodeModifiedUTF7(mailboxName),
                mailboxSession.getPathDelimiter());

            return mailboxesMono.doOnNext(mailboxes -> {
                Collection<String> mailboxResponses = new ArrayList<>();
                for (String mailbox : mailboxes) {
                    respond(responder, expression, mailbox, true, mailboxes, mailboxResponses, mailboxSession.getPathDelimiter());
                }
            }).then();
        } catch (SubscriptionException e) {
            throw new RuntimeException(e);
        }
    }

    private void respond(Responder responder, MailboxNameExpression expression, String mailboxName, boolean originalSubscription, Collection<String> mailboxes, Collection<String> mailboxResponses, char delimiter) {
        if (expression.isExpressionMatch(mailboxName)) {
            if (!mailboxResponses.contains(mailboxName)) {
                responder.respond(new LSubResponse(mailboxName, !originalSubscription, delimiter));
                mailboxResponses.add(mailboxName);
            }
        } else {
            int lastDelimiter = mailboxName.lastIndexOf(delimiter);
            if (lastDelimiter > 0) {
                String parentMailbox = mailboxName.substring(0, lastDelimiter);
                if (!mailboxes.contains(parentMailbox)) {
                    respond(responder, expression, parentMailbox, false, mailboxes, mailboxResponses, delimiter);
                }
            }
        }
    }

    @Override
    protected MDCBuilder mdc(LsubRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "LSUB")
            .addToContext("base", request.getBaseReferenceName())
            .addToContext("pattern", request.getMailboxPattern());
    }
}
