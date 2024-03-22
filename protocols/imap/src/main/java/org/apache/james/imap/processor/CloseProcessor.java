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

import jakarta.inject.Inject;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.CloseRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

public class CloseProcessor extends AbstractMailboxProcessor<CloseRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CloseProcessor.class);

    @Inject
    public CloseProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                          MetricFactory metricFactory) {
        super(CloseRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> processRequestReactive(CloseRequest request, ImapSession session, Responder responder) {
        MailboxSession mailboxSession = session.getMailboxSession();
        return getSelectedMailboxReactive(session)
            .flatMap(Throwing.function(mailbox -> {
                if (getMailboxManager().hasRight(mailbox.getMailboxEntity(), MailboxACL.Right.PerformExpunge, mailboxSession)) {
                    return mailbox.expungeReactive(MessageRange.all(), mailboxSession)
                        .then(session.deselect())
                        // Don't send HIGHESTMODSEQ when close. Like correct in the ERRATA of RFC5162
                        // See http://www.rfc-editor.org/errata_search.php?rfc=5162
                        .then(Mono.fromRunnable(() -> okComplete(request, responder)));
                }
                return Mono.empty();
            }))
            .onErrorResume(MailboxException.class, e -> {
                no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
                return ReactorUtils.logAsMono(() -> LOGGER.error("Close failed for mailbox {}", session.getSelected().getMailboxId(), e));
            }).then();
    }

    @Override
    protected MDCBuilder mdc(CloseRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "CLOSE");
    }
}
