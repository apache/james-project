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

import java.util.Date;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.ReplaceRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.streams.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * RFC-8508: IMAP Replace extension
 *
 * https://www.rfc-editor.org/rfc/rfc8508.html
 */
public class ReplaceProcessor extends AbstractMailboxProcessor<ReplaceRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceProcessor.class);
    private static final ImmutableList<Capability> CAPABILITIES = ImmutableList.of(Capability.of("REPLACE"));

    public ReplaceProcessor(MailboxManager mailboxManager, StatusResponseFactory statusResponseFactory, MetricFactory metricFactory) {
        super(ReplaceRequest.class, mailboxManager, statusResponseFactory, metricFactory);
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

    @Override
    protected Mono<Void> processRequestReactive(ReplaceRequest request, ImapSession session, Responder responder) {
        final String mailboxName = request.getMailboxName();
        final Content messageIn = request.getMessage().asMailboxContent();
        final Date datetime = request.getDatetime();
        final Flags flags = request.getFlags();
        final MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);
        final MailboxManager mailboxManager = getMailboxManager();

        session.stopDetectingCommandInjection();
        return append(session, responder, messageIn, datetime, flags, mailboxPath, mailboxManager)
            .then(deleteBaseMessage(request, session))
            .then(Mono.defer(() -> unsolicitedResponses(session, responder, request.isUseUid())))
            .then(Mono.fromRunnable(() -> okComplete(request, responder))).then()
            .doOnEach(logOnError(MailboxNotFoundException.class, e -> LOGGER.debug("Append failed for mailbox {}", mailboxPath, e)))
            .onErrorResume(MailboxNotFoundException.class, e -> {
                // Indicates that the mailbox does not exist
                // So TRY CREATE
                no(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, ResponseCode.tryCreate());
                return Mono.empty();
            })
            .doOnEach(logOnError(OverQuotaException.class, e -> LOGGER.info("Append failed for mailbox {} because overquota", mailboxPath)))
            .onErrorResume(OverQuotaException.class, e -> {
                // Indicates that the mailbox does not exist
                // So TRY CREATE
                no(request, responder, HumanReadableText.FAILURE_OVERQUOTA, ResponseCode.overQuota());
                return Mono.empty();
            })
            .doOnEach(logOnError(MailboxException.class, e -> LOGGER.error("Append failed for mailbox {}", mailboxPath, e)))
            .onErrorResume(MailboxException.class, e -> {
                // Some other issue
                no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
                return Mono.empty();
            });
    }

    private Mono<Void> append(ImapSession session, Responder responder, Content messageIn, Date datetime, Flags flags, MailboxPath mailboxPath, MailboxManager mailboxManager) {
        return Mono.from(mailboxManager.getMailboxReactive(mailboxPath, session.getMailboxSession()))
            .flatMap(mailbox -> appendToMailbox(messageIn, datetime, flags, session, mailbox, responder));
    }

    private Mono<Void> deleteBaseMessage(ReplaceRequest request, ImapSession session) {
        try {
            ImmutableList<MessageUid> uids = Iterators.toStream(messageRange(session.getSelected(), new IdRange(request.getId()), request.isUseUid())
                    .orElseThrow(() -> new MessageRangeException((request.getId() + " is an invalid range")))
                    .iterator())
                .collect(ImmutableList.toImmutableList());

            return getSelectedMailboxReactive(session)
                .flatMap(messageManager -> messageManager.deleteReactive(uids, session.getMailboxSession()))
                .then(Mono.fromRunnable(() -> uids.forEach(session.getSelected()::removeRecent)));
        } catch (MessageRangeException e) {
            return Mono.error(e);
        }
    }

    private Mono<Void> appendToMailbox(Content message, Date datetime, Flags flagsToBeSet, ImapSession session, MessageManager mailbox, Responder responder) {
        MailboxSession mailboxSession = session.getMailboxSession();
        SelectedMailbox selectedMailbox = session.getSelected();
        boolean isSelectedMailbox = selectedMailbox.getMailboxId().equals(mailbox.getId());


        return Mono.from(mailbox.appendMessageReactive(
            MessageManager.AppendCommand.builder()
                .withInternalDate(datetime)
                .withFlags(flagsToBeSet)
                .isRecent(!isSelectedMailbox)
                .build(message), mailboxSession))
            .map(MessageManager.AppendResult::getId)
            .map(Throwing.<ComposedMessageId, ComposedMessageId>function(messageId -> {
                    if (isSelectedMailbox) {
                        selectedMailbox.addRecent(messageId.getUid());
                    }
                    return messageId;
                }).sneakyThrow())
            .flatMap(messageId -> Mono.fromCallable(() -> {
                // get folder UIDVALIDITY
                UidValidity uidValidity = mailbox.getMailboxEntity().getUidValidity();
                ResponseCode responseCode = ResponseCode.appendUid(uidValidity, new UidRange[]{new UidRange(messageId.getUid())});
                responder.respond(getStatusResponseFactory()
                    .untaggedOk(HumanReadableText.REPLACE_READY, responseCode));
                return null;
            }))
            .then();
    }

    @Override
    protected MDCBuilder mdc(ReplaceRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "REPLACE")
            .addToContext("mailbox", request.getMailboxName())
            .addToContext("id", Long.toString(request.getId()));
    }
}
