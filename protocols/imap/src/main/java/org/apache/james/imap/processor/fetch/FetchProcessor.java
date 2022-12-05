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

package org.apache.james.imap.processor.fetch;

import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;
import static org.apache.james.util.ReactorUtils.logOnError;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.message.FetchData.Item;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.FetchRequest;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.imap.processor.AbstractMailboxProcessor;
import org.apache.james.imap.processor.EnableProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class FetchProcessor extends AbstractMailboxProcessor<FetchRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchProcessor.class);

    public FetchProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                          MetricFactory metricFactory) {
        super(FetchRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> processRequestReactive(FetchRequest request, ImapSession session, Responder responder) {
        IdRange[] idSet = request.getIdSet();
        FetchData fetch = computeFetchData(request, session);
        long changedSince = fetch.getChangedSince();
        MailboxSession mailboxSession = session.getMailboxSession();
        SelectedMailbox selected = session.getSelected();

        return Optional.ofNullable(selected)
            .map(s -> Mono.from(getMailboxManager().getMailboxReactive(s.getMailboxId(), mailboxSession)))
            .orElseGet(() -> Mono.error(new MailboxException("Session not in SELECTED state")))
            .flatMap(Throwing.<MessageManager, Mono<Void>>function(mailbox -> {
                boolean vanished = fetch.getVanished();
                if (vanished && !EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)) {
                    taggedBad(request, responder, HumanReadableText.QRESYNC_NOT_ENABLED);
                    return Mono.empty();
                }

                if (vanished && changedSince == -1) {
                    taggedBad(request, responder, HumanReadableText.QRESYNC_VANISHED_WITHOUT_CHANGEDSINCE);
                    return Mono.empty();
                }

                boolean constoreCommand = fetch.getChangedSince() != -1 || fetch.contains(Item.MODSEQ);
                Set<Capability> enabled = EnableProcessor.getEnabledCapabilities(session);
                if (constoreCommand && !enabled.contains(ImapConstants.SUPPORTS_CONDSTORE)) {
                    // Enable CONDSTORE as this is a CONDSTORE enabling command
                    return mailbox.getMetaDataReactive(IGNORE, mailboxSession, EnumSet.of(MailboxMetaData.Item.HighestModSeq))
                        .doOnNext(metaData -> condstoreEnablingCommand(session, responder, metaData, true))
                        .flatMap(Throwing.<MailboxMetaData, Mono<Void>>function(
                            any -> doFetch(selected, request, responder, fetch, mailboxSession, mailbox, session))
                            .sneakyThrow());
                }

                return doFetch(selected, request, responder, fetch, mailboxSession, mailbox, session);
            }).sneakyThrow())
            .doOnEach(logOnError(MessageRangeException.class, e -> LOGGER.debug("Fetch failed for mailbox {} because of invalid sequence-set {}", selected.getMailboxId(), idSet, e)))
            .onErrorResume(MessageRangeException.class, e -> {
                taggedBad(request, responder, HumanReadableText.INVALID_MESSAGESET);
                return Mono.empty();
            })
            .doOnEach(logOnError(MailboxException.class, e -> LOGGER.error("Fetch failed for mailbox {} and sequence-set {}", selected.getMailboxId(), idSet, e)))
            .onErrorResume(MailboxException.class, e -> {
                no(request, responder, HumanReadableText.SEARCH_FAILED);
                return Mono.empty();
            })
            .then();
    }

    private Mono<Void> doFetch(SelectedMailbox selected, FetchRequest request, Responder responder, FetchData fetch, MailboxSession mailboxSession, MessageManager mailbox, ImapSession session) throws MailboxException {
        List<MessageRange> ranges = new ArrayList<>();

        for (IdRange range : request.getIdSet()) {
            MessageRange messageSet = messageRange(session.getSelected(), range, request.isUseUids());
            if (messageSet != null) {
                MessageRange normalizedMessageSet = normalizeMessageRange(selected, messageSet);
                MessageRange batchedMessageSet = MessageRange.range(normalizedMessageSet.getUidFrom(), normalizedMessageSet.getUidTo());
                ranges.add(batchedMessageSet);
            }
        }

        if (fetch.getVanished()) {
            // TODO: From the QRESYNC RFC it seems ok to send the VANISHED responses after the FETCH Responses.
            //       If we do so we could prolly save one mailbox access which should give use some more speed up
            respondVanished(selected, ranges, responder);
        }
        boolean omitExpunged = (!request.isUseUids());
        return processMessageRanges(selected, mailbox, ranges, fetch, mailboxSession, responder)
            // Don't send expunge responses if FETCH is used to trigger this
            // processor. See IMAP-284
            .then(unsolicitedResponses(session, responder, omitExpunged, request.isUseUids()))
            .then(Mono.fromRunnable(() -> okComplete(request, responder)));
    }

    private FetchData computeFetchData(FetchRequest request, ImapSession session) {
        // if QRESYNC is enable its necessary to also return the UID in all cases
        if (EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)) {
            return FetchData.Builder.from(request.getFetch())
                .fetch(Item.UID)
                .build();
        }
        return request.getFetch();
    }

    /**
     * Process the given message ranges by fetch them and pass them to the
     * {@link org.apache.james.imap.api.process.ImapProcessor.Responder}
     */
    private Mono<Void> processMessageRanges(SelectedMailbox selected, MessageManager mailbox, List<MessageRange> ranges, FetchData fetch, MailboxSession mailboxSession, Responder responder) throws MailboxException {
        FetchResponseBuilder builder = new FetchResponseBuilder(new EnvelopeBuilder());
        FetchGroup resultToFetch = FetchDataConverter.getFetchGroup(fetch);

        return Flux.fromIterable(ranges)
            .concatMap(range -> {
                if (fetch.isOnlyFlags()) {
                    return processMessageRangeForFlags(selected, mailbox, fetch, mailboxSession, responder, builder, range);
                } else {
                    return processMessageRange(selected, mailbox, fetch, mailboxSession, responder, builder, resultToFetch, range);
                }
            })
            .then();
    }

    private Mono<Void> processMessageRangeForFlags(SelectedMailbox selected, MessageManager mailbox, FetchData fetch, MailboxSession mailboxSession, Responder responder, FetchResponseBuilder builder, MessageRange range) {
        return Flux.from(mailbox.listMessagesMetadata(range, mailboxSession))
            .filter(ids -> !fetch.contains(Item.MODSEQ) || ids.getModSeq().asLong() > fetch.getChangedSince())
            .concatMap(result -> toResponse(mailbox, fetch, mailboxSession, builder, selected, result))
            .doOnNext(responder::respond)
            .then();
    }

    private Mono<FetchResponse> toResponse(MessageManager mailbox, FetchData fetch, MailboxSession mailboxSession, FetchResponseBuilder builder, SelectedMailbox selected, org.apache.james.mailbox.model.ComposedMessageIdWithMetaData result) {
        try {
            return builder.build(fetch, result, mailbox, selected, mailboxSession);
        } catch (MessageRangeException e) {
            // we can't for whatever reason find the message so
            // just skip it and log it to debug
            LOGGER.debug("Unable to find message with uid {}", result.getComposedMessageId().getUid(), e);
            return ReactorUtils.logAsMono(() -> LOGGER.debug("Unable to find message with uid {}", result.getComposedMessageId().getUid(), e))
                .then(Mono.empty());
        } catch (MailboxException e) {
            // we can't for whatever reason find parse all requested parts of the message. This may because it was deleted while try to access the parts.
            // So we just skip it
            //
            // See IMAP-347
            return ReactorUtils.logAsMono(() -> LOGGER.error("Unable to fetch message with uid {}, so skip it", result.getComposedMessageId().getUid(), e))
                .then(Mono.empty());
        }
    }

    private Mono<FetchResponse> toResponse(MessageManager mailbox, FetchData fetch, MailboxSession mailboxSession, FetchResponseBuilder builder, SelectedMailbox selected, MessageResult result) {
        try {
            return builder.build(fetch, result, mailbox, selected, mailboxSession);
        } catch (MessageRangeException e) {
            // we can't for whatever reason find the message so
            // just skip it and log it to debug
            return ReactorUtils.logAsMono(() -> LOGGER.debug("Unable to find message with uid {}", result.getUid(), e))
                .then(Mono.empty());
        } catch (MailboxException e) {
            // we can't for whatever reason find parse all requested parts of the message. This may because it was deleted while try to access the parts.
            // So we just skip it
            //
            // See IMAP-347
            return ReactorUtils.logAsMono(() -> LOGGER.error("Unable to fetch message with uid {}, so skip it", result.getUid(), e))
                .then(Mono.empty());
        }
    }

    private Mono<Void> processMessageRange(SelectedMailbox selected, MessageManager mailbox, FetchData fetch, MailboxSession mailboxSession, Responder responder, FetchResponseBuilder builder, FetchGroup resultToFetch, MessageRange range) {
        return Flux.from(mailbox.getMessagesReactive(range, resultToFetch, mailboxSession))
            .filter(ids -> !fetch.contains(Item.MODSEQ) || ids.getModSeq().asLong() > fetch.getChangedSince())
            .concatMap(result -> toResponse(mailbox, fetch, mailboxSession, builder, selected, result))
            .doOnNext(responder::respond)
            .then();
    }

    @Override
    protected MDCBuilder mdc(FetchRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "FETCH")
            .addToContext("useUid", Boolean.toString(request.isUseUids()))
            .addToContext("idSet", IdRange.toString(request.getIdSet()))
            .addToContext("fetchedData", request.getFetch().toString());
    }
}
