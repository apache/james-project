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

import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;
import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.RETRIEVE;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.StatusDataItems;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.StatusRequest;
import org.apache.james.imap.message.response.MailboxStatusResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * STATUS command initial definition: https://www.rfc-editor.org/rfc/rfc3501#section-6.3.10
 *
 * STATUS=SIZE extension: https://www.rfc-editor.org/rfc/rfc8438.html
 */
public class StatusProcessor extends AbstractMailboxProcessor<StatusRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusProcessor.class);

    public StatusProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(StatusRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return ImmutableList.of(Capability.of("STATUS=SIZE"));
    }

    @Override
    protected Mono<Void> processRequestReactive(StatusRequest request, ImapSession session, Responder responder) {
        MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(request.getMailboxName());
        MailboxSession mailboxSession = session.getMailboxSession();

        return logInitialRequest(mailboxPath)
            .then(Mono.from(getMailboxManager().getMailboxReactive(mailboxPath, mailboxSession)))
            .flatMap(mailbox -> retrieveMetadata(mailbox, request.getStatusDataItems(), mailboxSession)
                .flatMap(metaData -> computeStatusResponse(mailbox, request, metaData, mailboxSession)
                    .doOnNext(response -> {
                        // Enable CONDSTORE as this is a CONDSTORE enabling command
                        if (response.getHighestModSeq() != null) {
                            condstoreEnablingCommand(session, responder, metaData, false);
                        }
                        responder.respond(response);
                    })))
            .then(unsolicitedResponses(session, responder, false))
            .then(Mono.fromRunnable(() -> okComplete(request, responder)))
            .onErrorResume(MailboxException.class, e -> {
                no(request, responder, HumanReadableText.STATUS_FAILED);
                return ReactorUtils.logAsMono(() -> LOGGER.error("Status failed for mailbox {}", mailboxPath, e));
            })
            .then();
    }

    private Mono<Void> logInitialRequest(MailboxPath mailboxPath) {
        if (LOGGER.isDebugEnabled()) {
            return ReactorUtils.logAsMono(() -> LOGGER.debug("Status called on mailbox named {}", mailboxPath));
        } else {
            return Mono.empty();
        }
    }

    private Mono<MessageManager.MailboxMetaData> retrieveMetadata(MessageManager mailbox, StatusDataItems statusDataItems, MailboxSession mailboxSession) {
        MessageManager.MailboxMetaData.FetchGroup fetchGroup = computeFetchGroup(statusDataItems);
        RecentMode recentMode = computeRecentMode(statusDataItems);

        try {
            return mailbox.getMetaDataReactive(recentMode, mailboxSession, fetchGroup);
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private RecentMode computeRecentMode(StatusDataItems statusDataItems) {
        if (statusDataItems.isRecent()) {
            return RETRIEVE;
        }
        return IGNORE;
    }

    private Mono<MailboxStatusResponse> computeStatusResponse(MessageManager mailbox,
                                                              StatusRequest request,
                                                              MessageManager.MailboxMetaData metaData,
                                                              MailboxSession session) {
        StatusDataItems statusDataItems = request.getStatusDataItems();
        return size(statusDataItems, mailbox, session)
            .map(maybeSize -> {
                Long messages = messages(statusDataItems, metaData);
                Long recent = recent(statusDataItems, metaData);
                MessageUid uidNext = uidNext(statusDataItems, metaData);
                UidValidity uidValidity = uidValidity(statusDataItems, metaData);
                Long unseen = unseen(statusDataItems, metaData);
                ModSeq highestModSeq = highestModSeq(statusDataItems, metaData);
                return new MailboxStatusResponse(maybeSize.orElse(null),
                    messages, recent, uidNext, highestModSeq, uidValidity, unseen, request.getMailboxName());
            });
    }

    private MessageManager.MailboxMetaData.FetchGroup computeFetchGroup(StatusDataItems statusDataItems) {
        if (statusDataItems.isUnseen()) {
            return MessageManager.MailboxMetaData.FetchGroup.UNSEEN_COUNT;
        } else {
            return MessageManager.MailboxMetaData.FetchGroup.NO_UNSEEN;
        }
    }

    private Long unseen(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isUnseen()) {
            return metaData.getUnseenCount();
        } else {
            return null;
        }
    }

    private UidValidity uidValidity(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isUidValidity()) {
            return metaData.getUidValidity();
        } else {
            return null;
        }
    }

    private ModSeq highestModSeq(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isHighestModSeq()) {
            return metaData.getHighestModSeq();
        } else {
            return null;
        }
    }
    
    private MessageUid uidNext(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isUidNext()) {
            return metaData.getUidNext();
        } else {
            return null;
        }
    }

    private Long recent(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isRecent()) {
            return metaData.countRecent();
        } else {
            return null;
        }
    }

    private Long messages(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isMessages()) {
           return metaData.getMessageCount();
        } else {
            return null;
        }
    }

    private Mono<Optional<Long>> size(StatusDataItems statusDataItems, MessageManager messageManager, MailboxSession session) {
        if (statusDataItems.isSize()) {
            return Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, session))
                .collect(Collectors.summingLong(MessageResult::getSize))
                .map(Optional::of);
        } else {
            return Mono.just(Optional.empty());
        }
    }

    @Override
    protected MDCBuilder mdc(StatusRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "STATUS")
            .addToContext("mailbox", request.getMailboxName())
            .addToContext("parameters", request.getStatusDataItems().toString());
    }
}
