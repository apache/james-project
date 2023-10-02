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

import static org.apache.james.imap.api.ImapConstants.SUPPORTS_UIDPLUS;
import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;

import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.ExpungeRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.AuditTrail;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ExpungeProcessor extends AbstractMailboxProcessor<ExpungeRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpungeProcessor.class);

    private static final List<Capability> UIDPLUS = ImmutableList.of(SUPPORTS_UIDPLUS);

    @Inject
    public ExpungeProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory) {
        super(ExpungeRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> processRequestReactive(ExpungeRequest request, ImapSession session, Responder responder) {
        MailboxSession mailboxSession = session.getMailboxSession();

        return getSelectedMailboxReactive(session)
            .flatMap(Throwing.function(mailbox -> {
                if (!getMailboxManager().hasRight(mailbox.getMailboxEntity(), MailboxACL.Right.PerformExpunge, mailboxSession)) {
                    no(request, responder, HumanReadableText.MAILBOX_IS_READ_ONLY);
                    return Mono.empty();
                } else {
                    return expunge(request, session, mailbox, mailboxSession)
                        .flatMap(expunged -> unsolicitedResponses(session, responder, false).thenReturn(expunged))
                        .flatMap(Throwing.function(expunged -> respondOk(request, session, responder, mailbox, mailboxSession, expunged)));
                }
            }))
            .onErrorResume(MessageRangeException.class, e -> {
                taggedBad(request, responder, HumanReadableText.INVALID_MESSAGESET);
                return ReactorUtils.logAsMono(() -> LOGGER.debug("Expunge failed", e));
            })
            .onErrorResume(MailboxException.class, e -> {
                no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
                return ReactorUtils.logAsMono(() -> LOGGER.error("Expunge failed for mailbox {}", session.getSelected().getMailboxId(), e));
            });
    }

    private Mono<Integer> expunge(ExpungeRequest request, ImapSession session, MessageManager mailbox, MailboxSession mailboxSession) {
        IdRange[] ranges = request.getUidSet();
        if (ranges == null) {
            return expunge(mailbox, MessageRange.all(), session, mailboxSession);
        } else {
            // Handle UID EXPUNGE which is part of UIDPLUS
            // See http://tools.ietf.org/html/rfc4315
            return Flux.fromIterable(ImmutableList.copyOf(ranges))
                .map(Throwing.<IdRange, MessageRange>function(range -> messageRange(session.getSelected(), range, true)
                    .orElseThrow(() -> new MessageRangeException(range.getFormattedString() + " is an invalid range")))
                    .sneakyThrow())
                .concatMap(range -> expunge(mailbox, range, session, mailboxSession))
                .reduce(Integer::sum);
        }
    }

    private Mono<Void> respondOk(ExpungeRequest request, ImapSession session, Responder responder, MessageManager mailbox, MailboxSession mailboxSession, int expunged) throws MailboxException {
        // Check if QRESYNC was enabled and at least one message was expunged. If so we need to respond with an OK response that contain the HIGHESTMODSEQ
        //
        // See RFC5162 3.3 EXPUNGE Command 3.5. UID EXPUNGE Command
        if (EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)  && expunged > 0) {
            return mailbox.getMetaDataReactive(IGNORE, mailboxSession, EnumSet.of(MessageManager.MailboxMetaData.Item.HighestModSeq))
                .doOnNext(metaData -> okComplete(request, ResponseCode.highestModSeq(metaData.getHighestModSeq()), responder))
                .then();
        } else {
            return Mono.fromRunnable(() -> okComplete(request, responder));
        }
    }

    private Mono<Integer> expunge(MessageManager mailbox, MessageRange range, ImapSession session, MailboxSession mailboxSession) {
        SelectedMailbox selected = session.getSelected();

        return mailbox.expungeReactive(range, mailboxSession)
            .doOnNext(selected::removeRecent)
            .count()
            .map(Long::intValue)
            .doOnSuccess(any -> AuditTrail.entry()
                .username(() -> mailboxSession.getUser().asString())
                .sessionId(() -> session.sessionId().asString())
                .protocol("IMAP")
                .action("EXPUNGE")
                .parameters(() -> ImmutableMap.of("loggedInUser", mailboxSession.getLoggedInUser().map(Username::asString).orElse(""),
                    "mailboxId", mailbox.getId().serialize(),
                    "messageUids", range.toString()))
                .log("IMAP EXPUNGE succeeded."));
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return UIDPLUS;
    }

    @Override
    protected MDCBuilder mdc(ExpungeRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "EXPUNGE")
            .addToContext("uidSet", IdRange.toString(request.getUidSet()));
    }
}
